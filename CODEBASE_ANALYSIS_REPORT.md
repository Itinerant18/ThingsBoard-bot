# Codebase Analysis Report

> ThingsBoard-Bot (SAI — Smart Assistant for IoT)
> Spring Boot 4.0.3 · Java 21 · React 18 + Vite 5 + Tailwind 4
> A hybrid "Deterministic-first + LLM-fallback" chatbot wired into a multi-tenant ThingsBoard IoT platform.

---

## 1. Project Summary

`ThingsBoard-Bot` is a white-label, multi-tenant conversational assistant for a ThingsBoard-driven IoT deployment that monitors 10 Indian bank branch networks (ATMs, kiosks, branches). The bot is embedded as a transparent iframe inside the ThingsBoard dashboard and answers natural-language questions about branch health (power, gateway, CCTV, alerts) and global overviews (totals, alert counts, branch health).

Key design points:

- **Deterministic-first, LLM-fallback.** ~80% of questions are answered by a rule-based query engine reading from Redis. The remaining ~20% (open-ended, summarization, free-form) fall through to OpenAI `gpt-4o-mini` with a curated SYSTEM prompt and structured context.
- **Event-driven ingestion path.** ThingsBoard webhooks land in `WebhookController` (ingestion profile), get fanned out per-customer via `RabbitMQQueueService`, are consumed by a separate JVM (consumer profile) running `EventConsumerService`, and finally state is projected to **Redis (Upstash)** as the source of truth for the chat path.
- **Multi-tenant by customer prefix.** Each device name encodes its tenant (e.g., `SYNB…`, `UBIN…`); routing is purely string-prefix based via `CustomerConfig` and `EventParseService`.
- **Triple-deployable profile architecture.** One codebase, three runtime roles, selected by Spring profile: `chat` (HTTP chatbot node), `ingestion` (webhook receiver), `consumer` (background AMQP worker; `spring.main.web-application-type=none`).
- **Admin/operational tools.** Standalone CLI utilities (`ThingsBoardBackupUtility`, `ThingsBoardTimescaleImporter`) and admin endpoints (`HierarchyAdminController`, `ReplayController`) for hierarchy CSV import, replay, and reconciliation.

The product vision, including the v4.0 production split, is documented in `BOT_PROPOSAL_DOCUMENT.md`.

---

## 2. Tech Stack

### Backend
- **Language / JVM:** Java 21
- **Framework:** Spring Boot 4.0.3 (Web MVC, Data JPA, Data Redis, AMQP, Cache, Scheduling, RestTemplate, Mail)
- **Persistence:** PostgreSQL / TimescaleDB (dev profile points to a remote Timescale instance); H2 in `MODE=PostgreSQL` for tests
- **Cache / Counters:** Redis (Lettuce) — Upstash in dev; Lua script for atomic counter updates
- **Messaging:** RabbitMQ via CloudAMQP
- **LLM Client:** Native OpenAI Chat Completions API (`gpt-4o-mini`) — no Spring AI; uses `RestTemplate`
- **JSON:** Jackson with custom `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`
- **Build:** Maven (`spring-boot-maven-plugin`, `frontend-maven-plugin` for React build)
- **Lombok** everywhere; **JJWT** is not present (token parsing done via custom `JwtParserUtil` using `io.jsonwebtoken`-style logic but in-house)

### Frontend
- **Framework:** React 18 + TypeScript
- **Bundler:** Vite 5
- **Styling:** Tailwind CSS v4 (CSS-first config in `globals.css`), glassmorphism theme tokens
- **State:** `ChatContext` (React Context API, no Redux/Zustand)
- **Special features:** Web Speech API (voice input), `postMessage` cross-frame auth (reads `TB_AUTH_TOKEN` + `TB_HOST` from parent)

### Infra
- **Docker:** 3-stage build (Node 20 → Maven 3.9/temurin-21 → temurin:21-jre-jammy), port 8083
- **Compose:** chatbot service + Caddy reverse proxy on `3.7.240.120.nip.io`
- **External ThingsBoard:** `https://seple.iot-private.cloud`
- **Default profile:** `dev` (everything in one JVM)

### Tests
- JUnit 5 + Mockito
- Test profile: H2, Redis/Rabbit disabled (`spring.autoconfigure.exclude=…RedisAutoConfiguration, RabbitAutoConfiguration`)
- Golden-question accuracy test (`integration/GoldenQuestionAccuracyTest`) drives the deterministic answer engine against `fixtures/full_data_fixture.json`

---

## 3. Architecture Overview

The codebase is built around a **three-profile deployment** (chat / ingestion / consumer) sharing a single Spring Boot app. Each profile is enabled via `application-{profile}.properties`:

