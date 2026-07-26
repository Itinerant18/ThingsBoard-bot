from app.query.contracts import Answer, Handler, RequestContext
from app.query.extract import LlmIntentExtractor
from app.query.handlers import AlarmDetail, DeviceInventory, GlobalOverview, SubsystemStatus


class QueryOrchestrator:
    def __init__(self) -> None:
        self.extractor = LlmIntentExtractor()
        self.handlers: list[Handler] = [
            GlobalOverview(),
            DeviceInventory(),
            AlarmDetail(),
            SubsystemStatus(),
        ]

    async def ask(self, question: str, ctx: RequestContext) -> Answer:
        intent = await self.extractor.extract(question)
        for handler in self.handlers:
            if await handler.can_handle(intent):
                return await handler.handle(intent, ctx)
        return Answer("I could not map that question to a supported fleet query.")
