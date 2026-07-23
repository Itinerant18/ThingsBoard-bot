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
    <div className="flex flex-col h-full min-h-0 bg-[#F8FAFC]">
      {/* Executive Navy Header */}
      <div className="executive-header px-3 py-2.5 sm:px-5 sm:py-3.5 flex items-center justify-between flex-shrink-0 z-10">
        <div className="flex items-center gap-2.5 sm:gap-3">
          {/* Logo badge */}
          <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-[#0F172A] border border-slate-700/60 flex items-center justify-center flex-shrink-0 shadow-sm">
            <svg className="w-4 h-4 sm:w-5 sm:h-5 text-[#38BDF8]" viewBox="0 0 24 24" fill="currentColor">
              <rect x="3" y="14" width="4" height="6" rx="1" />
              <rect x="10" y="8" width="4" height="12" rx="1" />
              <rect x="17" y="3" width="4" height="17" rx="1" />
            </svg>
          </div>

          <div>
            <div className="font-bold text-xs sm:text-sm text-white leading-tight">SAI Assistant</div>
            <div className="text-[10px] sm:text-[11px] text-slate-400 font-medium">IoT Fleet Intelligence</div>
          </div>
        </div>

        {/* Status LED badge */}
        <div className="status-led flex items-center gap-1.5 px-2.5 py-1">
          <span className="w-2 h-2 rounded-full bg-emerald-400 pulse-dot" />
          <span className="text-[10px] font-semibold text-emerald-400">Online</span>
        </div>
      </div>

      {/* Messages Area — fills remaining height, scrolls internally */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 min-h-0 overflow-y-auto chat-messages dashboard-bg px-2 py-3 sm:px-4 sm:py-4"
      >
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

