import React from 'react'

export const TypingIndicator: React.FC = () => {
  return (
    <div className="flex justify-start message-enter">
      <div className="dashboard-card px-4 py-3 flex gap-1.5 items-center rounded-2xl rounded-bl-none">
        <span className="w-2 h-2 rounded-full bg-[#2563EB] typing-dot" />
        <span className="w-2 h-2 rounded-full bg-[#2563EB] typing-dot" />
        <span className="w-2 h-2 rounded-full bg-[#2563EB] typing-dot" />
      </div>
    </div>
  )
}
