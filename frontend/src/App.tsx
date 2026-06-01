import { ChatProvider } from './context/ChatContext'
import { ChatToggle } from './components/ChatToggle'

function App() {
  return (
    <ChatProvider>
      {/* Container is transparent and non-blocking for dashboard placement */}
      <div className="w-screen h-screen bg-transparent pointer-events-none relative overflow-hidden">
        <div className="pointer-events-auto">
          <ChatToggle />
        </div>
      </div>
    </ChatProvider>
  )
}

export default App
