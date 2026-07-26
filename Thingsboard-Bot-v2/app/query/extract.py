import re

from app.query.contracts import ExtractedIntent


class LlmIntentExtractor:
    """Deterministic classifier first; this is intentionally safe without an LLM key."""

    async def extract(self, question: str) -> ExtractedIntent:
        text = question.lower()
        intent = "global_overview"
        if any(word in text for word in ("inventory", "how many device", "list device")):
            intent = "device_inventory"
        elif any(word in text for word in ("alarm", "alert", "fault")):
            intent = "alarm_detail"
        elif any(word in text for word in ("subsystem", "ups", "hvac", "battery", "status")):
            intent = "subsystem_status"
        device = re.search(r"(?:device|asset)\s+([\w-]+)", question, re.IGNORECASE)
        return ExtractedIntent(
            name=intent, device_id=device.group(1) if device else None, raw_question=question
        )
