import React from 'react'
import { useChat } from '../context/ChatContext'

const QUICK_ACTIONS = [
  { label: 'My Devices', emoji: '📱', question: 'What devices do I have?' },
  { label: 'Low Battery', emoji: '🔋', question: 'Are there any low battery devices?' },
  { label: 'Active Alarms', emoji: '🚨', question: 'Are there any active alarms?' },
  { label: 'Device Status', emoji: '📡', question: 'Are all my devices online?' }
]

export const WelcomeMessage: React.FC = () => {
  const { sendMessage } = useChat()

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-8">
      <div className="text-center space-y-4 max-w-sm">
        <div className="text-5xl">🤖</div>

        <div>
          <h3 className="text-lg font-semibold text-stone-50 mb-2">Hey! I'm your IoT Assistant</h3>
          <p className="text-sm text-stone-600">Ask me anything about your devices — status, battery health, or alarms.</p>
        </div>

        <div className="flex flex-wrap gap-2 justify-center">
          {QUICK_ACTIONS.map((action) => (
            <button
              key={action.question}
              onClick={() => sendMessage(action.question)}
              className="px-3 py-2 bg-stone-800 border border-stone-700 rounded-full text-xs text-stone-50 hover:border-accent-gold hover:bg-accent-gold/10 transition-colors"
            >
              {action.emoji} {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
