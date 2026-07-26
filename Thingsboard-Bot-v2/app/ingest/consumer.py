import asyncio
import json
import logging

import aio_pika
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.ingest.parse import EventParse
from app.ingest.write import write_event

logger = logging.getLogger(__name__)


async def consume_events(
    url: str, sessions: async_sessionmaker[AsyncSession], queue_name: str = "iot.events"
) -> None:
    connection = await aio_pika.connect_robust(url)
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=20)
    # Declare the DLX so rejected messages have somewhere to go; without it the
    # queue's x-dead-letter-exchange points at nothing and rejects are dropped.
    dlx = await channel.declare_exchange("iot.dlx", aio_pika.ExchangeType.FANOUT, durable=True)
    dlq = await channel.declare_queue("iot.events.dead", durable=True)
    await dlq.bind(dlx)
    queue = await channel.declare_queue(
        queue_name, durable=True, arguments={"x-dead-letter-exchange": "iot.dlx"}
    )
    logger.info("consuming %s", queue_name)
    async with queue.iterator() as messages:
        async for message in messages:
            async with message.process(requeue=False):
                event = EventParse.from_payload(json.loads(message.body))
                async with sessions() as session:
                    await write_event(session, event)


def main() -> None:
    """Run the consumer as a standalone worker: python -m app.ingest.consumer"""
    from app.config import get_settings
    from app.db.session import build_session_factory

    logging.basicConfig(level=logging.INFO)
    settings = get_settings()
    sessions = build_session_factory(settings)
    asyncio.run(consume_events(settings.rabbitmq_url, sessions))


if __name__ == "__main__":
    main()
