import React from 'react'
import { ChatProvider } from './context/ChatContext'
import { ChatToggle } from './components/ChatToggle'

function App() {
  return (
    <ChatProvider>
      <ChatToggle />
    </ChatProvider>
  )
}

export default App
