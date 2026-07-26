import json

from fastapi import APIRouter, Request

from app.auth.security import verify_webhook
from app.ingest.parse import EventParse
from app.ingest.write import write_event

router = APIRouter(tags=["webhooks"])


@router.post("/webhooks/thingsboard", status_code=202)
async def thingsboard_webhook(request: Request) -> dict[str, bool]:
    body = await verify_webhook(request, request.app.state.settings)
    event = EventParse.from_payload(json.loads(body))
    async with request.app.state.session_factory() as session:
        inserted = await write_event(session, event)
    return {"accepted": inserted}
