# SAI Chatbot — Roadmap to Real-Time Data

**To:** CEO
**From:** Engineering
**Subject:** Static → Real-Time: where the bot is today, and the work to make it live
**Status:** Phase 1 (Static) operational · Phase 2 (New Data Structure) in progress · Phase 3 (Webhook) planned

---

## 1. Executive Summary

The **SAI (Smart Assistant for IoT)** chatbot is **live and working today** using a static snapshot of the bank's IoT data. The bot can already answer ~80% of operational questions — branch health, alerts, gateway status, CCTV, power — by reading from a pre-built snapshot instead of receiving live events.

We are now in the middle of **Phase 2: re-platforming the data layer** to a normalized per-branch model. Once that lands, **Phase 3: webhook integration** will make every ThingsBoard event flow into the bot within seconds, in real time.

**What this means for the business:**

- **Today (Phase 1):** Answers are correct, but lag the ThingsBoard dashboard by the refresh cycle of the snapshot (minutes to hours, depending on deployment).
- **Within ~2 sprints (Phase 2 done):** The data structure is clean and indexed; bot answers stay correct as we wire up live data.
- **Within ~3–4 sprints from today (Phase 3 done):** Bot answers match the dashboard in **near real-time** with no manual refresh.

No customer-facing downtime. The same chat surface, the same multi-tenant dashboard embedding, the same 10-bank rollout.

---

## 2. Current State — Phase 1 (Static Data)

**Status: Working in production.**

The bot reads from a snapshot of all customer devices (`GET /api/v1/data/full`) that the backend refreshes on a schedule. The React chat widget inside the ThingsBoard dashboard posts a question to `POST /api/v1/chat/ask`, the backend classifies the intent, and returns a deterministic markdown answer.

```
┌─────────────────────────────────────────────────────────────────┐
│                  PHASE 1 — STATIC DATA MODE                     │
│                                                                 │
│   ┌──────────────┐    scheduled    ┌───────────────────────┐    │
│   │ ThingsBoard  │  ─────────────► │  Backend JVM          │    │
│   │ (live data)  │    snapshot     │  DataService          │    │
│   └──────────────┘                 │  (5-min in-mem cache) │    │
│                                    └──────────┬────────────┘    │
│   ┌──────────────┐                            │                 │
│   │   Browser    │  POST /api/v1/chat/ask     │                 │
│   │   Chat UI    │ ◄──────────────────────────┘                 │
│   └──────────────┘     markdown answer                          │
│                                                                 │
│   Lag: minutes to hours (refresh interval)                      │
└─────────────────────────────────────────────────────────────────┘
```

**What works:**

- Multi-tenant chat (10 banks, hard-coded prefixes: `SYNB`, `UBIN`, `IOB`, `CBI`, …)
- Deterministic answer engine with 25+ intents
- OpenAI `gpt-4o-mini` fallback for free-form questions
- Voice input, glassmorphism UI, iframe-embedded in ThingsBoard
- Per-user branch index with alias matching
- Nightly reconciliation that rebuilds the snapshot from history

**What is the limitation:**

- Answers are only as fresh as the last snapshot pull.
- New events appear in the bot only after the next refresh tick.

---

## 3. Phase 2 — New Data Structure (In Progress)

**Status: Engineered, partially landed, being stabilized.**

We are replacing the flat `branchName.field` payload with a typed, normalized per-branch domain model. This is the prerequisite for real-time because:

- The deterministic engine needs to know **what kind of value** it is reading (state vs. number vs. boolean).
- Field names differ across tenants; we need a single canonical shape.
- Status transitions (ON → OFF, ONLINE → OFFLINE) need to update counters atomically across the whole branch and the global view.

```
┌─────────────────────────────────────────────────────────────────┐
│              PHASE 2 — NORMALIZED DATA MODEL                    │
│                                                                 │
│   Raw payload (messy)        Normalized BranchSnapshot (clean)  │
│   ─────────────────────      ────────────────────────────────   │
│   "AHD_Vastrapur.gateway     BranchIdentity{                    │
│     Status_battery = 12.4"    customerCode="SYNB"               │
│                               branchName="AHD_Vastrapur"        │
│   "AHD_Vastrapur.power_        parentPath="Gujarat/Ahmedabad"   │
│     status = ON"             }                                  │
│   "AHD_Vastrapur.camera_      GatewayStatus{ battery=12.4 }     │
│     online = 1"              PowerStatus{ mains=ON }            │
│   ...                        CctvStatus{ online=true }          │
│                               AlertSummary{ active=2 }          │
│                             }                                   │
│                                                                 │
│   Pipeline: FullDataPayloadParser → FieldPrecedenceResolver     │
│             → ValueNormalizer → BranchSnapshotMapper            │
│                                                                 │
│   Plus: BranchAliasIndex for fuzzy "vastrapur" ↔ "AHD_Vastrapur"│
└─────────────────────────────────────────────────────────────────┘
```

