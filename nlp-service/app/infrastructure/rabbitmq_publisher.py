"""
NLP Context - RabbitMQ Event Publisher

Publishes domain events to RabbitMQ for consumption by other bounded contexts.
This is part of the outbound ACL (Java/Spring ecosystem boundary).
"""

from __future__ import annotations

import json
import logging

import aio_pika

from app.domain.events import BookingIntentResolved, QueryInterpretationFailed

logger = logging.getLogger(__name__)

EXCHANGE_NAME = "nlp.events"
BOOKING_ROUTING_KEY = "nlp.booking.intent.resolved"
FAILURE_ROUTING_KEY = "nlp.query.interpretation.failed"


class RabbitMQPublisher:
    """Publishes domain events to RabbitMQ."""

    def __init__(self, connection_url: str):
        self.connection_url = connection_url
        self._connection = None
        self._channel = None
        self._exchange = None

    async def connect(self):
        """Establish connection and declare the exchange."""
        self._connection = await aio_pika.connect_robust(self.connection_url)
        self._channel = await self._connection.channel()
        self._exchange = await self._channel.declare_exchange(
            EXCHANGE_NAME,
            aio_pika.ExchangeType.TOPIC,
            durable=True,
        )
        logger.info(f"Connected to RabbitMQ, exchange '{EXCHANGE_NAME}' declared")

    async def disconnect(self):
        """Close the connection."""
        if self._connection and not self._connection.is_closed:
            await self._connection.close()
            logger.info("Disconnected from RabbitMQ")

    async def publish_event(self, event) -> None:
        """Publish a domain event to the appropriate routing key."""
        if self._exchange is None:
            logger.error("RabbitMQ not connected, cannot publish event")
            return

        if isinstance(event, BookingIntentResolved):
            routing_key = BOOKING_ROUTING_KEY
        elif isinstance(event, QueryInterpretationFailed):
            routing_key = FAILURE_ROUTING_KEY
        else:
            logger.warning(f"Unknown event type: {type(event)}")
            return

        payload = event.to_message_payload()
        message = aio_pika.Message(
            body=json.dumps(payload).encode("utf-8"),
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        )

        await self._exchange.publish(message, routing_key=routing_key)
        logger.info(
            f"Published {payload['eventType']} to {routing_key} "
            f"(queryId={payload['queryId']})"
        )
