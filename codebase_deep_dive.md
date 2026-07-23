# ThingsBoard-Bot (SAI) — Complete Codebase Deep Dive

> **SAI — Smart Assistant for IoT.** A Context-Augmented Generation (CAG) chatbot that lets users query live IoT device data from ThingsBoard using plain English.

---

## 1. What This System Does

SAI sits between:
- **ThingsBoard** (IoT platform with 143+ devices, branches, telemetry)
- **End users** (bank/facility managers, SOC teams) who ask questions in plain English

The bot fetches verified, scoped live data (not hallucinated) and formats it as professional reports with interactive suggestion chips.

---

## 2. Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Backend | Java 21 + Spring Boot 4.0.3 | Core application framework |
| Database | TimescaleDB / PostgreSQL 15+ | Historical event store (hypertable) |
| Cache | Redis (Upstash) | Live device state mirror |
| Message Broker | RabbitMQ (CloudAMQP) | Real-time event ingestion |
| Frontend | React + TypeScript + Vite | Chat UI (embedded via iframe) |
| Build | Maven 3.8+ | Dependency & build management |
| LLM | OpenAI GPT-4o | Natural language generation |
| Reverse Proxy | Caddy 2.x | SSE flushing + TLS termination |
| Deployment | Docker + docker-compose | Containerized production stack |
| Auth | JJWT (HMAC/RSA) | JWT parsing and verification |
| HTTP Client | OkHttp 4.12 | OpenAI + ThingsBoard API calls |
| Fuzzy Match | Apache Commons Text (Jaro-Winkler) | Branch name resolution |

---

## 3. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ CLIENT LAYER                                                     │
│   ThingsBoard Dashboard ──(postMessage JWT)──► React Chat UI    │
└────────────────────────────┬─────────────────────────────────────┘
                             │ HTTPS / SSE
