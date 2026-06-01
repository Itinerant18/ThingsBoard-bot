# IoT Assistant Chatbot Frontend

Modern React + TypeScript + Tailwind CSS chatbot interface for ThingsBoard dashboard integration.

## Setup

### Prerequisites
- Node.js 18+ with npm/pnpm/yarn

### Install & Build

```bash
cd frontend

# Install dependencies
npm install

# Development server (with proxy to backend)
npm run dev

# Build for production (outputs to ../src/main/resources/static)
npm run build
```

## Architecture

### Components
- **ChatToggle** — Fixed button to open/close chat widget
- **ChatWindow** — Main chat interface with header, messages, input
- **MessageBubble** — User & bot message rendering with token metadata
- **TypingIndicator** — Loading state animation
- **ChatInput** — Text input with send button (Shift+Enter for newline, Enter to send)
- **WelcomeMessage** — Initial state with quick action buttons

### Context
- **ChatContext** — Global state for messages, loading, JWT token
- Automatic JWT token detection from localStorage, URL params, or postMessage
- Message history stored in state (memory only; add persistence if needed)

## Design Tokens

Colors (from CSSOM analysis):
- **Primary**: `#1c1917` (stone-950)
- **Surface**: `#44403c` (stone-900)
- **Accent Gold**: `#ca8a04`
- **Accent Teal**: `#0d9488`
- **Light**: `#faf7f2` (stone-50)

Typography:
- Display: DM Sans, semibold
- Body: Trebuchet MS, regular

No shadows; depth via borders & color contrast.

## API Integration

Connects to backend `/api/v1/chat/ask`:

**Request:**
```json
{
  "question": "What devices do I have?"
}
```

**Response:**
```json
{
  "answer": "You have 3 devices...",
  "error": false,
  "tokensUsed": 145,
  "timestamp": 1704067200000
}
```

Auth via `X-TB-Token` header (JWT from ThingsBoard).

## ThingsBoard Integration

Works as embedded iframe widget:
- Detects JWT token from multiple sources
- Listens for postMessage with `{ type: 'TB_AUTH_TOKEN', token: '...' }`
- Scales to fit dashboard panel
- Fixed bottom-right position (can move to inline via prop)

## Development

### Hot Reload
Vite dev server with proxy to `http://localhost:8080/api` for backend calls.

```bash
npm run dev
```

Visit `http://localhost:5173` (adjust port if needed).

### Build Output
Production build outputs to `../src/main/resources/static/`:
- `index.html`
- Bundled JS/CSS (minified)
- Ready to serve by Spring Boot

## Folder Structure

```
frontend/
├── src/
│   ├── components/          # React components
│   │   ├── ChatToggle.tsx
│   │   ├── ChatWindow.tsx
│   │   ├── MessageBubble.tsx
│   │   ├── ChatInput.tsx
│   │   ├── WelcomeMessage.tsx
│   │   └── TypingIndicator.tsx
│   ├── context/            # State management
│   │   └── ChatContext.tsx
│   ├── types/              # TypeScript types
│   │   └── index.ts
│   ├── styles/             # Global styles
│   │   └── globals.css
│   ├── App.tsx
│   └── main.tsx
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
└── README.md (this file)
```

## Customization

### Add Persistent History
In `src/context/ChatContext.tsx`, add localStorage persistence:
```typescript
useEffect(() => {
  localStorage.setItem('chat_history', JSON.stringify(messages))
}, [messages])
```

### Add Streaming Responses
Replace fetch with EventSource when backend supports SSE at `/api/v1/chat/ask/stream`.

### Customize Colors
Edit `tailwind.config.ts` theme section.

### Responsive Tweaks
Adjust breakpoints in components (currently `w-96 h-96` for chat window).

## Troubleshooting

**"Cannot reach server"** — Check:
- Backend is running on `http://localhost:8080`
- CORS headers are set (Spring Boot should have CorsConfig)
- JWT token is valid

**Token not detected** — Ensure one of:
- `?jwt_token=...` in URL
- `localStorage.jwt_token` is set
- `postMessage({ type: 'TB_AUTH_TOKEN', token: '...' })` is sent from parent

**Styles not loading** — Run `npm run build` to generate CSS bundle.

## Next Steps

- [ ] Add message persistence (localStorage or DB)
- [ ] Implement streaming responses (SSE)
- [ ] Add code block syntax highlighting
- [ ] Add message edit/regenerate UI
- [ ] Add device data visualization in chat
- [ ] Add copy-to-clipboard for messages
- [ ] Theme toggle (dark/light) if needed
