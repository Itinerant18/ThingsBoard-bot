# Audit Score Report — ThingsBoard-Bot (SAI)

## Executive Summary
| Dimension | Score (1–5) | Weight | Weighted Score |
|---|---|---|---|
| Architecture & Design | 4 | 20% | 0.80 |
| Code Quality & Maintainability | 3 | 20% | 0.60 |
| Security | 2 | 20% | 0.40 |
| Testing & Reliability | 4 | 15% | 0.60 |
| Observability & Operations | 3 | 10% | 0.30 |
| Performance & Scalability | 3 | 10% | 0.30 |
| Documentation & Onboarding | 4 | 5% | 0.20 |
| **TOTAL** | — | **100%** | **3.20 / 5.00** |

**Overall Grade: B− (73/100)** — Solid engineering with deliberate architectural choices, but critical security gaps and some technical debt require immediate attention before production hardening.

---

## Detailed Findings

### 1. Architecture & Design (4/5)
**Strengths:**
- Clear three-profile deployment model (`chat`, `ingestion`, `consumer`, `dev`) with `@Profile` gates — clean separation of concerns
- Hybrid deterministic/LLM brain: ~80% of queries served from Redis with zero LLM latency; fallback is bounded and auditable
- Event-driven ingestion via RabbitMQ with per-customer queues, idempotency, and atomic Lua counters — correct handling of at-least-once delivery
- Self-healing via nightly `ReconciliationService` + on-demand `ReplayService` from durable event log (TimescaleDB)
- Well-scoped packages (`service.query.*`, `service.normalization.*`) reflecting domain boundaries

**Weaknesses:**
- All-in-one `dev` profile blurs the boundary in local development; developers may not realize which components are profile-specific
- `QueryRouterService` is an `if/else` chain; as intents grow (>25), maintainability will degrade — consider a rule table
- `BranchAncestorPath` uses Postgres `text[]` — limits portability and complicates H2 test setup

---

### 2. Code Quality & Maintainability (3/5)
**Strengths:**
- Consistent Lombok usage (`@Data`, `@Builder`, `@RequiredArgsConstructor`) reduces boilerplate
- Immutable `ResolvedQuery` with `@Value @Builder` is a clean pattern
- Normalization pipeline (`FullDataPayloadParser` → `BranchSnapshotMapper` → `ValueNormalizer`) is well-factored and tested
- `util` vs `utility` split (runtime helpers vs standalone CLI) is intentional

**Weaknesses:**
- Large inline `SYSTEM_PROMPT` string in `ChatService` — hard to iterate; move to external resource
- `ContextFilterUtil` is `@Deprecated` but still in tree — remove
- `DataService.indexByUser` `ConcurrentHashMap` has no eviction — unbounded growth in long-running JVMs
- Two utility packages (`util`, `utility`) with similar names cause confusion; rename `utility → tools` or `cli`
- Some services exceed 300 lines (`ChatService` 570 lines, `DeterministicAnswerService` 980 lines) — consider extracting sub-handlers

---

### 3. Security (2/5) ⚠️ **CRITICAL**
**Critical Issues:**
- **`application-dev.properties` contains real production secrets** in Git: ThingsBoard credentials, TimescaleDB, Upstash Redis, CloudAMQP, OpenAI API key — **must be rotated and removed from repo immediately**
- **WebhookController has zero HMAC/signature verification** — anyone who can reach the ingestion endpoint can inject arbitrary events
- **Admin endpoints (`HierarchyAdminController`, `ReplayController`) have no authentication** — full hierarchy import / replay accessible to any network peer
- **`CorsConfig` allows `*` origins** — tighten to tenant allow-lists before any public exposure
- **In-house `JwtParserUtil`** — prefer battle-tested library (jjwt / nimbus-jose-jwt)

**Medium Issues:**
- `X-TB-Token` passed as header but no validation of token expiry or audience
- No rate limiting on chat endpoints

---

### 4. Testing & Reliability (4/5)
**Strengths:**
- Comprehensive unit tests for normalization pipeline (`ValueNormalizerTest`, `BranchSnapshotMapperTest`, `FullDataPayloadParserTest`)
- Integration test `GoldenQuestionAccuracyTest` exercises the full deterministic brain against `fixtures/full_data_fixture.json`
- `application-test.properties` correctly uses H2 in `MODE=PostgreSQL` for array column compatibility
- MockMvc tests for controllers; mock-based tests for services

**Weaknesses:**
- No contract tests for OpenAI client or ThingsBoard client
- `ChatMemoryService` (in-memory) not tested under concurrent load
- No chaos/integration test for `ReconciliationService` drift detection
- CI pipeline not visible in repo (no `.github/workflows` or `.gitlab-ci.yml`)