| Profile | `spring.main.web-application-type` | Role | Entry point |
|---|---|---|---|
| `chat` | servlet | HTTP chatbot node | `ChatController` + `DataController` + `HierarchyAdminController` + `ReplayController` |
| `ingestion` | servlet | Webhook receiver | `WebhookController` + `DataController` |
| `consumer` | `none` | Background AMQP worker | `EventConsumerService` + `ReconciliationService` |
| `dev` | servlet | All-in-one (default) | All controllers + consumer |
| `test` | servlet | Test slice (H2) | App context loads with no Redis/Rabbit |

**Component-level flow:**

```
                  ┌────────────────────────────────────────────────┐
                  │  ThingsBoard Dashboard (iframe parent)         │
                  │  postMessage → TB_AUTH_TOKEN, TB_HOST          │
                  └────────────────────┬───────────────────────────┘
                                       │
                                       ▼
   ┌───────────────────────  CHAT PROFILE  ───────────────────────────┐
   │  static/index.html (transparent, pointer-events:none)            │
   │   └── ChatContext ── fetch POST /api/v1/chat/ask                 │
   │                       X-TB-Token, X-TB-Host                      │
   │                          │                                       │
   │                          ▼                                       │
   │              ChatController.askAsk()                             │
   │                  │                                               │
   │                  ▼                                               │
   │              ChatService.ask()                                   │
   │                  │   ┌── short-circuits to:                      │
   │                  │   │   • small-talk regexes                    │
   │                  │   │   • branch inventory (RedisQueryService)  │
   │                  │   │   • global counters (RedisQueryService)   │
   │                  │   │   • single-branch attribute               │
   │                  │   │   • structured alerts (DeterministicAns..)│
   │                  │   │   • command "show me" (UI list)           │
   │                  │   ▼                                           │
   │                  │   QueryRouterService                          │
   │                  │      SIMPLE_REDIS ──► RedisQueryService       │
   │                  │      COMPLEX_LLM ──► DeterministicAnswer + ?  │
   │                  │                 fallback OpenAIClient (gpt-4o)│
   │                  ▼                                               │
   │              ChatResponse (markdown) + AnswerMetadata            │
   │   ┌──────────────────────────────────────────────────────────┐   │
   │   │ Side reads:                                              │   │
   │   │   • DataService      ── GET /api/v1/data/full (5m cache) │   │
   │   │   • UserDataService  ── per-user branch index,           │   │
   │   │                          UserAwareThingsBoardClient      │   │
   │   │                          ThingsBoardRequestFilter        │   │
   │   └──────────────────────────────────────────────────────────┘   │
   └──────────────────────────────────────────────────────────────────┘

   ┌───────────────────────  INGESTION PROFILE  ──────────────────────┐
   │  WebhookController  POST /api/v1/webhook/tb                      │
   │      (NO HMAC verification — see Risks)                          │
   │          │                                                       │
   │          ▼                                                       │
   │      RabbitMQQueueService.publish("iot.events", evt)             │
   │      declares per-customer queues + bindings (topic exchange)    │
   └──────────────────────────────────────────────────────────────────┘

   ┌───────────────────────  CONSUMER PROFILE  ───────────────────────┐
   │  @RabbitListener  iot.events.q.{customer}                        │
   │      │                                                           │
   │      ▼                                                           │
   │  EventConsumerService.handle()                                   │
   │      │                                                           │
   │      ├── IdempotencyService      (Redis SETNX, 24h TTL)          │
   │      ├── EventParseService       (extracts customer, branch)     │
   │      ├── EventWriteService       (persist DeviceEvent)           │
   │      ├── RedisCacheService       (HSET deviceState, hash)        │
   │      ├── LuaScriptService        (atomic counter deltas)         │
   │      │       └── scripts/update_counters.lua                     │
   │      ├── AncestorPathService     (precomputed HO→branch path)    │
   │      └── ReconciliationService   (nightly @Scheduled cron)       │
   │                                                                  │
   │  ReplayService    (admin trigger)  ── replays device_events      │
   └──────────────────────────────────────────────────────────────────┘


```

