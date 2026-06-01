# Development Guide — Frontend & Backend

## Project Structure

Organized into **frontend** and **backend** directories for clean separation.

```
ThingsBoard-Bot/
├── frontend/                          # React frontend (separate app)
│   ├── src/
│   │   ├── components/               # Chat UI components
│   │   ├── context/                  # ChatContext state
│   │   ├── types/                    # TypeScript defs
│   │   ├── styles/                   # Tailwind CSS
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   └── README.md
│
├── src/main/java/com/seple/ThingsBoard_Bot/
│   ├── controller/                   # REST APIs
│   ├── service/                      # Business logic
│   ├── model/                        # Data models
│   ├── config/                       # Spring config
│   └── client/                       # External APIs
│
├── src/main/resources/
│   ├── static/                       # Built frontend (outputs here)
│   │   ├── index.html
│   │   ├── *.js
│   │   └── *.css
│   └── application.properties
│
├── pom.xml                           # Maven build
└── README.md
```

## Frontend Setup

### Install

```bash
cd frontend
npm install
```

### Develop

```bash
npm run dev
```

Starts Vite at `http://localhost:5173` with proxy to `http://localhost:8080/api`.

### Build

```bash
npm run build
```

Outputs to `../src/main/resources/static/`. Spring Boot serves these assets.

## Backend Setup

### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 13+
- Redis (optional, for caching)

### Run

```bash
mvn spring-boot:run
```

Starts Spring Boot at `http://localhost:8080`.

## Full Stack Development

**Terminal 1 (Backend):**
```bash
mvn spring-boot:run
```

**Terminal 2 (Frontend):**
```bash
cd frontend && npm run dev
```

Visit `http://localhost:5173` → Frontend talks to backend via `/api` proxy.

## Frontend Architecture

### Components
- **ChatToggle** — Floating button + window container
- **ChatWindow** — Main chat UI
- **MessageBubble** — Message rendering (user + bot)
- **ChatInput** — Text input + send button
- **WelcomeMessage** — Initial state with quick actions
- **TypingIndicator** — Loading animation

### State Management
- **ChatContext** — Global chat state (messages, loading, JWT token)
- Auto-detects JWT from localStorage, URL params, or postMessage
- Message history in memory (add localStorage persistence if needed)

### Design System
- **Colors** (from CSSOM analysis):
  - Stone-950: `#1c1917` (primary dark)
  - Stone-900: `#44403c` (surface)
  - Accent-gold: `#ca8a04` (primary accent)
  - Accent-teal: `#0d9488` (secondary)
  - Stone-50: `#faf7f2` (light)

- **Typography**:
  - Display: DM Sans, semibold
  - Body: Trebuchet MS, regular

- **Depth**: Borders + color contrast (no shadows)

## Backend Architecture

### Controllers
- **ChatController** — POST `/api/v1/chat/ask`, GET `/api/v1/chat/init`

### Services
- **ChatService** — Orchestrates chat requests, calls OpenAI, manages context
- **ThingsBoardClient** — Fetches device data from TB
- **OpenAIClient** — Calls OpenAI API for responses
- Other services for caching, event handling, etc.

### Configuration
- `ChatbotConfig.java` — Chat-specific settings
- `ThingsBoardConfig.java` — TB connection
- `OpenAIConfig.java` — OpenAI keys
- `CorsConfig.java` — CORS headers (allows frontend requests)

## API Contract

### Chat Endpoint

**Request:**
```
POST /api/v1/chat/ask
Header: X-TB-Token: <jwt_token>
Body: { "question": "What devices do I have?" }
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

## Build & Deploy

### Development Build

Frontend + Backend run separately (hot reload):
```bash
# Terminal 1
mvn spring-boot:run

# Terminal 2
cd frontend && npm run dev
```

### Production Build

1. Build frontend:
   ```bash
   cd frontend && npm run build
   ```

2. Build backend (includes frontend assets):
   ```bash
   mvn clean package
   ```

3. Run JAR:
   ```bash
   java -jar target/ThingsBoard-Bot-1.0.0.jar
   ```

JAR includes static assets from `src/main/resources/static/`.

## Troubleshooting

### Frontend can't reach backend
- Backend should be on `:8080`
- Check `frontend/vite.config.ts` proxy URL
- Verify CORS in `CorsConfig.java`

### JWT token not detected
- Check browser localStorage for `jwt_token`
- Check URL for `?jwt_token=...` param
- Verify ThingsBoard sending postMessage

### Build fails
- Clear `node_modules`: `rm -rf node_modules && npm install`
- Clear Maven cache: `mvn clean`
- Update dependencies: `npm update`

### Port already in use
- Backend (`:8080`): `lsof -i :8080` → kill process
- Frontend (`:5173`): Configure in `vite.config.ts`

## Deployment Checklist

- [ ] Frontend built: `npm run build`
- [ ] Backend tests pass: `mvn test`
- [ ] JAR builds: `mvn clean package`
- [ ] Environment vars set (API keys, DB creds, etc.)
- [ ] Database migrations applied
- [ ] JWT tokens configured
- [ ] CORS headers correct
- [ ] Static assets served at `/` (Spring Boot default)

## Next Steps

- **Add SSE streaming** when backend supports it
- **Add localStorage persistence** for message history
- **Add syntax highlighting** for code blocks
- **Add device command UI** for actuation
- **Add analytics** for usage tracking
- **Add tests** (Jest + Vitest for frontend, JUnit for backend)

See individual README files:
- `frontend/README.md` — Frontend details
- Root `README.md` — High-level overview
