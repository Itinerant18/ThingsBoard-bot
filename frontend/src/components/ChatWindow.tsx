import React, { useEffect, useRef } from 'react'
import { useChat } from '../context/ChatContext'
import { MessageBubble } from './MessageBubble'
import { TypingIndicator } from './TypingIndicator'
import { ChatInput } from './ChatInput'
import { WelcomeMessage } from './WelcomeMessage'

export const ChatWindow: React.FC = () => {
  const { messages, isLoading, sendMessage } = useChat()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  // Follow the stream only while the user is parked near the bottom. If they
  // scroll up to read, stop yanking them down (ChatGPT/Claude behaviour).
  const stickToBottom = useRef(true)

  const handleScroll = () => {
    const el = scrollRef.current
    if (!el) return
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  useEffect(() => {
    // When the user sends their own message, always snap back to the bottom —
    // even if they had scrolled up to read history. They expect to see their
    // question and the incoming reply. Streaming bot tokens still respect the
    // "don't yank them down while reading" rule via stickToBottom.
    const last = messages[messages.length - 1]
    if (last?.role === 'user') {
      stickToBottom.current = true
    }
    if (stickToBottom.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isLoading])

  return (
    <div className="flex flex-col h-full bg-[#E8E0D4]">
      {/* Brushed-Metal Header */}
      <div className="brushed-metal-header px-6 py-3 flex items-center justify-between flex-shrink-0 z-10">
        <div className="flex items-center gap-3">
          {/* Embossed logo badge */}
          <div className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
            style={{
              background: 'linear-gradient(135deg, #5c3a2a 0%, #4b2e22 50%, #3d2519 100%)',
              border: '1px solid #3d2519',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.1), 0 2px 4px rgba(28,25,23,0.15)'
            }}
          >
            <svg className="w-5 h-5 text-[#EAB308]" viewBox="0 0 24 24" fill="currentColor">
              <rect x="3" y="14" width="4" height="6" rx="1" />
              <rect x="10" y="8" width="4" height="12" rx="1" />
              <rect x="17" y="3" width="4" height="17" rx="1" />
            </svg>
          </div>

          <div>
            <div className="font-bold text-sm text-[#1C1917] leading-tight">SAI Tech Support</div>
            <div className="text-[10px] text-[#44403C] mt-0.5">Technical support, troubleshooting, and diagrams</div>
          </div>
        </div>

        {/* Status LED badge */}
        <div className="status-led flex items-center gap-1.5 px-2.5 py-1">
          <span className="w-2 h-2 rounded-full bg-[#16A34A] pulse-dot"
            style={{ boxShadow: '0 0 4px rgba(22, 163, 74, 0.5)' }}
          />
          <span className="text-[10px] font-bold text-[#16A34A]">Online</span>
        </div>
      </div>

      {/* Messages Area */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto chat-messages grid-bg px-3 py-4 sm:p-5"
      >
        <div className="max-w-3xl mx-auto w-full space-y-4">
          {messages.length === 0 && <WelcomeMessage />}

          {messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} onSuggestionClick={sendMessage} />
          ))}

          {isLoading && <TypingIndicator />}

          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <ChatInput />
    </div>
  )
}
