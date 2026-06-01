import { ChatProvider } from './context/ChatContext'
import { ChatWindow } from './components/ChatWindow'

function App() {
  return (
    <ChatProvider>
      <div className="min-h-screen w-full flex items-center justify-center bg-[#ece8de] p-0 sm:p-4">
        <div className="w-full max-w-[460px] h-screen sm:h-[850px] bg-[#faf8f5] sm:border sm:border-[#d6cfc4] sm:rounded-[32px] overflow-hidden flex flex-col sm:shadow-[0_20px_50px_rgba(34,29,23,0.12)] relative">
          <ChatWindow />
        </div>
      </div>
    </ChatProvider>
  )
}

export default App
