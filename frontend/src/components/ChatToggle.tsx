import React, { useState } from 'react'
import { ChatWindow } from './ChatWindow'
import { BotLogoSvg } from './BotLogoSvg'

export const ChatToggle: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`fixed bottom-6 right-6 w-16 h-16 rounded-full flex items-center justify-center z-[99999] transition-all duration-300 hover:scale-110 overflow-hidden ${
          isOpen ? 'bg-accent-gold text-stone-950 border border-[#d6cfc4] shadow-lg' : 'bg-transparent'
        }`}
        aria-label="Toggle chat"
      >
        {isOpen ? (
          <svg
            className="w-7 h-7"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        ) : (
          <div className="logo-toggle-container">
            <div className="logo-toggle-glow" />
            <BotLogoSvg className="w-[54px] h-[54px] logo-toggle-img" />
          </div>
        )}
      </button>

      {/* Chat Window */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 w-[380px] h-[580px] max-h-[85vh] glass-chat rounded-3xl z-[99998] flex flex-col overflow-hidden">
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