**Components completed:**

- Domain model (`BranchSnapshot`, `GatewayStatus`, `PowerStatus`, `CctvStatus`, `BranchSubsystems`, `AlertSummary`, `HardwareHealth`)
- Normalization pipeline (`FullDataPayloadParser`, `ValueNormalizer`, `FieldPrecedenceResolver`, `BranchSnapshotMapper`)
- Branch alias index
- Golden-question accuracy test suite

**What this enables:**

- The query engine reads typed values; no more "is this a string or a number?" guessing.
- Status transitions become first-class: ON→OFF updates a counter, not just a field.
- Per-tenant data differences are absorbed by the precedence resolver instead of breaking the answer.

---

## 4. Phase 3 — Webhook Implementation (Planned)

**Status: Code path exists, not yet wired to live TB.**

Once Phase 2 is stable, we turn on the real-time pipeline. ThingsBoard rules will fire on every relevant event and POST to our `WebhookController`. The consumer JVM picks the event up from RabbitMQ, deduplicates it, persists it to TimescaleDB, and atomically updates the Redis state that the chat path reads.

```
┌─────────────────────────────────────────────────────────────────┐
│                  PHASE 3 — REAL-TIME PIPELINE                   │
│                                                                 │
│   ┌──────────────┐                                              │
│   │ ThingsBoard  │  POST /api/v1/webhook/tb                     │
│   │   (live)     │ ──────────────► WebhookController            │
│   └──────────────┘                       │                      │
│                                          ▼                      │
│                              ┌──────────────────────┐           │
│                              │   RabbitMQ(CloudAMQP)│           │
│                              │   topic: iot.events  │           │
│                              │   per-customer queues│           │
│                              └──────────┬───────────┘           │
│                                         │                       │
│                                         ▼                       │
│                              ┌──────────────────────┐           │
│                              │ EventConsumerService │           │
│                              │  (consumer JVM)      │           │
│                              └──────────┬───────────┘           │
│                                         │                       │
│              ┌──────────┬───────────────┼───────────────┐       │
│              ▼          ▼               ▼               ▼       │
│      ┌──────────┐ ┌──────────┐  ┌──────────────┐ ┌──────────┐   │
│      │ Idempot. │ │ Event    │  │ TimescaleDB  │ │ Redis    │   │
│      │ Service  │ │ Write    │  │ (durable log)│ │ (state)  │   │
│      │ SETNX 24h│ │ Service  │  │              │ │ + Lua    │   │
│      └──────────┘ └──────────┘  └──────────────┘ └────┬─────┘   │
│                                                       │         │
│                                                       ▼         │
│      ┌──────────────────────────────────────────────────────┐   │
│      │   Chat path (chat JVM) — same as before,             │   │
│      │   but reads LIVE Redis state                         │   │
│      │   QueryRouter → RedisQueryService → AnswerTemplate   │   │
│      └──────────────────────────────────────────────────────┘   │
│                                                                 │
│   Lag: seconds (one webhook → one Redis update)                 │
└─────────────────────────────────────────────────────────────────┘
```

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



**Key properties:**

- **Idempotent.** Each event UUID is checked against Redis (`SETNX` with 24h TTL) before processing — duplicate deliveries are dropped.
- **Atomic counters.** A Lua script (`update_counters.lua`) decrements the old status bucket and increments the new one in a single Redis round-trip — no race conditions across concurrent events.
- **Replayable.** The full event log in TimescaleDB lets us rebuild Redis from scratch via `ReplayService` if needed.
- **Self-healing.** A nightly `@Scheduled` reconciliation in `ReconciliationService` (cron `0 0 2 * * ? Asia/Kolkata`) re-derives Redis from the last 24h of events and logs any drift.

**What changes for the user:**

- A power cut at a branch shows up in the bot within seconds, not minutes.
- An alert raised in ThingsBoard is reflected in the next question the user asks.
- No frontend changes — same chat widget, same UI.

---

## 5. Work Breakdown & Effort Estimate

This is a realistic look at the engineering work between today and the real-time launch.

