# Fresh-start data refresh (ThingsBoard → TimescaleDB → Redis)

Wipes the analytical stores and rebuilds them from the **current** state of ThingsBoard. The
hierarchy is rebuilt from each device's name prefix + `full_path` (ThingsBoard is the source of
truth — any CSV-imported hierarchy is replaced).

> ⚠️ **Destructive and irreversible.** The importer runs `DELETE FROM device_events`,
> `hierarchy_nodes`, and `branch_ancestor_paths` on the configured database (currently the live
> Timescale Cloud `tsdb` in `application-dev.properties`). Take a `pg_dump` first if you want a
> safety net. The `customers` mapping table is NOT touched.

## Steps

```bash
# 1. Fetch current data from ThingsBoard -> Thingsboard-Data/thingsboard_devices_backup.json
./mvnw -q exec:java@tb-backup

# 2. Wipe + reimport TimescaleDB (device_events + hierarchy_nodes + branch_ancestor_paths)
./mvnw -q exec:java@tb-import

# 3. Rebuild Redis from the new device_events (clears each customer's cache, recomputes counters).
#    Requires the app running (e.g. on 8083):
curl.exe -X POST "http://localhost:8083/api/v1/admin/replay?customerId=ALL"
```

## Notes

- Credentials (TB + DB) are read from `application-dev.properties`.
- The fetched snapshot lands in `Thingsboard-Data/` (gitignored — it contains live telemetry).
- Customer/branch are derived from the device name prefix (`SEPL-DX2` → customer `SEPL`, branch
  `DX2`). Devices must follow that `PREFIX-...` naming for correct attribution.
- Step 3 is the only step that needs the running application; replay holds a per-customer lock and
  pauses the local consumer while it rebuilds (audit #12).
