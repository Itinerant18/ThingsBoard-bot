from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.api import admin, chat, data, health, webhooks
from app.cache.redis import create_redis
from app.clients.thingsboard import ThingsBoardClient
from app.config import Settings, get_settings
from app.db.session import build_engine
from app.db.timescale import initialize_timescale
from app.query.orchestrate import QueryOrchestrator


def create_app(settings: Settings | None = None) -> FastAPI:
    """Create the FastAPI app; tests pass custom settings."""
    test_settings = settings or get_settings()

    @asynccontextmanager
    async def test_lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.settings = test_settings
        engine = build_engine(test_settings)
        app.state.session_factory = async_sessionmaker(engine, expire_on_commit=False)
        if test_settings.timescale_init_enabled:
            await initialize_timescale(engine)
        app.state.redis = await create_redis(test_settings.redis_url)
        app.state.tb = ThingsBoardClient(test_settings)
        app.state.orchestrator = QueryOrchestrator()
        try:
            yield
        finally:
            await app.state.tb.close()
            await app.state.redis.aclose()
            await engine.dispose()

    app = FastAPI(title="ThingsBoard IoT Chatbot", version="0.1.0", lifespan=test_lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=test_settings.origins,
        allow_credentials=True,
        allow_methods=["GET", "POST"],
        allow_headers=[
            "Authorization",
            "Content-Type",
            "X-Admin-Token",
            "X-HMAC-SHA256",
            "X-Timestamp",
        ],
    )

    @app.middleware("http")
    async def security_headers(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        response = await call_next(request)
        response.headers["Content-Security-Policy"] = f"frame-ancestors {test_settings.frame_ancestors}"
        return response

    app.include_router(health.router)
    app.include_router(chat.router)
    app.include_router(data.router)
    app.include_router(admin.router)
    app.include_router(webhooks.router)

    return app


# Production app
app = create_app()