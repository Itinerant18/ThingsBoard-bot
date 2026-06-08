# Operational Runbooks — ThingsBoard-Bot

Incident playbooks for the on-call engineer. Each entry: symptom → diagnose → mitigate → verify.

---

## 1. Webhook ingestion down (events not arriving)

**Symptom:** Branch state stops updating; `WebhookController` logs go quiet; RabbitMQ queue depth flat.

**Diagnose**
- `GET /webhooks/health` on the ingestion node → expect `{"status":"UP"}`.
- Check ThingsBoard rule-chain webhook config points at the current ingestion URL.
- If requests arrive but return `401`: HMAC mismatch — see below.
- Inspect `/actuator/metrics` and RabbitMQ console for exchange `iot.events` publish rate.

**Mitigate**
- Wrong/stale secret: align `IOTCHATBOT_SECURITY_WEBHOOK_HMAC_SECRET` with the value ThingsBoard signs with. Empty secret = verification disabled (a `[SECURITY]` warning is logged each request).
- Ingestion node down: restart the `ingestion` profile instance.
- Backlog after recovery: run a replay (runbook #4) for the gap window.

**Verify:** new events flow; queue depth rises then drains; branch snapshots update.

---

## 2. Redis drift / stale or wrong counters

**Symptom:** Deterministic answers report values that disagree with ThingsBoard ground truth.

**Diagnose**
- Compare a known branch's Redis state vs ThingsBoard UI.
- Check `[RECONCILE]` logs from the nightly 02:00 IST `ReconciliationService` run for drift counts.
- Check consumer health — a dead consumer means counters stop advancing.

**Mitigate**
- Targeted fix: replay the affected customer/window (runbook #4) to rebuild from the durable event log.
- Broad corruption: flush the affected Redis keys, then replay.
- The in-memory branch-index cache self-evicts after 10 min idle; force-refresh by re-querying.

**Verify:** reconciliation reports zero drift; spot-checked branches match ThingsBoard.

---

## 3. LLM quota / rate-limit exhaustion

**Symptom:** LLM-backed answers fail or stall; OpenAI 429s in logs. Deterministic path (~80% of traffic) is unaffected.

**Diagnose**
- `/actuator/prometheus` → `chat_answers_total{type="llm"}` vs `{type="deterministic"}` to gauge LLM load.
- Confirm 429 / quota errors from the OpenAI client.

**Mitigate**
- Rotate to a key with remaining quota via `OPENAI_API_KEY` and restart the `chat` node.
- Lower `iotchatbot.openai.max-tokens` to reduce spend per call.
- The deterministic brain keeps serving most queries; only free-form LLM answers degrade.

**Verify:** LLM answers resume; 429 rate drops to zero.

---

## 4. Replay from event log

Rebuild Redis state from the durable TimescaleDB event log for a customer/time window.

```
POST /api/v1/admin/replay?customerId=BOI&startTime=2026-06-01T00:00:00Z&endTime=2026-06-02T00:00:00Z
Header: X-Admin-Token: <IOTCHATBOT_SECURITY_ADMIN_TOKEN>
```

Omitting `startTime`/`endTime` defaults to the last 7 days. Requires the admin token when
`iotchatbot.security.admin-token` is set (it should be in production).

**Verify:** counters for the window match the event log; `[REPLAY-API]` logs `SUCCESS`.

---

## 5. Memory growth on long-running JVM

**Symptom:** Heap climbs steadily over hours/days.

**Diagnose / Mitigate**
- `ChatMemoryService` evicts sessions idle > 30 min (sweep every 5 min) — `[MEMORY]` logs show evictions.
- `BranchIndexService` evicts cached indexes idle > 10 min — `[CACHE]` logs show evictions.
- If growth persists, capture a heap dump and inspect; these two caches are the known unbounded risks and are now TTL-bounded.

> **Scaling note:** `ChatMemoryService` is in-JVM, so the `chat` profile must run **single-replica** — multiple chat nodes split conversation memory. `ingestion`/`consumer` are stateless (Redis-backed) and scale freely. See ADR-0001.

---

## Quick reference

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Liveness/readiness |
| `/actuator/prometheus` | Metrics scrape: `chat_answers_total{type}`, `chat_latency_seconds`, `llm_tokens_total`, `redis_operations_total{result}`, `redis_latency_seconds`, `rabbitmq_queue_depth`, `rabbitmq_publish_total{result}`, `openai_requests_total{result}`, `openai_tokens_used`, `openai_latency_seconds`, `query_router_total{complexity}` |
| `/webhooks/health` | Ingestion node health |
| `POST /api/v1/admin/replay` | Replay (needs `X-Admin-Token`) |
| `POST /api/v1/admin/hierarchy/import` | Hierarchy import (needs `X-Admin-Token`) |

**Security env vars:** `IOTCHATBOT_SECURITY_WEBHOOK_HMAC_SECRET`, `IOTCHATBOT_SECURITY_ADMIN_TOKEN`, `IOTCHATBOT_SECURITY_ALLOWED_ORIGINS`.