┌────────────────────────────▼─────────────────────────────────────┐
│ PROXY LAYER                                                      │
│   Caddy Reverse Proxy                                            │
│   • SSE flush_interval -1 (real-time streaming)                 │
│   • TLS termination (Let's Encrypt via nip.io)                  │
│   • Security headers (X-Content-Type-Options, Referrer-Policy)  │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│ SPRING BOOT APPLICATION                                          │
│                                                                  │
│  ┌─────── Profile: chat ──────────────────────────┐             │
│  │  ChatController  →  QueryIntentResolver         │             │
│  │  UserDataService →  BranchSnapshotMapper        │             │
│  │  ChatService     →  OpenAI GPT-4o              │             │
│  └─────────────────────────────────────────────────┘             │
│                                                                  │
│  ┌─── Profile: ingestion ─────┐  ┌─── Profile: consumer ────┐  │
│  │  WebhookController         │  │  EventConsumerService     │  │
│  │  (receives TB webhooks,    │  │  (reads RabbitMQ, writes  │  │
│  │   publishes to RabbitMQ)   │  │   TimescaleDB + Redis)    │  │
│  └────────────────────────────┘  └──────────────────────────┘  │
└──────────┬───────────────────────────────────┬───────────────────┘
           │ Publish                           │ Read
┌──────────▼──────────────┐    ┌───────────────▼────────────────────┐
│ MESSAGING LAYER         │    │ STORAGE LAYER                      │
│ RabbitMQ (CloudAMQP)    │    │                                    │
│  • Exchange: iot.exchange│    │ Redis (Upstash)                   │
│  • Queue: iot.events    │───►│  • Device active state             │
│  • DLQ: iot.events.dlq  │    │  • Replay distributed locks       │
│  • DLX: iot.dlx         │    │  • Idempotency keys               │
└─────────────────────────┘    │                                    │
                               │ TimescaleDB (Cloud)                │
                               │  • device_events (hypertable)      │
                               │  • hierarchy_nodes                 │
                               │  • branch_ancestor_paths           │
                               │  • customers table                 │
                               └────────────────────────────────────┘
```

---

## 4. Spring Profiles — Runtime Roles

The application is split into **3 independent runtime roles** via Spring Profiles. They can run in one JVM or separate containers.

| Profile | Role | Key Beans |
|---|---|---|
| `chat` | Serves chat API, reads Redis + TimescaleDB | `ChatController`, `CustomerSyncRunner`, `ReplayService` |
| `ingestion` | Receives ThingsBoard webhooks → publishes to RabbitMQ | `WebhookController`, `RabbitMQConfig` |
| `consumer` | Consumes RabbitMQ → writes TimescaleDB + Redis | `EventConsumerService`, `RabbitMQConfig` |
| `dev` | Local overrides | `application-dev.properties`, `.env` file |

**Local dev (all-in-one):**
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,ingestion,chat,consumer" "-Dspring-boot.run.arguments=--server.port=8083"
```

**Production (separate containers):** See `docs/deployment-topology.md`

---

## 5. Folder & File Structure

```
ThingsBoard-Bot/
├── frontend/                         ← React + TypeScript + Vite UI
│   └── src/
│       ├── components/               ← ChatToggle, ChatWindow, MessageBubble, ChatInput
│       ├── context/ChatContext.tsx   ← postMessage auth, SSE streaming, token state
│       ├── types/index.ts            ← Discriminated-union SSE frame types
│       └── styles/                   ← Tailwind CSS
│
├── src/main/java/com/seple/ThingsBoard_Bot/
│   ├── ThingsBoardBotApplication.java ← Spring Boot entry point
│   ├── client/
│   │   ├── OpenAIClient.java          ← GPT-4o streaming + non-streaming calls
│   │   ├── ThingsBoardClient.java     ← Core TB REST API client
│   │   └── UserAwareThingsBoardClient.java ← Scoped TB client (per-tenant, SSRF-guarded)
│   ├── config/
│   │   ├── RabbitMQConfig.java        ← Exchange, Queue, DLQ, DLX topology
│   │   ├── RedisConfig.java           ← Lettuce connection + serialization
│   │   ├── SecurityProperties.java    ← All security feature flags
│   │   ├── DatabaseInitializer.java   ← TimescaleDB hypertable setup
│   │   ├── DotenvEnvironmentPostProcessor.java ← .env file loading
│   │   ├── JwtParserInitializer.java  ← JWT signature key setup
│   │   ├── CorsConfig.java            ← CORS headers
│   │   ├── ContentSecurityPolicyFilter.java ← frame-ancestors CSP
│   │   ├── AdminAuthFilter.java       ← Bearer token gate for admin endpoints
│   │   └── ... (15 more config classes)
│   ├── controller/
│   │   ├── ChatController.java        ← POST /api/v1/chat/ask/stream (SSE)
│   │   ├── WebhookController.java     ← POST /api/v1/webhook (ThingsBoard events)
│   │   ├── ReplayController.java      ← POST /api/v1/admin/replay
│   │   ├── DataController.java        ← Data inspection / debug endpoints
│   │   └── HierarchyAdminController.java ← Hierarchy management endpoints
│   ├── service/
│   │   ├── ChatService.java           ← Main chat orchestrator (42 KB)
│   │   ├── UserDataService.java       ← Tenant scope resolution (22 KB)
│   │   ├── EventConsumerService.java  ← RabbitMQ consumer + idempotency
│   │   ├── EventWriteService.java     ← DB writer for consumed events
│   │   ├── EventParseService.java     ← Parses raw TB webhook payloads
│   │   ├── RedisCacheService.java     ← All Redis reads/writes
│   │   ├── ReplayService.java         ← Replay with distributed lock
│   │   ├── CustomerSyncRunner.java    ← Auto-sync customer UUID→prefix on startup
│   │   ├── CustomerMatcher.java       ← UUID-to-prefix mapping logic
│   │   ├── IdempotencyService.java    ← Redis-based deduplication (SET NX)
│   │   ├── LuaScriptService.java      ← Atomic Redis counter updates via Lua
│   │   ├── ChatMemoryService.java     ← Conversation memory management
│   │   ├── ReconciliationService.java ← Drift detection between Redis and DB
│   │   ├── ResponseEvaluationService.java ← AI response quality evaluation
│   │   ├── ChartService.java          ← Time-series chart generation
│   │   ├── DataService.java           ← Device data aggregation
│   │   ├── AncestorPathService.java   ← Hierarchy path resolution
│   │   ├── AncestorPathCache.java     ← JVM in-memory cache (5-min TTL)
│   │   ├── RabbitMQQueueService.java  ← Queue management helpers
│   │   ├── normalization/             ← Field name normalization, alias resolution
│   │   └── query/                     ← Intent resolution + query handlers
│   │       ├── QueryIntentResolver.java   ← Classifies user queries (38 KB)
│   │       ├── QueryRouterService.java    ← Routes resolved intent to handler
│   │       ├── DeterministicAnswerService.java ← Truth-injection into LLM prompt
│   │       ├── AnswerTemplateService.java ← Format templates per intent
│   │       ├── IntentKeyProfileRegistry.java ← Maps intents to Redis key profiles
│   │       ├── QueryRuleRegistry.java     ← Business rule definitions
│   │       └── handler/               ← 18 domain-specific handlers:
│   │           ├── CctvHandler.java       ← CCTV camera status + channel ranges
│   │           ├── PowerHandler.java      ← Power / UPS / battery
│   │           ├── AlertHandler.java      ← Alarm events
│   │           ├── FleetAnalyticsHandler.java ← Multi-branch analytics (18 KB)
│   │           ├── GlobalOverviewHandler.java  ← Fleet-wide summary (12 KB)
│   │           ├── NetworkStatusHandler.java   ← Network / SIM status
│   │           ├── GatewayStatusHandler.java   ← Gateway device health
│   │           ├── HierarchyHandler.java       ← Branch hierarchy queries
│   │           ├── DeviceInventoryHandler.java ← Device list queries
│   │           ├── DeviceIdentityHandler.java  ← Device info lookup
│   │           ├── SubsystemHandler.java       ← Generic sub-system queries
│   │           ├── DoorStatusHandler.java      ← Door / access control
│   │           ├── FaultReasonHandler.java     ← Fault diagnosis
│   │           ├── AccessControlHandler.java   ← User access verification
│   │           ├── GlossaryHandler.java        ← Terminology explainer
│   │           ├── CapabilityReplyHandler.java ← "What can you do?" replies
│   │           └── AnswerHandler.java          ← Generic answer fallback
│   ├── entity/                        ← JPA entities
│   │   ├── DeviceEvent               ← TimescaleDB device_events row
│   │   ├── HierarchyNode             ← hierarchy_nodes row
│   │   ├── BranchAncestorPath        ← branch_ancestor_paths row
│   │   └── Customer                  ← customers table row
│   ├── model/
│   │   ├── domain/                   ← BranchSnapshot, PowerStatus, CctvStatus, ...
│   │   └── dto/                      ← TbEventPayload, ChatRequest, ChatResponse, ...
│   ├── exception/                    ← UnprovisionedCustomerException, ...
│   ├── tools/
│   │   ├── ThingsBoardBackupUtility.java  ← CLI: fetch devices + telemetry → JSON
│   │   └── ThingsBoardTimescaleImporter.java ← CLI: JSON → TimescaleDB + schema setup
│   └── util/                         ← JwtUtil, HmacUtil, StatusDelta, ThingsBoardHostValidator
│
├── src/main/resources/
│   ├── application.properties         ← Base config (no secrets, committed)
│   ├── application-dev.properties     ← Local secrets (gitignored)
│   └── db/timescale/timescale_setup.sql ← Hypertable + retention + indexes DDL
│
├── src/test/                          ← 157 unit + integration tests
├── docs/
│   ├── deployment-topology.md         ← Multi-container prod layout
│   └── data-refresh.md                ← Wipe-and-rebuild runbook
├── Dockerfile                         ← 3-stage build (node → maven → jre)
├── docker-compose.yml                 ← chatbot + caddy services
├── Caddyfile                          ← SSE flush, TLS, security headers
├── pom.xml                            ← Maven build + exec plugin (tb-backup, tb-import)
└── .env / .env.example                ← Runtime secrets (gitignored)
```

---

## 6. Deployment — How It Works

### 6a. Docker Build (3-Stage)

```dockerfile
# Stage 1: Build React frontend (node:20-slim)
FROM node:20-slim AS frontend-builder
  → npm install + npm run build
  → Output: src/main/resources/static/

# Stage 2: Build Spring Boot backend (maven:3.9-eclipse-temurin-21)
FROM maven AS backend-builder
  → mvn clean package -DskipTests
  → Embeds frontend static assets from Stage 1

# Stage 3: Minimal JRE runtime (eclipse-temurin:21-jre-jammy)
FROM eclipse-temurin:21-jre-jammy
  → Copies ThingsBoard-Bot-0.0.1-SNAPSHOT.jar
  → EXPOSE 8083
  → ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 6b. Docker Compose Stack

```yaml
services:
  chatbot:                         # Spring Boot app (internal port 8083)
    build: .
    env_file: .env
    restart: always

  caddy:                           # Reverse proxy (exposed 80 + 443)
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile               # explicit SSE + security config
      - caddy_data                # persistent Let's Encrypt certs
```

### 6c. Caddy Reverse Proxy

Caddy handles two important jobs:
1. **SSE streaming**: `/api/v1/chat/ask/stream` uses `flush_interval -1` + 5-minute read/write timeouts — no buffering
2. **TLS**: Auto-provisions Let's Encrypt certs for `3.7.240.120.nip.io`

Production server: `ubuntu@3.7.240.120` (AWS EC2)

---

## 7. Event Ingestion Pipeline (Real-Time Data Flow)

```
ThingsBoard IoT Platform
        │
        │  HTTP POST (HMAC-SHA256 signed)
        ▼
WebhookController  (/api/v1/webhook)
  • Verifies HMAC signature (if enabled)
  • Validates X-TB-Timestamp replay window (±5 min)
  • Parses raw payload via EventParseService
        │
        │  Publishes TbEventPayload (JSON)
        ▼
RabbitMQ → Exchange: iot.exchange → Queue: iot.events
  • Dead-letter: iot.dlx → iot.events.dlq
        │
        │  @RabbitListener("iot.events")
        ▼
EventConsumerService.consume(TbEventPayload)
  1. Null check → drop unparseable events
  2. IdempotencyService.exists(messageId) → skip if already done
  3. EventWriteService.writeToDatabase(event) → TimescaleDB
     • Catches DataIntegrityViolationException (duplicate tb_message_id)
  4. updateRedisCache(event):
     a. RedisCacheService.updateDeviceState()
     b. RedisCacheService.setDeviceMeta()
     c. AncestorPathCache.getAncestors() → DB fallback → cache
     d. LuaScriptService.executeUpdateCounters() → atomic Lua script
  5. IdempotencyService.mark(messageId) → stamped AFTER success
  → Exceptions propagate → RabbitMQ retries → DLQ after exhaustion
```

**Key guarantees:**
- **Exactly-once**: `tb_message_id` has a `UNIQUE` DB constraint
- **No silent drops**: exceptions go to DLQ, not `/dev/null`
- **Crash-safe**: idempotency key is set AFTER DB+Redis success

---

## 8. Chat Query Flow (Ask → Answer)

```
User types question in React UI
        │
        │  POST /api/v1/chat/ask/stream  (SSE, streaming)
        │  Headers: X-TB-Token: <JWT>, X-TB-Host: <TB host>
        ▼
ChatController
  • Extracts JWT → JwtUtil.extractCustomerId()
  • Validates X-TB-Host via ThingsBoardHostValidator (SSRF guard)
  • Maps customerId → internal prefix via CustomerMatcher
        │
        ▼
QueryIntentResolver  (38 KB — the brain)
  • Classifies intent: CCTV | POWER | ALERT | FLEET | HIERARCHY | etc.
  • Extracts branch name using fuzzy Jaro-Winkler matching
  • Detects ambiguity (missing branch) → requests clarification
  • Applies pending intent from ChatMemoryService for follow-ups
        │
        ▼
QueryRouterService → routes to specific Handler (18 handlers)
        │
        ▼
Handler (e.g. CctvHandler, PowerHandler, FleetAnalyticsHandler)
  • Reads live state from Redis via RedisCacheService
  • Reads historical/hierarchy from TimescaleDB if needed
  • Builds BranchSnapshot (domain model)
        │
        ▼
DeterministicAnswerService
  • Injects verified "Truth" (device counts, statuses) into prompt
  • Wraps user question in <<<USER_QUESTION>>> delimiters (prompt injection guard)
        │
        ▼
ChatService → OpenAIClient → GPT-4o (streaming)
  • Streams tokens back via SSE to React UI
  • ResponseEvaluationService optionally evaluates quality
        │
        ▼
React UI renders MessageBubble + suggestion chips
```

---

## 9. Data Layer Deep Dive

### Redis (Upstash)
- **Key pattern**: `customer:branch:deviceId` → Hash of field→value pairs
- **What's stored**: Live telemetry snapshot, device meta, lastUpdatedAt
- **Replay lock**: `SET NX` distributed lock per customer prevents concurrent replays
- **Idempotency**: `SET NX` per `tb_message_id` with TTL
- **Atomic counters**: Lua scripts via `LuaScriptService` for fleet-level counts

### TimescaleDB
- **Table**: `device_events` — hypertable partitioned by day
- **Retention**: 180-day policy
- **Indexes**: `(customer_id, event_time)`, `(customer_id, branch, event_time)`, `(tb_message_id)` UNIQUE
- **JSONB columns**: Raw telemetry stored as JSONB; `DISTINCT ON` queries for latest state
- **Other tables**: `hierarchy_nodes`, `branch_ancestor_paths`, `customers`

### JVM In-Memory Cache (AncestorPathCache)
- Caches hierarchy path lookups with 5-minute TTL
- Eliminates repeated DB round-trips for ancestor resolution

---

## 10. CLI Tools (Data Management)

Two standalone tools run via Maven `exec:java`:

### `ThingsBoardBackupUtility` (tb-backup)
```bash
./mvnw -q exec:java@tb-backup
```
- Fetches all 143 devices + current telemetry from ThingsBoard REST API
- Saves to `Thingsboard-Data/thingsboard_devices_backup.json` (gitignored)

### `ThingsBoardTimescaleImporter` (tb-import)
```bash
./mvnw -q exec:java@tb-import
```
- Reads the backup JSON
- Wipes and reimports `device_events`, `hierarchy_nodes`, `branch_ancestor_paths`
- Sets up schema if needed

### Replay API (Redis rebuild)
```bash
curl -X POST "http://localhost:8083/api/v1/admin/replay?customerId=ALL&startTime=2020-01-01T00:00:00Z&endTime=2035-01-01T00:00:00Z"
```
- Rebuilds Redis from TimescaleDB historical data
- Acquires per-customer distributed lock → HTTP 409 if concurrent

---

## 11. Security Model

### Tenant Isolation
- JWT `customerId` → `customers` table → internal prefix (`BOI`, `SEPL`, etc.)
- `IOTCHATBOT_SECURITY_STRICT_CUSTOMER_MAPPING=true` → HTTP 403 on unknown tenant
- Dev mode: falls back to `BOI` for local testing

### JWT Verification
- `IOTCHATBOT_SECURITY_REQUIRE_JWT_VERIFICATION=true` → validates signature at startup
- Key: `IOTCHATBOT_JWT_SIGNING_KEY` (base64)
- Refuses to boot if flag=true but key is blank

### SSRF Protection
- `X-TB-Host` header validated by `ThingsBoardHostValidator`
- Proper URI-based host extraction (not substring matching)
- Exact + subdomain-suffix matching against `IOTCHATBOT_SECURITY_ALLOWED_THINGSBOARD_HOSTS`

### Webhook Security
- HMAC-SHA256 signature verification
- `X-TB-Timestamp` replay window (default ±5 min)

### Frontend Security
- `ChatContext.tsx` validates `event.origin` allowlist before accepting JWT via postMessage
- JWT held in React state only — never `localStorage` or URL params

### CSP + Admin
- `ContentSecurityPolicyFilter` adds `frame-ancestors` header
- Admin endpoints gated by bearer token (`AdminAuthFilter`)
- Actuator exposes only `health` and `info`

---

## 12. Frontend Architecture (React + Vite)

### Embedding in ThingsBoard
ThingsBoard sends JWT via `postMessage` to the iframe:
```js
iframe.contentWindow.postMessage(
  { type: 'TB_AUTH_DATA', payload: { token: jwt, host: window.location.origin } },
  'https://your-bot-host'
);
// Sent at 0ms, 400ms, 1200ms, 2500ms to handle React listener race
```

### Key Components
| Component | Purpose |
|---|---|
| `ChatContext.tsx` | Global state — JWT, messages, SSE stream management |
| `ChatToggle` | Floating button + window container |
| `ChatWindow` | Main chat UI |
| `MessageBubble` | Renders user + bot messages (with chips) |
| `ChatInput` | Text input + send |
| `WelcomeMessage` | Initial state + quick actions |

### SSE Streaming
- Spring Boot streams tokens via Server-Sent Events
- Caddy proxies with `flush_interval -1` (no buffering)
- React renders tokens as they arrive

---

## 13. Query Intent Handlers (18 Domain Handlers)

| Handler | What It Answers |
|---|---|
| `CctvHandler` | CCTV camera count, offline channels, model grouping |
| `PowerHandler` | UPS status, battery voltage, mains power |
| `AlertHandler` | Active alarms, alert history |
| `FleetAnalyticsHandler` | Multi-branch comparisons, trends |
| `GlobalOverviewHandler` | Fleet-wide health summary |
| `NetworkStatusHandler` | SIM/network connectivity |
| `GatewayStatusHandler` | Gateway device health |
| `HierarchyHandler` | Branch → Zone → Region relationships |
| `DeviceInventoryHandler` | List of devices at a branch |
| `DeviceIdentityHandler` | Specific device info lookup |
| `SubsystemHandler` | Generic subsystem queries |
| `DoorStatusHandler` | Door / access control state |
| `FaultReasonHandler` | Fault diagnosis and root cause |
| `AccessControlHandler` | User access verification |
| `GlossaryHandler` | IoT term explanations |
| `CapabilityReplyHandler` | "What can SAI do?" |
| `AnswerHandler` | Generic fallback |
| `AnswerSupport` | Shared utilities for answer formatting |

---

## 14. Key Dependencies (pom.xml)

| Dependency | Version | Use |
|---|---|---|
| spring-boot-starter-webmvc | 4.0.3 | REST + SSE endpoints |
| spring-boot-starter-data-jpa | 4.0.3 | TimescaleDB ORM |
| spring-boot-starter-data-redis | 4.0.3 | Redis (Lettuce) |
| spring-boot-starter-amqp | 4.0.3 | RabbitMQ |
| spring-boot-starter-actuator | 4.0.3 | Health/metrics |
| micrometer-registry-prometheus | — | Prometheus metrics |
| micrometer-tracing-bridge-otel | — | Distributed tracing |
| jjwt-api/impl/jackson | 0.12.6 | JWT verification |
| okhttp | 4.12.0 | OpenAI + TB HTTP calls |
| jackson-databind + jsr310 | — | JSON serialization |
| commons-text | 1.14.0 | Jaro-Winkler fuzzy matching |
| postgresql | — | PostgreSQL JDBC driver |
| testcontainers | 1.20.6 | PostgreSQL integration tests |
| lombok | — | Boilerplate reduction |

---

## 15. Configuration Reference (Key Env Vars)

### Security (all default `false` — safe for local dev)
| Env Var | Purpose |
|---|---|
| `IOTCHATBOT_SECURITY_STRICT_CUSTOMER_MAPPING` | HTTP 403 on unknown tenant |
| `IOTCHATBOT_SECURITY_REQUIRE_JWT_VERIFICATION` | Validate JWT signature |
| `IOTCHATBOT_JWT_SIGNING_KEY` | ThingsBoard JWT signing key (base64) |
| `IOTCHATBOT_SECURITY_REQUIRE_WEBHOOK_HMAC` | HMAC-SHA256 on webhooks |
| `IOTCHATBOT_SECURITY_WEBHOOK_HMAC_SECRET` | HMAC secret |
| `IOTCHATBOT_SECURITY_REQUIRE_ADMIN_TOKEN` | Gate admin endpoints |
| `IOTCHATBOT_SECURITY_ALLOWED_THINGSBOARD_HOSTS` | SSRF allowlist |
| `IOTCHATBOT_SECURITY_FRAME_ANCESTORS` | CSP frame-ancestors |

### Features
| Property | Default | Purpose |
|---|---|---|
| `iotchatbot.timescale.init-enabled` | false | Auto-setup hypertable on startup |
| `iotchatbot.customers.sync-enabled` | false | Auto-sync customer UUIDs from TB |

---

## 16. Testing Strategy (157 Tests)

| Test Class | What It Covers |
|---|---|
| `QueryIntentResolverTest` | Intent routing — questions → correct handlers |
| `DeterministicAnswerServiceTest` | Truth-injection counts + CCTV channel-range grouping |
| `ChatServiceTest` | Scope verification, JWT-scoping, UnprovisionedCustomer path |
| `EventConsumerServiceTest` | Idempotency-after-success, duplicate handling |
| `TimescaleIntegrationTest` | JSONB queries + `DISTINCT ON` against real PostgreSQL (Docker-gated) |
| `ThingsBoardHostValidatorTest` | SSRF allowlist exact/subdomain matching, bypass rejection |

---

## 17. Data Refresh Runbook

Full wipe-and-rebuild:
```bash
# 1. Snapshot all devices + telemetry from ThingsBoard
./mvnw -q exec:java@tb-backup

# 2. Wipe DB + reimport
./mvnw -q exec:java@tb-import

# 3. Rebuild Redis from DB (wide window to catch all data)
curl -X POST "http://localhost:8083/api/v1/admin/replay?customerId=ALL&startTime=2020-01-01T00:00:00Z&endTime=2035-01-01T00:00:00Z"
```

---

## 18. Integration Points Summary

```
External System          How SAI Integrates
─────────────────────    ───────────────────────────────────────────────────
ThingsBoard (IoT)        • Webhook → ingestion pipeline (push model)
                         • REST API polling via ThingsBoardClient (backup tool)
                         • JWT auth (users log in via TB)

OpenAI GPT-4o            • OkHttp streaming via OpenAIClient
                         • SSE token stream → React UI

Redis (Upstash)          • Lettuce client (spring-data-redis)
                         • Device state, idempotency, replay locks, Lua scripts

RabbitMQ (CloudAMQP)     • Spring AMQP
                         • iot.exchange → iot.events queue → DLQ

TimescaleDB (Cloud)      • Spring Data JPA + PostgreSQL driver
                         • Hypertable, JSONB, DISTINCT ON queries

React Frontend           • postMessage JWT handshake
                         • SSE streaming from /api/v1/chat/ask/stream

Caddy Proxy              • TLS termination, SSE flush, security headers

AWS EC2                  • Hosts Docker containers (ubuntu@3.7.240.120)
```

## 🚀 Deploy to AWS EC2

### 1. SSH into the server
```bash
ssh -i D:\Ganesh\Office\aws-key/sai.pem ubuntu@3.7.240.120
```



### 2. Pull latest code (on the server)
```bash
git pull
```

---

### 3. Rebuild & Redeploy (Docker)
```bash
docker compose down
docker compose up -d --build
```

---

### 4. Watch live logs
```bash
docker logs chatbot-demo -f
```

---

## 🔁 Full Deploy Sequence (copy-paste)

```bash
# SSH in
ssh -i D:\Ganesh\Office\aws-key/sai.pem ubuntu@3.7.240.120

# On the server:
git pull && docker compose down && docker compose up -d --build && docker logs chatbot-demo -f
```

---

## 📋 Other useful Docker commands

```bash
# Check running containers
docker ps

# Check Caddy proxy logs
docker logs caddy-proxy -f

# Restart only the app (no rebuild)
docker compose restart chatbot

# Stop everything
docker compose down
```

> **Note:** The `docker compose up -d --build` command triggers the full 3-stage Docker build — frontend (React) → backend (Maven JAR) → runtime (JRE). It can take **3–5 minutes** the first time.

```
```
### Full application Running Command.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,chat,ingestion,consumer" "-Dspring-boot.run.arguments=--server.port=8083"
```
### Or if you only want the chat role (no RabbitMQ/broker needed):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,chat" "-Dspring-boot.run.arguments=--server.port=8083"
```