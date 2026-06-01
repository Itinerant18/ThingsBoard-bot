import React, { useState } from 'react'
import { ChatWindow } from './ChatWindow'

export const ChatToggle: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <>
      {/* Toggle Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`fixed bottom-6 right-6 w-16 h-16 rounded-full bg-accent-gold text-stone-950 flex items-center justify-center z-[99999] transition-all duration-300 hover:scale-110 ${
          isOpen ? 'rotate-45' : ''
        }`}
        aria-label="Open chat"
      >
        <svg
          className="w-7 h-7 transition-transform"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      </button>

      {/* Chat Window */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 w-[380px] h-[580px] max-h-[85vh] bg-[#faf8f5] rounded-3xl border border-[#d6cfc4] z-[99998] flex flex-col shadow-[0_12px_40px_rgba(34,29,23,0.15)] overflow-hidden">
          <ChatWindow />
        </div>
      )}

      {/* Backdrop for mobile */}
      {isOpen && (
        <button
          onClick={() => setIsOpen(false)}
          className="fixed inset-0 z-[99997] md:hidden"
          aria-label="Close chat"
        />
      )}
    </>
  )
}
