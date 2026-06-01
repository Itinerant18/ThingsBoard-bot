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
    <div className="flex flex-col h-full bg-[#faf8f5]">
      {/* Header */}
      <div className="border-b border-[#d6cfc4] bg-[#faf8f5]/80 backdrop-blur-md px-4 py-3 flex items-center justify-between flex-shrink-0 z-10">
        <div className="flex items-center gap-2">
          <div className="w-9 h-9 rounded-xl bg-[#2e2620] flex items-center justify-center flex-shrink-0">
            {/* ThingsBoard/IoT styled logo */}
            <svg
              className="w-5 h-5 text-[#e2b13c]"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <rect x="3" y="14" width="4" height="6" rx="1" />
              <rect x="10" y="8" width="4" height="12" rx="1" />
              <rect x="17" y="3" width="4" height="17" rx="1" />
            </svg>
          </div>

          <div>
            <div className="font-bold text-xs text-[#221d17] leading-tight">SAI Tech Support</div>
            <div className="text-[9px] text-[#868078] truncate max-w-[120px] xs:max-w-none">Technical support, troubleshooting...</div>
          </div>
        </div>

        <div className="flex items-center gap-1.5 bg-[#eaf7f2] px-2 py-1 rounded-lg">
          <span className="w-1.5 h-1.5 rounded-full bg-[#10b981] pulse-dot" />
          <span className="text-[10px] font-bold text-[#10b981]">Online</span>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto chat-messages grid-bg p-4 space-y-4">
        {messages.length === 0 && <WelcomeMessage />}

        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} />
        ))}

        {isLoading && <TypingIndicator />}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <ChatInput />
    </div>
  )
}