---

### 5. Observability & Operations (3/5)
**Strengths:**
- Structured logging throughout with `[SERVICE]`, `[CACHE]`, `[HIERARCHY]`, `[LUA]`, `[RECONCILE]` prefixes
- `@Scheduled` reconciliation at 02:00 IST with drift logging
- `Actuator` dependency present (health, metrics endpoints)
- `ReplayService` and `ReconciliationService` provide operational levers

**Weaknesses:**
- No distributed tracing (OpenTelemetry, Micrometer Tracing)
- No metrics exported for Redis hit/miss, RabbitMQ queue depth, LLM token usage, deterministic vs LLM ratio
- No alerting rules or runbooks in repo
- `ChatMemoryService` and `DataService` caches not exposed via actuator

---

### 6. Performance & Scalability (3/5)
**Strengths:**
- Deterministic path is O(1) Redis reads; p99 < 50 ms
- Lua script ensures atomic counter updates under concurrent consumers
- 5-minute in-memory cache in `DataService` absorbs burst traffic
- Streaming SSE endpoint with `requestAnimationFrame` batching keeps UI fluid

**Weaknesses:**
- `DataService.indexByUser` and `ChatMemoryService` are unbounded in-memory maps — will OOM in multi-tenant SaaS
- `UserDataService` per-user `@Scheduled` refresh does not batch; scheduler thread pool starvation at scale
- No connection pool sizing for RabbitMQ / Redis / PostgreSQL visible in config
- OpenAI `RestTemplate` uses default pool — no timeout/pool tuning for streaming

---

### 7. Documentation & Onboarding (4/5)
**Strengths:**
- `README.md` — clear product description
- `DEVELOPMENT.md` — step-by-step local setup
- `BOT_PROPOSAL_DOCUMENT.md` — architectural north star (v4.0 split)
- `DAILY_TASKS.md` — running progress log with blockers
- Inline Javadoc on public APIs and complex logic (e.g., `QueryIntentResolver`)

**Weaknesses:**
- No architecture decision records (ADRs) for key choices (why Lua, why three profiles, why `text[]`)
- No runbooks for incident response (webhook down, Redis drift, LLM rate-limit)
- Frontend lacks Storybook or component docs

---

## Priority Action Plan

| Priority | Item | Effort | Owner |
|---|---|---|---|
| **P0** | Rotate & remove all secrets from `application-dev.properties`; move to env vars / secret manager | 1 day | Platform |
| **P0** | Add HMAC verification to `WebhookController` | 1 day | Backend |
| **P0** | Add authentication (or at minimum IP allow-list) to admin endpoints | 1 day | Backend |
| **P1** | Tighten `CorsConfig` to tenant-specific origins | 2 hrs | Backend |
| **P1** | Add eviction / TTL to `DataService.indexByUser` and `ChatMemoryService` (move to Redis) | 3 days | Backend |
| **P1** | Extract `SYSTEM_PROMPT` to `classpath:prompts/system-prompt.txt` | 2 hrs | Backend |
| **P2** | Replace `JwtParserUtil` with jjwt library | 1 day | Backend |
| **P2** | Add Micrometer + Prometheus metrics for Redis, RabbitMQ, LLM, deterministic ratio | 2 days | Platform |
| **P2** | Rename `utility → tools` and document the `util` vs `tools` split | 1 day | Backend |
| **P2** | Wire `GoldenQuestionAccuracyTest` into CI with accuracy gate | 1 day | Platform |
| **P3** | Add distributed tracing (OpenTelemetry) | 3 days | Platform |
| **P3** | Create runbooks for: webhook outage, Redis flush, LLM quota exhaustion | 2 days | SRE |

---

## Appendix: Quick Reference — Risk Matrix

| Risk | Likelihood | Impact | Mitigation Status |
|---|---|---|---|
| Secrets leaked via Git | High | Critical | ❌ Not mitigated |
| Unauthenticated webhook injection | Medium | Critical | ❌ Not mitigated |
| Unauthenticated admin replay/import | Medium | High | ❌ Not mitigated |
| OOM from unbounded in-memory caches | Medium | High | ❌ Not mitigated |
| H2/Postgres array column mismatch in prod | Low | Medium | ⚠️ Test config correct |
| Scheduler thread starvation at scale | Medium | Medium | ❌ Not mitigated |
| LLM cost runaway (no budget guard) | Low | Medium | ⚠️ TokenCounter exists but not enforced |
| Single-point-of-failure: consumer JVM | Medium | High | ⚠️ RabbitMQ HA + Reconciliation |

---

*Report generated on 2026-06-08 by automated codebase analysis.*