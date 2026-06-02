import React from 'react'
import { ChatMessage } from '../types'

interface MessageBubbleProps {
  message: ChatMessage
  onSuggestionClick?: (question: string) => void
}

const CollapsibleSection: React.FC<{ summary: string; content: string; isOpenDefault: boolean }> = ({
  summary,
  content,
  isOpenDefault
}) => {
  const [isOpen, setIsOpen] = React.useState(isOpenDefault)

  return (
    <div className="my-2 border border-[#d6cfc4] rounded-xl bg-white overflow-hidden shadow-sm transition-all">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between px-3 py-2.5 bg-[#fbfaf8] hover:bg-[#ca8a04]/5 text-stone-900 font-semibold text-xs transition-colors border-b border-[#e8e3d9]"
      >
        <span className="flex items-center gap-1.5 text-left">
          <span className="text-[#ca8a04] text-[9px]">{isOpen ? '▼' : '▶'}</span>
          {summary}
        </span>
        <span className="text-[9px] text-[#ca8a04] font-bold flex-shrink-0 ml-2">{isOpen ? 'Hide' : 'Show'}</span>
      </button>
      {isOpen && (
        <div className="p-3 text-xs whitespace-pre-wrap break-words leading-relaxed text-stone-700 bg-white">
          {content}
        </div>
      )}
    </div>
  )
}

interface Block {
  type: 'text' | 'details'
  content: string
  summary?: string
  isOpenDefault?: boolean
}

const parseContentBlocks = (text: string): Block[] => {
  const blocks: Block[] = []
  if (!text) return blocks

  const detailsRegex = /<details( open)?>\s*<summary>(.*?)<\/summary>(.*?)\s*<\/details>/gs
  let lastIndex = 0
  let match

  while ((match = detailsRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      blocks.push({
        type: 'text',
        content: text.substring(lastIndex, match.index)
      })
    }

    const isOpenDefault = !!match[1]
    const summaryHtml = match[2]
    const summary = summaryHtml.replace(/<[^>]*>/g, '').trim()
    const content = match[3].trim()

    blocks.push({
      type: 'details',
      summary,
      content,
      isOpenDefault
    })

    lastIndex = detailsRegex.lastIndex
  }

  if (lastIndex < text.length) {
    blocks.push({
      type: 'text',
      content: text.substring(lastIndex)
    })
  }

  return blocks
}

export const MessageBubble: React.FC<MessageBubbleProps> = ({ message, onSuggestionClick }) => {
  const isUser = message.role === 'user'

  let mainContent = message.content || ''
  let suggestions: string[] = []

  if (!isUser && mainContent.includes('[SUGGESTIONS]')) {
    const parts = mainContent.split('[SUGGESTIONS]')
    mainContent = parts[0].trim()
    const suggestionsText = parts[1] || ''

    suggestions = suggestionsText
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.startsWith('-') || line.startsWith('*'))
      .map(line => line.substring(1).trim().replace(/^\*|\*$/g, '').trim())
      .filter(line => line.length > 0)
  }

  const blocks = parseContentBlocks(mainContent)

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} message-enter`}>
      <div
        className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? 'bg-accent-gold text-stone-950 font-medium rounded-br-none'
            : 'bg-white/70 text-[#221d17] border border-[#d6cfc4]/60 rounded-bl-none'
        }`}
      >
        <div className="space-y-2">
          {blocks.map((block, idx) => {
            if (block.type === 'text') {
              return (
                <div key={idx} className="whitespace-pre-wrap break-words">
                  {block.content}
                </div>
              )
            } else {
              return (
                <CollapsibleSection
                  key={idx}
                  summary={block.summary || 'Details'}
                  content={block.content}
                  isOpenDefault={block.isOpenDefault || false}
                />
              )
            }
          })}
        </div>

        {suggestions.length > 0 && onSuggestionClick && (
          <div className="mt-3.5 pt-3 border-t border-[#e8e3d9] flex flex-col gap-1.5">
            <div className="text-[10px] font-bold text-stone-500 uppercase tracking-wider mb-0.5">Suggested follow-ups</div>
            <div className="flex flex-wrap gap-1.5">
              {suggestions.map((suggestion, idx) => (
                <button
                  key={idx}
                  onClick={() => onSuggestionClick(suggestion)}
                  className="px-2.5 py-1.5 bg-white hover:bg-[#ca8a04]/5 border border-[#d6cfc4] hover:border-[#ca8a04] active:bg-[#ca8a04]/10 rounded-lg text-xs font-semibold text-[#ca8a04] transition-all text-left shadow-sm hover:scale-[1.01]"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        {!isUser && typeof message.tokensUsed === 'number' && message.tokensUsed > 0 && (
          <div className="mt-2 flex items-center gap-1 text-xs text-stone-600 pt-2 border-t border-[#e8e3d9]">
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
