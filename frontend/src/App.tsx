import { ChatProvider } from './context/ChatContext'
import { ChatToggle } from './components/ChatToggle'

function App() {
  return (
    <ChatProvider>
      {/* No wrapper div — all ChatToggle elements are fixed-positioned.
          Body/root have pointer-events:none so only the fixed elements intercept clicks. */}
      <ChatToggle />
    </ChatProvider>
  )
}

export default App
