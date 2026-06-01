import React, { useState, useRef } from 'react'
import { useChat } from '../context/ChatContext'

export const ChatInput: React.FC = () => {
  const { sendMessage, isLoading } = useChat()
  const [input, setInput] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const handleSend = async () => {
    if (input.trim() && !isLoading) {
      await sendMessage(input)
      setInput('')
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !isLoading) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="border-t border-stone-800 bg-stone-900 p-3 space-y-2">
      <p className="text-center text-[10px] text-stone-600 opacity-50">
        AI can make mistakes — verify important info
      </p>

      <div className="flex items-center gap-2">
        <input
          ref={inputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask about your devices…"
          disabled={isLoading}
          className="flex-1 bg-stone-800 border border-stone-700 rounded-3xl px-4 py-2.5 text-sm text-stone-50 placeholder-stone-600 outline-none transition-colors focus:border-accent-gold focus:ring-1 focus:ring-accent-gold/20 disabled:opacity-50"
        />

        <button
          onClick={handleSend}
          disabled={!input.trim() || isLoading}
          className="w-10 h-10 rounded-full bg-accent-gold text-stone-950 flex items-center justify-center font-semibold transition-all hover:scale-105 disabled:opacity-30 disabled:hover:scale-100 disabled:cursor-not-allowed"
        >
          <svg
            className="w-5 h-5"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
        </button>
      </div>
    </div>
  )
}