<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg style="fill:none;stroke:none;fill-rule:evenodd;clip-rule:evenodd;stroke-linecap:round;stroke-linejoin:round;stroke-miterlimit:1.5;" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="2460" height="1172" viewBox="0 0 2460 1172"><style class="text-font-style fontImports" data-font-family="Roboto">@import url(https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&amp;display=block);</style><g id="items" style="isolation: isolate"><g id="blend" style="mix-blend-mode: normal"><g id="g-root-cp_1_lines-1_dgouerlqbl6v-fill" data-item-order="-2594052" transform="translate(1953.7421875, 128)"><g id="cp_1_lines-1_dgouerlqbl6v-fill-merged" stroke="none" fill="#3cc583" fill-opacity="0.2"><g><path d="M10 125.008C10 126.839 11.299 128.443 13.249 128.86C21.877 130.708 46.65 134.767 74.883 128.09C100.574 122.015 123.401 124.829 133.804 126.777C136.801 127.338 139.766 125.264 139.766 122.468 V14C139.766 11.791 137.975 10 135.766 10 H14C11.791 10 10 11.791 10 14 V125.008"></path></g></g></g><g id="g-root-cp_1_lines-1_dgouerlqbl6v-stroke" data-item-order="-2594052" transform="translate(1953.7421875, 128)"><g id="cp_1_lines-1_dgouerlqbl6v-stroke" fill="none" stroke-linecap="round" stroke-linejoin="round" stroke-miterlimit="4" stroke="#3cc583" stroke-width="2" stroke-opacity="1"><g><path d="M 16 10L 14 10C 11.7909 10 10 11.7909 10 14L 10 16M 10 16L 10 121.78125M 139.765625 16L 139.765625 121.78125M 16 10L 133.765625 10M 133.765625 10L 135.765625 10C 137.974725 10 139.765625 11.7909 139.765625 14L 139.765625 16M 139.765625 121.78125L 139.765625 122.467751C 139.765625 125.26445 136.800972 127.338455 133.804176 126.777145C 123.4011 124.82885 100.574448 122.014847 74.882813 128.090347C 46.650333 134.766647 21.877389 130.708252 13.248565 128.860245C 11.298639 128.44265 10 126.839447 10 125.008453L 10 121.78125"></path></g></g></g></g></g></svg>


The chat path treats **Redis as the live source of truth**; TimescaleDB is the durable event store. On a Redis cold start, `ReconciliationService` and `ReplayService` rebuild the hashes from the event log.

---

## 4. Module / Component Breakdown

Top-level packages under `com.seple.ThingsBoard_Bot`:

| Package | Role | Key Classes |
|---|---|---|
| `config` | Spring `@Configuration` beans, profiles, customer registry, Redis/AMQP templates, JPA, Jackson, CORS, OpenAI | `CustomerConfig`, `DatabaseConfig`, `DatabaseInitializer`, `RedisConfig`, `RabbitMQConfig`, `OpenAIConfig`, `CacheConfig`, `ChatbotConfig`, `JacksonConfig`, `CorsConfig`, `ThingsBoardConfig` |
| `controller` | HTTP entry points (4 profile-gated + 1 unprofiled + 2 admin) | `ChatController`, `WebhookController`, `DataController`, `HierarchyAdminController`, `ReplayController` |
| `client` | Outbound HTTP wrappers for ThingsBoard and OpenAI | `ThingsBoardClient` (system JWT), `UserAwareThingsBoardClient` (per-user JWT, ThreadLocal), `OpenAIClient` |
| `entity` | JPA `DeviceEvent`, `HierarchyNode`, `Customer`, `BranchAncestorPath` |
| `repository` | Spring Data JPA interfaces |
| `model.domain` | Normalized branch domain (`BranchSnapshot`, `BranchIdentity`, `GatewayStatus`, `PowerStatus`, `CctvStatus`, `BranchSubsystems`, `SubsystemStatus`, `AlertSummary`, `HardwareHealth`, `NormalizedState`) |
| `model.dto` | API request/response DTOs (`ChatRequest`, `ChatResponse`, `ChatMessage`, `TbEventPayload`, `ChartData`, `DeviceIndexEntry`, `GlobalOverviewCounters`, `AnswerMetadata`) |
| `service` | Core orchestration: chat, data, event consumer, replay, reconciliation, idempotency, Redis cache, Lua, RabbitMQ | `ChatService`, `DataService`, `UserDataService`, `EventConsumerService`, `EventParseService`, `EventWriteService`, `IdempotencyService`, `RedisCacheService`, `LuaScriptService`, `RabbitMQQueueService`, `ReplayService`, `ReconciliationService`, `AncestorPathService`, `AncestorPathCache`, `ChartService`, `ChatMemoryService` |
| `service.index` | Per-user branch index + global aggregator reads | `BranchIndexService`, `GlobalAggregatorService` |
| `service.normalization` | `FullDataPayloadParser` → `ValueNormalizer` + `FieldPrecedenceResolver` → `BranchSnapshotMapper` (with `BranchAliasIndex`) |
| `service.query` | The "deterministic brain": intent resolution, routing, key registry, Redis query, deterministic rendering | `QueryRouterService`, `QueryIntentResolver`, `QueryIntent` (enum), `IntentKeyProfileRegistry`, `NodeNameResolver`, `RedisQueryService`, `DeterministicAnswerService`, `AnswerTemplateService`, `ResolvedQuery` |
| `util` | Filters, token parsing, context builders | `ThingsBoardRequestFilter` (servlet filter), `ThingsBoardRequestContext` (ThreadLocal), `JwtParserUtil`, `TokenCounterService`, `StructuredContextBuilder`, deprecated `ContextFilterUtil` |
| `utility` | Standalone CLI tools (intentionally separate from `util`) | `ThingsBoardBackupUtility`, `ThingsBoardTimescaleImporter` |
| `exception` | Domain exceptions + `@RestControllerAdvice` | `ThingsBoardException`, `OpenAIException`, `ContextOverflowException`, `GlobalExceptionHandler` |

