import React from 'react'
import { useChat } from '../context/ChatContext'

const QUICK_ACTIONS = [
  { label: 'Are there any offline branches in the network?', question: 'Are there any inactive branches?' },
  { label: 'Compare the status of SALTLAKE and MALDATOWN side-by-side', question: 'Compare BOI-SALTLAKE and BOI-MALDATOWN' },
  { label: 'Show the top 5 branches by alarm count', question: 'Top 5 branches by alarm count' },
  { label: 'Get a category-wise health breakdown of the IoT fleet', question: 'Show category-wise health' }
]

const TAGS = [
  { label: 'Compare Branches', question: 'Compare BOI-SALTLAKE and BOI-MALDATOWN' },
  { label: 'Top Alarms', question: 'Which branch has the most alarms?' },
  { label: 'CCTV Health', question: 'Show category-wise health of CCTV' },
  { label: 'Offline Branches', question: 'Are there any inactive branches?' },
  { label: 'Fleet Health Breakdown', question: 'Show category-wise health' }
]

export const WelcomeMessage: React.FC = () => {
  const { sendMessage } = useChat()

  return (
    <div className="flex-1 flex flex-col items-center justify-center py-10 px-4">
      {/* Embossed Bot Icon */}
      <div className="w-14 h-14 rounded-xl flex items-center justify-center mb-5"
        style={{
          background: 'linear-gradient(135deg, #5c3a2a 0%, #4b2e22 50%, #3d2519 100%)',
          border: '1px solid #3d2519',
          boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.1), 0 3px 8px rgba(75,46,34,0.25)'
        }}
      >
        <span className="text-2xl">🤖</span>
      </div>

      {/* Headers */}
      <h2 className="text-[#1C1917] text-2xl font-bold text-center tracking-tight mb-2">
        SAI Assistant
      </h2>
      <p className="text-[#44403C] text-xs text-center max-w-xl px-4 mb-9 leading-relaxed">
        I am SAI, your smart IoT monitoring assistant. Ask me about branch statuses, network health, active alerts, or regional breakdowns.
      </p>

      {/* Quick Actions — Paper Cards in 2x2 Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 w-full max-w-3xl mb-8">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.question}
            onClick={() => sendMessage(action.question)}
            className="paper-card text-left p-4 flex items-start gap-3 cursor-pointer"
          >
            <span className="text-[#CA8A04] font-mono text-sm leading-none mt-0.5"
              style={{ textShadow: '0 1px 0 rgba(255,255,255,0.6)' }}
            >➔</span>
            <span className="text-xs text-[#1C1917] font-semibold leading-normal">{action.label}</span>
          </button>
        ))}
      </div>

      {/* Suggestion Tags — Brass Pills */}
      <div className="flex flex-wrap gap-2 justify-center max-w-3xl">
        {TAGS.map((tag) => (
          <button
            key={tag.label}
            onClick={() => sendMessage(tag.question)}
            className="brass-tag px-3.5 py-2 text-[11px] text-[#44403C]"
          >
            {tag.label}
          </button>
        ))}
      </div>
    </div>
  )
}
