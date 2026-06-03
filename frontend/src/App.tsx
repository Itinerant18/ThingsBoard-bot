import { ChatProvider } from './context/ChatContext'
import { ChatWindow } from './components/ChatWindow'

function App() {
  return (
    <ChatProvider>
      <div className="w-full h-screen flex flex-col bg-[#faf8f5]">
        <ChatWindow />
      </div>
    </ChatProvider>
  )
}

export default App