---

## 5. File-by-File Reference

> Paths are relative to repo root. Excludes `target/`, `node_modules/`, `.git/`, `.codex/`, `temp_extracted/`, `new_architecture/`.

### Root & Configuration
| Path | Purpose | Notes |
|---|---|---|
| `pom.xml` | Maven build; `spring-boot-starter-parent` 4.0.3, Java 21, frontend-maven-plugin | Single module |
| `Dockerfile` | 3-stage: `node:20` → `maven:3.9-temurin-21` → `eclipse-temurin:21-jre-jammy` | Exposes 8083; copies `target/classes/static` |
| `docker-compose.yml` | chatbot service + Caddy reverse proxy; serves `3.7.240.120.nip.io` | One service in `docker-compose.yml` |
| `README.md` | High-level product description for SAI | |
| `DEVELOPMENT.md` | Project layout and dev commands | |
| `BOT_PROPOSAL_DOCUMENT.md` | Roadmap to v4.0 production architecture | |
| `DAILY_TASKS.md` | Progress log; TimescaleDB setup blocked on Windows/WSL | |
| `AGENTS.md` | Use `graphify` skill instructions (out of scope) | |

### `src/main/resources`
| Path | Purpose | Notes |
|---|---|---|
| `application.properties` | Shared defaults | H2 datasource, `simple` cache type, AMQP listener autostart |
| `application-dev.properties` | Real credentials (TB, Timescale, Upstash, CloudAMQP, OpenAI key) | **Secrets in repo — see Risks** |
| `application-chat.properties` | Chat node profile | `spring.profiles.active=chat,dev` |
| `application-ingestion.properties` | Ingestion node profile | `spring.profiles.active=ingestion,dev` |
| `application-consumer.properties` | Consumer node profile | `spring.profiles.active=consumer,dev`, `web-application-type=none` |
| `scripts/update_counters.lua` | Atomic Lua: takes a node key + previous/new status, decrements old bucket, increments new bucket | Used by `LuaScriptService.updateCounters` |
| `static/index.html` | Built React shell | Transparent body, `pointer-events:none` for iframe embedding |
| `static/index.css` | Tailwind v4 compiled output | |
| `static/index.js` | Minified React bundle | |