### Already done (Phase 1)
| Area | Work |
|---|---|
| Backend | Spring Boot 4.0.3 service, multi-tenant, REST + AMQP |
| Chat engine | 25+ intents, deterministic answer engine, OpenAI fallback |
| Frontend | React 18 + Tailwind v4, voice input, iframe-embedded |
| Snapshot pipeline | `DataService` with 5-min cache |
| Tests | Golden-question accuracy test, normalization tests, controller tests |
| Infra | Dockerfile, docker-compose, Caddy reverse proxy |

### Phase 2 (now → 2 sprints)
| Task | Estimate | Notes |
|---|---|---|
| Stabilize `BranchSnapshotMapper` against all 10 tenants | 4–5 days | Per-tenant data quirks |
| Tighten `FieldPrecedenceResolver` rules | 2 days | |
| Promote `BranchAliasIndex` from soft to authoritative | 2 days | |
| Increase golden-question accuracy to > 95% | 3 days | Iterative |
| Performance test on 24h synthetic payload | 2 days | |
| **Subtotal** | **~13 working days** | |

### Phase 3 (2 sprints after Phase 2)
| Task | Estimate | Notes |
|---|---|---|
| HMAC signature verification on `WebhookController` | 1 day | Security: critical, not optional |
| Configure TB-side webhook rules (10 tenants) | 2 days | One per bank; reusable template |
| Per-customer queue + binding declarations in `RabbitMQConfig` | 1 day | Idempotent; safe to redeploy |
| Lua script hardening + Redis cluster failover test | 2 days | |
| ReconciliationService tuning + on-call runbook | 2 days | |
| End-to-end chaos test: kill consumer, replay events | 2 days | |
| Cutover plan: dual-run for 1 week, then flip | 2 days | Zero-downtime switch |
| **Subtotal** | **~12 working days** | |

### Total remaining effort: **~25 working days** (≈ 5 calendar weeks, 1 engineer)

---

## 6. Timeline

```
Week  1  2  3  4  5  6  7  8  9
      ├──┴──┤                 PHASE 2 — New data structure
            ├──┴──┴──┤         PHASE 3 — Webhook implementation
                     ├──┴──┤   Dual-run + cutover
                        ▲
                        │
                  Real-time bot live
```

- **Today:** Bot in production on static data.
- **End of Week 2:** New data structure stable, golden-questions > 95%.
- **End of Week 4:** Webhook pipeline code-complete.
- **End of Week 5:** Dual-run for one customer (pilot bank), then full cutover.
- **End of Week 6:** Real-time bot fully live across all 10 tenants.

---

## 7. Business Impact

| Dimension | Today (Static) | After Phase 3 (Real-Time) |
|---|---|---|
| **Time to reflect a new alert in chat** | Minutes – hours (snapshot interval) | Seconds |
| **Ops use case** | "What's the status right now?" answered with last-known | Live, same as dashboard |
| **Customer trust** | "It lags a bit" | "It knows" |
| **OpenAI spend** | Low (deterministic-first) | Low (no change — same engine) |
| **Infra cost** | Single JVM | Chat JVM + Ingestion JVM + Consumer JVM (horizontal) |
| **Downtime risk** | Low | Low (dual-run cutover) |

The deterministic engine is the same in both phases; we are not rewriting the brain, only the data feed. That keeps risk contained and lets us ship the real-time cutover with confidence.

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Webhook spoofing / injection | HMAC signature verification on every request (Phase 3, day 1) |
| Duplicate events from RabbitMQ | IdempotencyService (Redis `SETNX`, 24h TTL) — already built |
| Redis flush = data loss | TimescaleDB event log + `ReplayService` rebuild |
| Per-tenant data quirks breaking the answer | `FieldPrecedenceResolver` + `BranchAliasIndex` + golden-question regression |
| JVM time-zone drift on the nightly cron | Hard-coded `zone="Asia/Kolkata"` on the `@Scheduled` annotation |
| Cutover surprises | One-week dual-run with a pilot bank before flipping all 10 |

---

## 9. What I'm Asking For

- **Approval to proceed** with Phase 3 as scoped (~12 engineering days).
- **One pilot bank** for the dual-run cutover (recommend `SYNB` — largest tenant, best telemetry coverage).
- **Continued access** to a ThingsBoard sandbox tenant for chaos-testing the webhook path.

No additional headcount required. No new vendors. The same React + Spring stack we already run.

---

*Prepared by Engineering · Source: in-code analysis of `C:\workspace\thingsboard\ThingsBoard-Bot` and the full `CODEBASE_ANALYSIS_REPORT.md` companion document.*
