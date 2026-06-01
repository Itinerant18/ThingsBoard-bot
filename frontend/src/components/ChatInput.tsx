import React, { useState, useRef } from 'react'
import { useChat } from '../context/ChatContext'

export const ChatInput: React.FC = () => {
  const { sendMessage, isLoading } = useChat()
  const [input, setInput] = useState('')
  const [isListening, setIsListening] = useState(false)
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

  const startVoiceInput = () => {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Speech recognition is not supported in this browser.')
      return
    }

    if (isListening) {
      setIsListening(false)
      return
    }

    const recognition = new SpeechRecognition()
    recognition.continuous = false
    recognition.interimResults = false
    recognition.lang = 'en-US'

    recognition.onstart = () => {
      setIsListening(true)
    }

    recognition.onresult = (event: any) => {
      const transcript = event.results[0][0].transcript
      setInput((prev) => prev + (prev ? ' ' : '') + transcript)
    }

    recognition.onerror = (event: any) => {
      console.error('Speech recognition error:', event.error)
      setIsListening(false)
    }

    recognition.onend = () => {
      setIsListening(false)
    }

    recognition.start()
  }

  return (
    <div className="border-t border-[#d6cfc4] bg-[#faf8f5] px-4 py-3.5 flex-shrink-0">
      <div className="flex items-center gap-2 relative">
        <div className="relative flex-1">
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask anything..."
            disabled={isLoading}
            className="w-full bg-[#faf8f5] border border-[#d6cfc4] rounded-2xl pl-4 pr-10 py-3.5 text-xs text-[#221d17] placeholder-[#868078] outline-none transition-all focus:border-[#ca8a04] focus:ring-1 focus:ring-[#ca8a04]/20 disabled:opacity-50 shadow-[inset_0_1px_2px_rgba(0,0,0,0.02)]"
          />

          {/* Microphone Icon */}
          <button 
            type="button"
            onClick={startVoiceInput}
            className={`absolute right-3 top-1/2 -translate-y-1/2 transition-colors p-1 rounded-full ${
              isListening 
                ? 'text-red-500 bg-red-50 animate-pulse' 
                : 'text-[#868078] hover:text-[#ca8a04]'
            }`}
            title={isListening ? "Listening... Click to stop" : "Voice Input"}
          >
            <svg
              className="w-4 h-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="22" />
            </svg>
          </button>
        </div>

        {/* Send Button */}
        <button
          onClick={handleSend}
          disabled={!input.trim() || isLoading}
          className="w-11 h-11 rounded-2xl bg-[#ca8a04] hover:bg-[#a16f03] text-stone-950 flex items-center justify-center transition-all hover:scale-105 disabled:opacity-30 disabled:hover:scale-100 disabled:cursor-not-allowed shadow-sm flex-shrink-0"
        >
          <svg
            className="w-4.5 h-4.5 transform rotate-45 -translate-x-0.5 translate-y-0.5 text-stone-950"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
      </div>
    </div>
  )
}
