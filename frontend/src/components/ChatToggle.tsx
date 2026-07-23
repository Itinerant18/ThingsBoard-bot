import React, { useState } from 'react'
import { ChatWindow } from './ChatWindow'
import { BotLogoSvg } from './BotLogoSvg'

export const ChatToggle: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`pointer-events-auto fixed bottom-6 right-6 w-14 h-14 sm:w-16 sm:h-16 rounded-full flex items-center justify-center z-[99999] transition-all duration-300 hover:scale-105 shadow-xl ${
          isOpen ? 'bg-[#0F172A] text-white border border-slate-700' : 'bg-[#0F172A] text-white border border-slate-700'
        }`}
        aria-label="Toggle chat"
      >
        {isOpen ? (
          <svg
            className="w-6 h-6 text-slate-200"
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
          <div className="flex items-center justify-center">
            <BotLogoSvg className="w-10 h-10 sm:w-11 sm:h-11" />
          </div>
        )}
      </button>

      {/* Chat Window */}
      {isOpen && (
        <div className="pointer-events-auto fixed bottom-24 right-6 w-[380px] h-[580px] max-h-[85vh] bg-[#F8FAFC] rounded-2xl border border-slate-200 z-[99998] flex flex-col shadow-[0_20px_50px_rgba(15,23,42,0.18)] overflow-hidden">
          <ChatWindow />
        </div>
      )}

      {/* Backdrop for mobile */}
      {isOpen && (
        <button
          onClick={() => setIsOpen(false)}
          className="pointer-events-auto fixed inset-0 z-[99997] md:hidden"
          aria-label="Close chat"
        />
      )}
    </>
  )
}
