from typing import Protocol

from app.query.contracts import Answer, ExtractedIntent, Handler, RequestContext
from app.query.extract import KeywordIntentExtractor
from app.query.handlers import AlarmDetail, DeviceInventory, GlobalOverview, MetricHandler


class _Extractor(Protocol):
    async def extract(self, question: str) -> ExtractedIntent: ...


class QueryOrchestrator:
    def __init__(self, extractor: "_Extractor | None" = None) -> None:
        self.extractor: _Extractor = extractor or KeywordIntentExtractor()
        self.handlers: list[Handler] = [
            GlobalOverview(),
            DeviceInventory(),
            AlarmDetail(),
            MetricHandler(),
        ]

    async def ask(self, question: str, ctx: RequestContext) -> Answer:
        intent = await self.extractor.extract(question)
        for handler in self.handlers:
            if await handler.can_handle(intent):
                return await handler.handle(intent, ctx)
        return Answer("I could not map that question to a supported fleet query.")
