import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { ChatMessage, ChatResponse, ChatContextType } from '../types'

const ChatContext = createContext<ChatContextType | null>(null)

export const ChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [jwtToken, setJwtToken] = useState<string | null>(null)

  // Get JWT token from various sources
  useEffect(() => {
    const token = getJwtToken()
    if (token) setJwtToken(token)

    // Listen for postMessage from ThingsBoard
    const handleMessage = (event: MessageEvent) => {
      if (event.data?.type === 'TB_AUTH_TOKEN' && event.data?.token) {
        setJwtToken(event.data.token)
        localStorage.setItem('jwt_token', event.data.token)
      }
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [])

  const sendMessage = useCallback(
    async (question: string) => {
      if (!question.trim() || isLoading) return

      const userMessage: ChatMessage = {
        id: Date.now().toString(),
        role: 'user',
        content: question,
        timestamp: Date.now()
      }

      setMessages(prev => [...prev, userMessage])
      setIsLoading(true)

      try {
        const headers: Record<string, string> = {
          'Content-Type': 'application/json'
        }

        if (jwtToken) {
          headers['X-TB-Token'] = jwtToken
        }

        const response = await fetch('/api/v1/chat/ask', {
          method: 'POST',
          headers,
          body: JSON.stringify({ question })
        })

        const data: ChatResponse = await response.json()

        const botMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          role: 'bot',
          content: data.error ? (data.errorMessage || 'Something went wrong') : data.answer,
          tokensUsed: data.tokensUsed,
          timestamp: data.timestamp || Date.now()
        }

        setMessages(prev => [...prev, botMessage])
      } catch (error) {
        console.error('Chat error:', error)
        const errorMessage: ChatMessage = {
          id: (Date.now() + 1).toString(),
          role: 'bot',
          content: 'Unable to reach the server. Please try again.',
          timestamp: Date.now()
        }
        setMessages(prev => [...prev, errorMessage])
      } finally {
        setIsLoading(false)
      }
    },
    [isLoading, jwtToken]
  )

  const clearHistory = useCallback(() => {
    setMessages([])
  }, [])

  return (
    <ChatContext.Provider value={{ messages, isLoading, sendMessage, clearHistory, jwtToken }}>
      {children}
    </ChatContext.Provider>
  )
}

export const useChat = () => {
  const context = useContext(ChatContext)
  if (!context) {
    throw new Error('useChat must be used within ChatProvider')
  }
  return context
}

function getJwtToken(): string | null {
  // Check URL parameters
  const urlParams = new URLSearchParams(window.location.search)
  let token = urlParams.get('token') || urlParams.get('jwt_token') || urlParams.get('tb_token') || urlParams.get('access_token')
  if (token) {
    localStorage.setItem('jwt_token', token)
    return token
  }

  // Check localStorage
  const keys = ['jwt_token', 'tb_token', 'token', 'access_token', 'authToken', 'thingsboard_token']
  for (const key of keys) {
    token = localStorage.getItem(key)
    if (token) return token
  }

  // Try parent window (for iframe context)
  try {
    if (window.parent !== window) {
      const parentToken = window.parent.localStorage?.getItem('jwt_token') || window.parent.localStorage?.getItem('tb_token')
      if (parentToken) return parentToken
    }
  } catch (e) {
    console.debug('Cannot access parent window', e)
  }

  return null
}