### `src/main/java/com/seple/ThingsBoard_Bot`
| Path | Purpose | Key contents |
|---|---|---|
| `ThingsBoardBotApplication.java` | `@SpringBootApplication` + `@EnableCaching` + `@EnableScheduling` | Entry point |
| `config/CacheConfig.java` | Redis-backed `CacheManager` bean | |
| `config/ChatbotConfig.java` | `gpt-4o-mini`, prompt version, fallback thresholds | |
| `config/CorsConfig.java` | `WebMvcConfigurer` allowing `*` origin | **Permissive CORS — see Risks** |
| `config/CustomerConfig.java` | Hard-coded list of 10 bank tenant prefixes (`SYNB`, `UBIN`, `IOB`, `CBI`, …) + display names | Multi-tenant routing |
| `config/DatabaseConfig.java` | JPA repos package, entity scan, HikariCP | |
| `config/DatabaseInitializer.java` | Seeds `customer`, `hierarchy_nodes`, `branch_ancestor_paths` on startup | |
| `config/JacksonConfig.java` | `JavaTimeModule`, ISO-8601 dates, ignore unknowns | |
| `config/OpenAIConfig.java` | `RestTemplate` bean tuned for OpenAI (timeouts, headers) | |
| `config/RabbitMQConfig.java` | Topic exchange `iot.events` + per-customer queue declarations + DLQ | |
| `config/RedisConfig.java` | `LettuceConnectionFactory`, `StringRedisTemplate`, `RedisTemplate<String,Object>` | |
| `config/ThingsBoardConfig.java` | Base URL, system credentials, JWT cache | |
| `client/ThingsBoardClient.java` | System JWT; everything TB: telemetry, attributes, alarms, hierarchy, device list | |
| `client/UserAwareThingsBoardClient.java` | Per-user JWT; uses `ThingsBoardRequestContext` ThreadLocal | |
| `client/OpenAIClient.java` | Wraps `/v1/chat/completions`, parses response, throws `OpenAIException` on rate-limit | |
| `controller/ChatController.java` | Profile `chat`; `POST /api/v1/chat/ask`; reads `X-TB-Token` and `X-TB-Host` | |
| `controller/DataController.java` | Unprofiled; `GET /api/v1/data/full`, `/devices`, `/devices/{id}` | |
| `controller/HierarchyAdminController.java` | CSV import of hierarchy (region/zone/branch) | |
| `controller/ReplayController.java` | Admin replay endpoint | |
| `controller/WebhookController.java` | Profile `ingestion`; `POST /api/v1/webhook/tb`; no HMAC verify | **Security risk** |
| `entity/Customer.java` | JPA: id, code, name, branchPrefixes | |
| `entity/DeviceEvent.java` | JPA: id, eventId (UUID), deviceId, customerCode, branchName, eventType, payload (JSONB), receivedAt | |
| `entity/HierarchyNode.java` | JPA: id, customerCode, level (REGION/ZONE/BRANCH/HO), name, parentId | |
| `entity/BranchAncestorPath.java` | JPA: id, branchNodeId, ancestorPath (Postgres text[]), depth | **Array type — see Risks** |
| `repository/*` | Spring Data JPA: `findByEventId`, `findByCustomerCodeAndBranchName`, `findByBranchNodeId`, etc. | |
| `model/dto/*` | API DTOs — see Tech Stack | |
| `model/domain/*` | `BranchSnapshot` composes `BranchIdentity + GatewayStatus + PowerStatus + CctvStatus + BranchSubsystems + AlertSummary + HardwareHealth` + `NormalizedState` enum | Pure value types |
| `service/ChatService.java` | The orchestrator. Pre-checks (small-talk, list-branches, attribute-ask, alerts, command). Routes to `QueryRouterService` or `OpenAIClient`. Injects `ChatMemoryService`. | ~25 KB; large `SYSTEM_PROMPT` constant |
| `service/DataService.java` | Wraps `GET /api/v1/data/full`; 5-min in-memory `ConcurrentHashMap` cache; background refresh task | **In-memory cache + `indexByUser` map (no eviction) — see Risks** |
| `service/UserDataService.java` | Per-user `BranchIndexService` + per-user telemetry; `@Scheduled` refresh; reads JWT from `ThingsBoardRequestContext` | |
| `service/EventConsumerService.java` | Profile `consumer`; `@RabbitListener`; orchestrates idempotency, parse, write, cache, Lua, ancestor | |
| `service/EventParseService.java` | Splits `deviceName` by tenant prefix; produces `customerCode`, `branchName` | |
| `service/EventWriteService.java` | `DeviceEventRepository.save(...)` | |
| `service/IdempotencyService.java` | `redisTemplate.opsForValue().setIfAbsent(key, "1", 24h)` | |
| `service/RedisCacheService.java` | `HSET deviceState:{deviceId}` + `HSET nodeCounters:{nodePath}`; `updateCountersAtomically` via `LuaScriptService` | |
| `service/LuaScriptService.java` | Loads `classpath:scripts/update_counters.lua` once; `DefaultRedisScript<Long>` | |
| `service/RabbitMQQueueService.java` | Declares per-customer queues + bindings (idempotent on startup) | |
| `service/ReplayService.java` | Chronologically re-emits a window of `device_events` into Redis | Used after Redis flush |
| `service/ReconciliationService.java` | `@Scheduled(cron="0 0 2 * * ?", zone="Asia/Kolkata")`; verifies Redis matches last 24h of events | |
| `service/AncestorPathService.java` | Builds/reads ancestor path per customer (DB) | |
| `service/AncestorPathCache.java` | Redis cache for `branch → ancestor path string` | |
| `service/ChartService.java` | 24h history of telemetry keys for chart rendering | |
| `service/ChatMemoryService.java` | In-memory sliding window of last N turns per session id | |
| `service/index/BranchIndexService.java` | Alias→device map per user; `@Scheduled` refresh | |
| `service/index/GlobalAggregatorService.java` | Reads aggregate attributes from a single aggregator device | |
| `service/normalization/FullDataPayloadParser.java` | Splits `branchName.field` flat key/value map into per-branch maps | |
| `service/normalization/BranchSnapshotMapper.java` | Raw map → `BranchSnapshot`; calls `FieldPrecedenceResolver` + `ValueNormalizer` | |
| `service/normalization/FieldPrecedenceResolver.java` | e.g., `battery_status_battery_voltage` beats `gatewayStatus_battery_voltage` | |
| `service/normalization/ValueNormalizer.java` | Coerces state/boolean/double; treats `null1`/`null11`/`not_found` as unknown | |
| `service/normalization/BranchAliasIndex.java` | Alias variants (e.g., `AHD_Vastrapur` ↔ `vastrapur`, abbreviations) | |
| `service/query/QueryRouterService.java` | Hard-coded rules classify `SIMPLE_REDIS` vs `COMPLEX_LLM`; falls through to LLM otherwise | |
| `service/query/QueryIntentResolver.java` | Resolves `QueryIntent` enum value + branch name + scope (single/global) | |
| `service/query/QueryIntent.java` | Enum: 25+ intents (`LIST_BRANCHES`, `GLOBAL_ALERT_COUNT`, `BRANCH_POWER_STATUS`, …) | |
| `service/query/IntentKeyProfileRegistry.java` | Intent → list of Redis keys to read | |
| `service/query/NodeNameResolver.java` | Fuzzy match region/zone/branch name with aliases | |
| `service/query/RedisQueryService.java` | Thin wrapper around `RedisCacheService` for the engine's reads | |
| `service/query/DeterministicAnswerService.java` | Renders markdown answer from `ResolvedQuery` + `AnswerTemplateService` | |
| `service/query/AnswerTemplateService.java` | Markdown templates per intent | |
| `service/query/ResolvedQuery.java` | Immutable result of intent resolution | |
| `util/JwtParserUtil.java` | In-house JWT parser (no library) | |
| `util/TokenCounterService.java` | Estimates OpenAI tokens for budgeting | |
| `util/StructuredContextBuilder.java` | Builds the structured "context block" prepended to LLM prompts | |
| `util/ThingsBoardRequestContext.java` | ThreadLocal holder of (userToken, userHost) | |
| `util/ThingsBoardRequestFilter.java` | Servlet filter that populates context, then clears it | |
| `util/ContextFilterUtil.java` | **Deprecated** legacy utility — still in tree | Should be removed |
| `utility/ThingsBoardBackupUtility.java` | Standalone CLI; hard-coded TB creds | One-shot backup script |
| `utility/ThingsBoardTimescaleImporter.java` | Standalone CLI; reads `application-dev.properties` | Imports telemetry dump |
| `exception/*` | Domain exceptions + `@RestControllerAdvice` | |

