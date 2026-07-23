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
    /* h-full + min-h-0 — fills the iframe/container without overflowing.
       flex-col lets header + messages + input each get their natural height. */
    <div className="flex flex-col h-full min-h-0 bg-[#E8E0D4]">
      {/* Brushed-Metal Header — compact on small frames, roomier on large */}
      <div className="brushed-metal-header px-3 py-2 sm:px-5 sm:py-3 flex items-center justify-between flex-shrink-0 z-10">
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Embossed logo badge — scales with the frame */}
          <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-lg flex items-center justify-center flex-shrink-0"
            style={{
              background: 'linear-gradient(135deg, #5c3a2a 0%, #4b2e22 50%, #3d2519 100%)',
              border: '1px solid #3d2519',
              boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.1), 0 2px 4px rgba(28,25,23,0.15)'
            }}
          >
            <svg className="w-4 h-4 sm:w-5 sm:h-5 text-[#EAB308]" viewBox="0 0 24 24" fill="currentColor">
              <rect x="3" y="14" width="4" height="6" rx="1" />
              <rect x="10" y="8" width="4" height="12" rx="1" />
              <rect x="17" y="3" width="4" height="17" rx="1" />
            </svg>
          </div>

          <div>
            <div className="font-bold text-xs sm:text-sm text-[#1C1917] leading-tight">SAI</div>
          </div>
        </div>

        {/* Status LED badge */}
        <div className="status-led flex items-center gap-1 sm:gap-1.5 px-2 py-1 sm:px-2.5">
          <span className="w-1.5 h-1.5 sm:w-2 sm:h-2 rounded-full bg-[#16A34A] pulse-dot"
            style={{ boxShadow: '0 0 4px rgba(22, 163, 74, 0.5)' }}
          />
          <span className="text-[9px] sm:text-[10px] font-bold text-[#16A34A]">Online</span>
        </div>
      </div>

      {/* Messages Area — fills remaining height, scrolls internally */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 min-h-0 overflow-y-auto chat-messages grid-bg px-2 py-3 sm:px-4 sm:py-4"
      >
        {/* Remove max-w-3xl cap on narrow frames so messages use full width */}
        <div className="w-full max-w-3xl mx-auto space-y-3 sm:space-y-4">
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

