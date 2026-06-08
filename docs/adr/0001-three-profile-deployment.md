# ADR 0001 — Three-profile deployment (chat / ingestion / consumer)

**Status:** Accepted

## Context
The bot does three distinct jobs: serve chat queries (HTTP), receive ThingsBoard webhooks
(HTTP), and consume RabbitMQ events to update state (background worker). Bundling all three in
one always-on process couples scaling and failure domains: a chat traffic spike shouldn't
starve event consumption, and a consumer crash shouldn't take down the chat API.

## Decision
Split responsibilities behind Spring `@Profile` gates — `chat`, `ingestion`, `consumer` — so
each can be deployed and scaled independently. A `dev` profile activates everything for
zero-setup local runs.

## Consequences
- **+** Independent scaling and isolated failure domains per concern.
- **+** Consumer runs with `web-application-type=none` (no HTTP surface to attack).
- **−** `dev` blurs boundaries; developers may not realize a component is profile-specific.
- **−** Controllers must declare profiles deliberately (e.g. `WebhookController` is
  `@Profile("ingestion")`); admin controllers are profile-agnostic and rely on the
  `AdminAuthFilter` token guard instead.

## Known constraint — `chat` profile is currently single-replica
`ChatMemoryService` holds conversation memory in-JVM (TTL-evicted, see `RUNBOOKS.md` #5). This
state is **not shared across instances**, so running more than one `chat` replica splits memory:
a user's follow-up may land on a node that never saw the prior turns, and history is lost on
restart. The `ingestion` and `consumer` profiles are stateless (state lives in Redis) and scale
horizontally without this caveat. **Run exactly one `chat` replica** until conversation memory is
moved to a shared store (Redis). Independent scaling of the other two profiles is unaffected.