### Frontend (`frontend/`)
| Path | Purpose |
|---|---|
| `package.json` | React 18, Vite 5, Tailwind v4 |
| `src/main.tsx` | ReactDOM mount |
| `src/App.tsx` | Wraps `ChatProvider` → `ChatToggle` + `ChatWindow` |
| `src/types/index.ts` | `ChatMessage`, `Branch`, etc. |
| `src/context/ChatContext.tsx` | State + `postMessage` listener; auto-uses `TB_AUTH_TOKEN`/`TB_HOST` |
| `src/components/ChatToggle.tsx` | Floating launcher (transparent, iframe-friendly) |
| `src/components/ChatWindow.tsx` | Modal-like chat panel; renders `MessageBubble`s |
| `src/components/MessageBubble.tsx` | Parses `<details>`/`<summary>` in markdown |
| `src/components/ChatInput.tsx` | Web Speech API mic button + text input |
| `src/components/TypingIndicator.tsx` | Animated dots |
| `src/components/WelcomeMessage.tsx` | Static intro |
| `src/components/BotLogoSvg.tsx` | Inline SVG |
| `src/styles/globals.css` | Tailwind v4 + glassmorphism tokens |

### Tests (`src/test/java/...`)
| Path | Purpose |
|---|---|
| `ThingsBoardBotApplicationTests.java` | Context-load smoke test |
| `controller/ChatControllerTest.java` | MockMvc round-trip |
| `service/ChatServiceTest.java` | Mocks for the orchestrator |
| `service/AncestorPathServiceTest.java` | Path resolution logic |
| `service/ReplayServiceTest.java` | Windowed replay |
| `service/ReconciliationServiceTest.java` | Drift detection |
| `service/normalization/*Test.java` | Three files; raw → snapshot pipeline |
| `service/query/QueryIntentResolverTest.java` | Intent extraction |
| `service/query/NodeNameResolverTest.java` | Alias resolution |
| `service/query/QueryRouterServiceTest.java` | SIMPLE_REDIS vs COMPLEX_LLM |
| `service/query/DeterministicAnswerServiceTest.java` | Markdown rendering |
| `integration/GoldenQuestionAccuracyTest.java` | End-to-end intent+answer accuracy vs fixture |
| `support/FixtureLoader.java` | Reads `fixtures/full_data_fixture.json` |
| `utility/ThingsBoardBackupUtilityTest.java` | CLI test |
| `src/test/resources/application-test.properties` | H2 in PostgreSQL mode; excludes Redis/Rabbit auto-config |

---

## 6. Data Flow

### 6.1 Happy Path — User asks "How is Mumbai branch?"

```
1. Browser iframe (ChatContext)
   └─ postMessage listener supplies TB_AUTH_TOKEN, TB_HOST (or X-TB-* header fallback)
2. POST /api/v1/chat/ask
   Headers: X-TB-Token, X-TB-Host, X-TB-Session-Id
   Body:    { "message": "How is Mumbai branch?" }
3. ChatController.askAsk()
   - extracts token + host + sessionId
   - delegates to ChatService.ask(...)
4. ChatService
   a. ChatMemoryService.addTurn(sessionId, user)
   b. short-circuits (cheap pre-checks):
        - small-talk?  → templated reply
        - command "show me …"?  → returns list as AnswerMetadata
        - already in current branch?  → answer from cache
   c. QueryRouterService.classify(message)
        - SIMPLE_REDIS (e.g., "list branches", "global alert count")
            → RedisQueryService.<intent>()  → AnswerTemplateService
        - COMPLEX_LLM (e.g., free-form summary, multi-hop)
            → QueryIntentResolver
                - QueryIntent enum value
                - NodeNameResolver (alias-aware)
            → ResolvedQuery
            → DeterministicAnswerService.render(resolved)
                 if unresolved → OpenAIClient.chat(messages) with SYSTEM_PROMPT
                               + StructuredContextBuilder.build(branch snapshot, alerts)
5. ChatService formats ChatResponse { answer (markdown), metadata, chartData? }
6. ChatMemoryService.addTurn(sessionId, assistant)
7. Returns to browser; MessageBubble renders <details>/<summary> + chart
```

