export interface ChatMessage {
  id: string
  role: 'user' | 'bot'
  content: string
  tokensUsed?: number
  timestamp: number
  metadata?: Record<string, unknown>
}

export interface ChatRequest {
  question: string
}

export interface ChatResponse {
  answer: string
  error: boolean
  errorMessage?: string
  tokensUsed?: number
  timestamp: number
}

export interface ChatContextType {
  messages: ChatMessage[]
  isLoading: boolean
  sendMessage: (question: string) => Promise<void>
  clearHistory: () => void
  jwtToken: string | null
}
