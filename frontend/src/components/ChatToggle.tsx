import React, { useState } from 'react'
import { ChatWindow } from './ChatWindow'

export const ChatToggle: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <>
      {/* Toggle Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`fixed bottom-6 right-6 w-15 h-15 rounded-full bg-accent-gold text-stone-950 flex items-center justify-center z-[99999] transition-all duration-300 hover:scale-110 ${
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
        <div className="fixed bottom-24 right-6 w-96 h-96 bg-stone-950 rounded-3xl border border-stone-800 z-[99998] flex flex-col shadow-2xl">
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
