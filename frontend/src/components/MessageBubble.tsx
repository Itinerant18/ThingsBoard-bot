import React from 'react'
import { ChatMessage } from '../types'

const renderFormattedText = (text: string) => {
  if (!text) return null
  const parts = text.split('**')
  return parts.map((part, idx) => {
    if (idx % 2 === 1) {
      return <strong key={idx} className="font-extrabold text-[#1C1917]">{part}</strong>
    }
    return part
  })
}

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
    <div className="my-2 border border-[#d6cfc4] rounded-lg overflow-hidden shadow-[inset_0_1px_3px_rgba(0,0,0,0.06)] bg-[#FAF7F2]/50">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between px-3 py-2 bg-[#f0ede8] hover:bg-[#CA8A04]/10 text-stone-900 font-bold text-xs transition-colors border-b border-[#d6cfc4] cursor-pointer"
      >
        <span className="flex items-center gap-1.5 text-left">
          <span className="text-[#CA8A04] text-[9px]">{isOpen ? '▼' : '▶'}</span>
          {summary}
        </span>
        <span className="text-[9px] text-[#CA8A04] font-bold flex-shrink-0 ml-2">{isOpen ? 'Hide' : 'Show'}</span>
      </button>
      {isOpen && (
        <div className="p-3 text-xs whitespace-pre-wrap break-words leading-relaxed text-stone-700 bg-[#FAF7F2]">
          {renderFormattedText(content)}
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
    <div className={`flex gap-2 sm:gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'} message-enter items-start w-full max-w-[96%] sm:max-w-[85%] ${isUser ? 'ml-auto' : 'mr-auto'}`}>
      {/* Avatar with vintage skeuomorphic border */}
      <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 shadow-md border ${
        isUser 
          ? 'text-[#FAF7F2] font-bold text-xs border-[#3d2519]' 
          : 'text-[#EAB308] font-bold text-xs border-[#3d2519]'
      }`}
      style={{
        background: isUser 
          ? 'linear-gradient(135deg, #7c4f37 0%, #5c3a2a 50%, #4b2e22 100%)' 
          : 'linear-gradient(135deg, #2e2620 0%, #1c1917 50%, #0c0a09 100%)',
        boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.1), 0 2px 4px rgba(0,0,0,0.15)'
      }}>
        {isUser ? 'U' : '🤖'}
      </div>

      {/* Bubble */}
      <div
        className={`px-4.5 py-3 text-sm leading-relaxed shadow-md ${
          isUser
            ? 'leather-bubble rounded-tr-none'
            : 'paper-card rounded-tl-none text-[#1C1917]'
        }`}
        style={!isUser ? { transform: 'none' } : undefined} // Prevent hover translateY on message bubbles to keep text stable
      >
        <div className="space-y-2">
          {blocks.map((block, idx) => {
            if (block.type === 'text') {
              return (
                <div key={idx} className="whitespace-pre-wrap break-words">
                  {renderFormattedText(block.content)}
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
          {!isUser && message.streaming && (
            <span className="streaming-cursor" aria-hidden="true" />
          )}
        </div>

        {suggestions.length > 0 && onSuggestionClick && (
          <div className="mt-3.5 pt-3 border-t border-[#D6CFC4] flex flex-col gap-1.5">
            <div className="text-[9px] font-bold text-[#78716c] uppercase tracking-wider mb-0.5"
              style={{ textShadow: '0 1px 0 rgba(255,255,255,0.6)' }}
            >
              Suggested follow-ups
            </div>
            <div className="flex flex-wrap gap-1.5">
              {suggestions.map((suggestion, idx) => (
                <button
                  key={idx}
                  onClick={() => onSuggestionClick(suggestion)}
                  className="brass-tag px-3 py-1.5 text-xs text-[#44403C] hover:text-[#92400e] text-left transition-all"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        {!isUser && typeof message.tokensUsed === 'number' && message.tokensUsed > 0 && (
          <div className="mt-2 flex items-center gap-1 text-[10px] text-stone-500 pt-2 border-t border-[#D6CFC4] font-medium"
            style={{ textShadow: '0 1px 0 rgba(255,255,255,0.6)' }}
          >
            <svg
              className="w-3 h-3 text-[#CA8A04]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
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
