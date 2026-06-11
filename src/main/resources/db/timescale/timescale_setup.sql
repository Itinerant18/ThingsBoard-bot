-- TimescaleDB setup for device_events (audit finding #9 / #7.1).
-- Run AFTER the device_events table exists (Hibernate ddl-auto creates it). Idempotent.
-- Executed automatically by TimescaleInitializer when iotchatbot.timescale.init-enabled=true
-- (production with a TimescaleDB/PostgreSQL backend). H2/local dev leaves this untouched.

-- 1. Ensure the extension is present (no-op on managed TimescaleDB where it is preinstalled).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- 2. Convert the plain table into a hypertable partitioned on event_time. Without this, every
--    time-range query is a full table scan instead of chunk-pruned. migrate_data moves any rows
--    already present; if_not_exists makes re-runs safe.
SELECT create_hypertable('device_events', 'event_time',
                         chunk_time_interval => INTERVAL '1 day',
                         migrate_data => TRUE,
                         if_not_exists => TRUE);

-- 3. Retention: drop chunks older than the configured window so the table does not grow unbounded.
SELECT add_retention_policy('device_events', INTERVAL '180 days', if_not_exists => TRUE);

-- 4. Hypertable-aware indexes for the hot query paths (mirrors the entity @Index definitions).
CREATE INDEX IF NOT EXISTS idx_device_events_customer_time
    ON device_events (customer_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_device_events_customer_branch_time
    ON device_events (customer_id, branch_node_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_device_events_time
    ON device_events (event_time DESC);
