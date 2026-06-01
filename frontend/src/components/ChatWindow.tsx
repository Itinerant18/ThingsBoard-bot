import React, { useEffect, useRef } from 'react'
import { useChat } from '../context/ChatContext'
import { MessageBubble } from './MessageBubble'
import { TypingIndicator } from './TypingIndicator'
import { ChatInput } from './ChatInput'
import { WelcomeMessage } from './WelcomeMessage'

export const ChatWindow: React.FC = () => {
  const { messages, isLoading } = useChat()
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, isLoading])

  return (
    <div className="flex flex-col h-full bg-stone-950">
      {/* Header */}
      <div className="border-b border-stone-800 bg-stone-900 px-4 py-3 flex items-center gap-3 flex-shrink-0">
        <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-accent-gold to-accent-gold/70 flex items-center justify-center flex-shrink-0">
          <svg
            className="w-6 h-6 text-stone-950"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M12 2a4 4 0 0 0-4 4v2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-2V6a4 4 0 0 0-4-4z" />
            <circle cx="9" cy="13" r="1" />
            <circle cx="15" cy="13" r="1" />
            <path d="M9 17h6" />
          </svg>
        </div>

        <div className="flex-1">
          <div className="font-semibold text-stone-50">SAI</div>
          <div className="text-xs text-accent-gold flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-accent-gold pulse-dot inline-block" />
            Online — Ready to help
          </div>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto chat-messages p-4 space-y-3">
        {messages.length === 0 && <WelcomeMessage />}

        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} />
        ))}

        {isLoading && <TypingIndicator />}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <ChatInput />

      {/* Footer */}
      <div className="text-center text-[10px] text-stone-600 py-2 border-t border-stone-800 flex-shrink-0">
        Powered by <span className="font-semibold text-accent-gold">SEPLE</span>
      </div>
    </div>
  )
}
