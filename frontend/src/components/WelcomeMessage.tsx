import React from 'react'
import { useChat } from '../context/ChatContext'

const QUICK_ACTIONS = [
  { label: 'Are there any inactive branches in the network?', question: 'Are there any inactive branches?' },
  { label: 'Check the current battery health and low voltage sensors', question: 'Are there any low battery devices?' },
  { label: 'List all active alarms and critical alerts across regions', question: 'Are there any active alarms?' },
  { label: 'What is the CCTV camera online status of BALLY BAZAR?', question: 'What is the CCTV status of BALLY BAZAR?' }
]

const TAGS = [
  { label: 'Low Battery', question: 'Are there any low battery devices?' },
  { label: 'Active Alarms', question: 'Are there any active alarms?' },
  { label: 'CCTV Status', question: 'What is the CCTV status?' },
  { label: 'Offline Branches', question: 'Are there any inactive branches?' },
  { label: 'System Health', question: 'What is the system health overview?' }
]

export const WelcomeMessage: React.FC = () => {
  const { sendMessage } = useChat()

  return (
    <div className="flex-1 flex flex-col items-center justify-center py-6 px-2">
      {/* Bot Icon */}
      <div className="w-12 h-12 rounded-2xl bg-[#cfe6de] border border-[#a2cebe] flex items-center justify-center mb-4 shadow-sm">
        <span className="text-2xl">🤖</span>
      </div>

      {/* Main Headers */}
      <h2 className="text-[#221d17] text-lg font-bold text-center mb-1">
        Ask about ThingsBoard IoT Network
      </h2>
      <p className="text-[#868078] text-xs text-center max-w-sm px-4 mb-6 leading-normal">
        I am SAI, your ThingsBoard support assistant. Ask me anything — troubleshooting, telemetry, or active alarms.
      </p>

      {/* Quick Actions (Large Cards) */}
      <div className="w-full max-w-sm space-y-2 mb-6">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.question}
            onClick={() => sendMessage(action.question)}
            className="w-full text-left bg-white/50 hover:bg-white/80 border border-[#d6cfc4]/60 rounded-2xl p-3.5 flex items-start gap-3 transition-all hover:scale-[1.01] hover:border-[#ca8a04] shadow-[0_2px_4px_rgba(34,29,23,0.02)]"
          >
            <span className="text-[#ca8a04] font-mono text-sm leading-none mt-0.5">➔</span>
            <span className="text-xs text-[#221d17] font-medium leading-normal">{action.label}</span>
          </button>
        ))}
      </div>

      {/* Suggestion Tags */}
      <div className="flex flex-wrap gap-1.5 justify-center max-w-sm">
        {TAGS.map((tag) => (
          <button
            key={tag.label}
            onClick={() => sendMessage(tag.question)}
            className="px-3 py-1.5 bg-white/40 hover:bg-[#f4ebd0]/80 border border-[#d6cfc4]/60 hover:border-[#ca8a04] rounded-full text-[10px] font-semibold text-[#57534e] hover:text-[#ca8a04] transition-all"
          >
            {tag.label}
          </button>
        ))}
      </div>
    </div>
  )
}
