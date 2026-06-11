import { ChatProvider } from './context/ChatContext'
import { ChatWindow } from './components/ChatWindow'
import { ErrorBoundary } from './components/ErrorBoundary'

function App() {
  return (
    <ErrorBoundary>
      <ChatProvider>
        <div className="w-full h-screen flex flex-col bg-[#faf8f5]">
          <ChatWindow />
        </div>
      </ChatProvider>
    </ErrorBoundary>
  )
}

export default App
