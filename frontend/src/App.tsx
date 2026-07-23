import { ChatProvider } from './context/ChatContext'
import { ChatWindow } from './components/ChatWindow'
import { ErrorBoundary } from './components/ErrorBoundary'

function App() {
  return (
    <ErrorBoundary>
      <ChatProvider>
        {/* Use h-full (not h-screen) so the widget fills the iframe/container,
            whatever size ThingsBoard gives it — not always the full viewport. */}
        <div className="w-full h-full min-h-0 flex flex-col bg-[#faf8f5] overflow-hidden">
          <ChatWindow />
        </div>
      </ChatProvider>
    </ErrorBoundary>
  )
}

export default App
