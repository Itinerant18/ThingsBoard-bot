import React from 'react'

export const TypingIndicator: React.FC = () => {
  return (
    <div className="flex justify-start message-enter">
      <div className="bg-stone-900 border border-stone-800 rounded-2xl rounded-bl-none px-4 py-3 flex gap-1 items-center">
        <span className="w-2 h-2 rounded-full bg-accent-gold typing-dot" />
        <span className="w-2 h-2 rounded-full bg-accent-gold typing-dot" />
        <span className="w-2 h-2 rounded-full bg-accent-gold typing-dot" />
      </div>
    </div>
  )
}
