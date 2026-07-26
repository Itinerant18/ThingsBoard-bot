from collections import Counter
from typing import Any

from sqlalchemy import select

from app.db.models import DeviceEvent
from app.query.contracts import Answer, ExtractedIntent, RequestContext


class GlobalOverview:
    intent = "global_overview"

    async def can_handle(self, intent: ExtractedIntent) -> bool:
        return intent.name == self.intent

    async def handle(self, intent: ExtractedIntent, ctx: RequestContext) -> Answer:
        if not ctx.tenant.customer_id:
            return Answer(
                "Your token is not scoped to a customer, so I cannot retrieve fleet data."
            )
        fleet = await ctx.tb.devices(ctx.tenant.customer_id)
        devices = fleet.get("data", fleet) if isinstance(fleet, dict) else fleet
        return Answer(
            f"This customer has {len(devices)} device(s) in the ThingsBoard fleet.",
            {"device_count": len(devices)},
            [{"type": "thingsboard", "resource": "devices"}],
        )


class DeviceInventory:
    intent = "device_inventory"

    async def can_handle(self, intent: ExtractedIntent) -> bool:
        return intent.name == self.intent

    async def handle(self, intent: ExtractedIntent, ctx: RequestContext) -> Answer:
        if not ctx.tenant.customer_id:
            return Answer(
                "Your token is not scoped to a customer, so I cannot retrieve device inventory."
            )
        fleet = await ctx.tb.devices(ctx.tenant.customer_id)
        devices: list[dict[str, Any]] = fleet.get("data", []) if isinstance(fleet, dict) else fleet
        names = [
            str(device.get("name", device.get("id", {}).get("id", "unknown"))) for device in devices
        ]
        shown = ", ".join(names[:10]) or "none"
        suffix = " (showing first 10)" if len(names) > 10 else ""
        return Answer(
            f"Found {len(names)} device(s): {shown}{suffix}.",
            {"devices": devices},
            [{"type": "thingsboard", "resource": "devices"}],
        )


class AlarmDetail:
    intent = "alarm_detail"

    async def can_handle(self, intent: ExtractedIntent) -> bool:
        return intent.name == self.intent

    async def handle(self, intent: ExtractedIntent, ctx: RequestContext) -> Answer:
        rows = (
            (
                await ctx.db.execute(
                    select(DeviceEvent.event_type)
                    .where(
                        DeviceEvent.tenant_id == ctx.tenant.tenant_id,
                        DeviceEvent.event_type.in_(["alarm", "alert", "fault"]),
                    )
                    .limit(100)
                )
            )
            .scalars()
            .all()
        )
        counts = Counter(rows)
        if not counts:
            return Answer("I found no recorded alarm events for this tenant.", {"alarms": {}})
        summary = ", ".join(f"{kind}: {count}" for kind, count in counts.items())
        return Answer(
            f"Recorded alarms: {summary}.",
            {"alarms": dict(counts)},
            [{"type": "device_event", "resource": "tenant-scoped"}],
        )


class SubsystemStatus:
    intent = "subsystem_status"

    async def can_handle(self, intent: ExtractedIntent) -> bool:
        return intent.name == self.intent

    async def handle(self, intent: ExtractedIntent, ctx: RequestContext) -> Answer:
        if not intent.device_id:
            return Answer(
                "Please name a device (for example, ‘status for device abc-123’) to check its subsystem status."
            )
        raw = await ctx.redis.get(f"snapshot:{ctx.tenant.tenant_id}:{intent.device_id}")
        # decode_responses=True on the client makes this str at runtime.
        snapshot = raw.decode() if isinstance(raw, bytes) else raw
        if not snapshot:
            telemetry = await ctx.tb.telemetry(intent.device_id)
            return Answer(
                "Live telemetry was retrieved; no cached normalized subsystem snapshot is available yet.",
                {"telemetry": telemetry},
                [{"type": "thingsboard", "resource": f"device:{intent.device_id}"}],
            )
        return Answer(
            f"Latest subsystem snapshot for {intent.device_id}: {snapshot}",
            {"snapshot": snapshot},
            [{"type": "redis", "resource": f"snapshot:{intent.device_id}"}],
        )