### 6.2 Event Ingestion Path

```
1. ThingsBoard rule fires → POST /api/v1/webhook/tb
   Body: TbEventPayload
2. WebhookController (NO HMAC check)
   └─ RabbitMQQueueService.publish("iot.events", evt)
3. Topic exchange "iot.events" → per-customer queue iot.events.q.{customer}
4. EventConsumerService.handle(evt)  (consumer JVM)
   a. IdempotencyService.markIfNew(eventId)  → skip if seen (24h)
   b. EventParseService.parse(deviceName)    → (customerCode, branchName)
   c. EventWriteService.save(deviceEvent)    → TimescaleDB
   d. AncestorPathService.lookup(branch)     → ancestor path string
   e. RedisCacheService.apply(evt)           → HSET deviceState:{id} fields
   f. LuaScriptService.updateCounters(nodePath, prevStatus, newStatus)
        → atomic decrement old bucket, increment new bucket
        → recompute nodeCounters:{path} and globalCounters
5. Returns ack; WebhookController returns 200
```

### 6.3 Replay / Reconciliation

```
ReplayService.adminTrigger(customerCode, from, to)
  - find DeviceEvents in window
  - sort by receivedAt asc
  - apply each via RedisCacheService.apply(...)
  - last status per device wins

ReconciliationService.@Scheduled(cron="0 0 2 * * ?", zone="Asia/Kolkata")
  - per customer: read last 24h of device_events
  - replay them, recompute counters
  - log diff (current vs expected)
```

---

## 7. Key Patterns & Design Decisions

- **Profile-based node splitting.** `@Profile` annotations on `ChatController`, `WebhookController`, `EventConsumerService` allow one codebase to run as chat, ingestion, or consumer JVM. This is a deliberate deployment choice — not a runtime polymorphism.
- **Strategy + Rule-based Router.** `QueryRouterService` is a hard-coded classifier with intent-aware branches. New question types are added by extending `QueryIntent` enum, `IntentKeyProfileRegistry`, and a template in `AnswerTemplateService`.
- **ThreadLocal request context.** `ThingsBoardRequestContext` + `ThingsBoardRequestFilter` propagate per-user JWT across the call stack to `UserAwareThingsBoardClient` without polluting method signatures. The filter is responsible for clearing.
- **Deterministic-first, LLM-fallback.** Most reads are O(1) Redis lookups; only "give me a summary" or unresolved queries hit OpenAI. Token usage is bounded by `ChatMemoryService` sliding window + `TokenCounterService` budget.
- **Atomic Redis counters via Lua.** `update_counters.lua` ensures status transitions (e.g., ON→OFF) decrement the previous bucket and increment the new bucket in one round-trip. This is critical because multi-threaded event consumption would otherwise race.
- **Idempotency at the consumer boundary.** `IdempotencyService` uses Redis `setIfAbsent` with 24h TTL keyed on event UUID. This protects against at-least-once delivery from RabbitMQ.
- **Field precedence + alias index.** `FieldPrecedenceResolver` and `BranchAliasIndex` let the same data model survive different tenant naming conventions without a migration.
- **`@Scheduled` reconciliation.** A nightly cron rebuilds Redis state from the event log, providing a soft "self-healing" mechanism without requiring a transactional outbox.
- **Embedded React with iframe-friendly shell.** Static bundle in `src/main/resources/static`, body is transparent, `pointer-events:none` on the root, allowing the bot to sit on top of the ThingsBoard UI without occluding it.
- **JPA + native array column.** `branch_ancestor_paths.ancestor_path` uses a Postgres-specific text[]; in-memory H2 tests must use a compatible mode (`MODE=PostgreSQL`).
- **`util` vs `utility` split.** The `util` package holds runtime helpers; `utility` holds standalone CLI tools. This is a deliberate separation (CLI tools can be invoked without booting the full Spring context) but causes naming confusion — see Risks.

---

## 8. Things to Watch Out For

### Secrets & Configuration
- **`application-dev.properties` contains real credentials in the repo**: ThingsBoard system user, TimescaleDB, Upstash Redis, CloudAMQP, OpenAI API key. **Must be rotated and moved to env vars / a secret manager before any external release.**
- `application.properties` defaults to H2 with username `sa` / empty password — fine for dev, dangerous if accidentally deployed.
- `CorsConfig` allows `*` for all origins — must be tightened per-tenant before any public exposure.

