import React from 'react'
import { ChatMessage } from '../types'

interface MessageBubbleProps {
  message: ChatMessage
}

export const MessageBubble: React.FC<MessageBubbleProps> = ({ message }) => {
  const isUser = message.role === 'user'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} message-enter`}>
      <div
        className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? 'bg-accent-gold text-stone-950 font-medium rounded-br-none'
            : 'bg-stone-900 text-stone-50 border border-stone-800 rounded-bl-none'
        }`}
      >
        <div className="whitespace-pre-wrap break-words">{message.content}</div>

        {!isUser && message.tokensUsed && (
          <div className="mt-2 flex items-center gap-1 text-xs text-stone-600 pt-2 border-t border-stone-800">
            <svg
              className="w-3 h-3"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v6l4 2" />
            </svg>
            {message.tokensUsed} tokens
          </div>
        )}
      </div>
    </div>
  )
}
