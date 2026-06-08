# ADR 0002 — Atomic counter updates via Redis Lua script

**Status:** Accepted

## Context
Multiple `consumer` instances process at-least-once RabbitMQ events concurrently and update
aggregate counters (online/offline branches, system states) in Redis. A read-modify-write done
in application code races: two consumers can read the same value and both write back, losing an
update. Idempotency is also required because at-least-once delivery means duplicate events.

## Decision
Perform counter mutation inside a single Redis Lua script (`scripts/update_counters.lua`),
loaded once at startup (`LuaScriptService`). Redis executes the script atomically, so the
read-modify-write and idempotency check happen as one indivisible operation server-side.

## Consequences
- **+** Correct counters under concurrent consumers; no lost updates.
- **+** Idempotency enforced atomically — safe replay of duplicate events.
- **−** Business logic lives in Lua, outside the Java type system and test harness.
- **−** Couples the design to Redis scripting; portability to another store is non-trivial.
