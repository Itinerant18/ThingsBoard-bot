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
    <div className="flex-1 flex flex-col items-center justify-center py-4 sm:py-8 px-2 sm:px-4">
      {/* Bot Icon */}
      <div className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl flex items-center justify-center mb-3 sm:mb-4 bg-gradient-to-br from-[#0F172A] to-[#1E293B] border border-slate-700 shadow-md">
        <span className="text-xl sm:text-2xl">🤖</span>
      </div>

      {/* Headers */}
      <h2 className="text-[#0F172A] text-lg sm:text-2xl font-bold text-center tracking-tight mb-1 sm:mb-1.5">
        SAI Assistant
      </h2>
      <p className="text-[#64748B] text-xs sm:text-sm text-center max-w-xs sm:max-w-xl px-2 mb-5 sm:mb-7 leading-relaxed">
        I am SAI, your smart assistant for IoT network analytics & fleet management.
      </p>

      {/* Quick Actions */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 sm:gap-3 w-full max-w-xl mb-4 sm:mb-6">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.question}
            onClick={() => sendMessage(action.question)}
            className="dashboard-card text-left p-3 sm:p-4 flex items-start gap-2.5 sm:gap-3 cursor-pointer group"
          >
            <span className="text-[#2563EB] font-sans text-xs sm:text-sm leading-none mt-0.5 group-hover:translate-x-0.5 transition-transform">
              ➔
            </span>
            <span className="text-xs sm:text-sm text-[#1E293B] font-medium leading-normal">{action.label}</span>
          </button>
        ))}
      </div>

      {/* Suggestion Tags */}
      <div className="flex flex-wrap gap-1.5 sm:gap-2 justify-center max-w-xl">
        {TAGS.map((tag) => (
          <button
            key={tag.label}
            onClick={() => sendMessage(tag.question)}
            className="dashboard-tag px-3 py-1.5 text-xs text-[#334155]"
          >
            {tag.label}
          </button>
        ))}
      </div>
    </div>
  )
}