### Security
- `WebhookController` accepts incoming webhooks with **no HMAC / signature verification**. Anyone who can reach the ingestion endpoint can inject events into the pipeline. Add HMAC header verification using a shared secret.
- `HierarchyAdminController` and `ReplayController` have **no authentication** on the admin endpoints — anyone with network access to the chat JVM can trigger a hierarchy import or replay.
- `JwtParserUtil` is an in-house implementation; library-based parsing (jjwt or nimbus-jose-jwt) is safer for production.

### Performance / Memory
- `DataService` keeps a `ConcurrentHashMap` 5-minute cache and a `indexByUser` map; **the `indexByUser` map has no eviction** and grows for the lifetime of the JVM. With 10 tenants × O(hundreds) of users, this is manageable, but in a multi-tenant SaaS it will leak.
- `ChatMemoryService` is in-memory and unbounded by user count. A Redis-backed implementation (already partially available via `RedisCacheService`) is recommended.
- `UserDataService` `@Scheduled` refresh runs per-user; if the user count grows large, the scheduled task will start blocking other scheduler ticks.

### Code Health
- **`util.ContextFilterUtil` is deprecated but still in the tree.** Should be removed in a cleanup PR.
- **Two utility-style packages (`util`, `utility`)** with different conventions. New developers will reach for the wrong one. Consider renaming `utility → tools` (or `cli`) and documenting the split.
- `ChatService.SYSTEM_PROMPT` is a large inline string literal. If it grows further, move to `classpath:prompts/system-prompt.txt` to make iteration faster and avoid recompilation.
- The query router is a chain of `if/else` inside `QueryRouterService.classify(...)`. With 25+ intents, this is getting hard to maintain. Consider a declarative rules table.

### Data Model
- `BranchAncestorPath.ancestor_path` uses a Postgres `text[]` column. **H2 in non-PostgreSQL mode will fail.** `application-test.properties` correctly uses `MODE=PostgreSQL`, but new devs may be caught by this.
- The `deviceName` parser in `EventParseService` is purely string-based; renaming devices in ThingsBoard will silently break routing. Add a periodic validator that flags devices whose name does not match a known customer prefix.

### Operational
- `ReconciliationService` cron is `0 0 2 * * ? Asia/Kolkata` — verify the JVM default timezone matches; otherwise the cron will drift.
- `RabbitMQConfig` declares per-customer queues on startup. Adding a new customer requires a redeploy of the consumer JVM unless a bootstrap job creates the queues on the broker side.
- The ingestion webhook does not persist the raw payload on the way in; if Redis is flushed before the event hits TimescaleDB, the event is lost. Replay is only possible from `device_events`.

### Tests
- The golden-question suite depends on `fixtures/full_data_fixture.json`; if fixture maintenance lags, accuracy will silently degrade. Wire it into CI with a minimum accuracy threshold.

---

## 9. Recommended Starting Points

For a brand-new developer onboarding in week 1, follow this order:

1. **Read these three docs, in order:**
   - `README.md` — what SAI is and who uses it.
   - `DEVELOPMENT.md` — local dev workflow.
   - `BOT_PROPOSAL_DOCUMENT.md` — the architectural north star (chat / ingestion / consumer split, hybrid brain).

2. **Get the dev environment up:**
   - `docker compose up` (or just `mvn spring-boot:run` against `dev` profile).
   - Open the React shell at `http://localhost:8083/`; verify the transparent iframe.

3. **Read the code in this order:**
   - `ThingsBoardBotApplication.java` — entry point.
   - `pom.xml` — confirm Spring Boot 4.0.3, Java 21, no Spring AI.
   - `application.properties` + `application-dev.properties` — note all the credentials (do not commit any new ones).
   - `controller/ChatController.java` → `service/ChatService.java` — see the orchestrator.
   - `service/query/QueryRouterService.java` + `QueryIntent.java` + `IntentKeyProfileRegistry.java` — the deterministic brain.
   - `service/DeterministicAnswerService.java` + `AnswerTemplateService.java` — answer rendering.
   - `controller/WebhookController.java` + `service/EventConsumerService.java` — the ingestion half.
   - `service/RedisCacheService.java` + `scripts/update_counters.lua` — atomic counters.
   - `service/ReconciliationService.java` + `ReplayService.java` — self-healing.

4. **Run the test suite:** `mvn test`. Pay particular attention to `integration/GoldenQuestionAccuracyTest` and the normalization tests.

5. **Try a small change:** add a new `QueryIntent` value + Redis key in `IntentKeyProfileRegistry` + a template in `AnswerTemplateService`. This is the most common type of change and the most isolated.

6. **Then** the three "watch out" items to read first: webhook HMAC, `indexByUser` eviction, and `application-dev.properties` secrets.
