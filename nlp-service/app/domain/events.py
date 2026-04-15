"""
NLP Context - Domain Events

Events published by the NLP aggregate to notify other bounded contexts.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from app.domain.value_objects import Resolution


@dataclass(frozen=True)
class BookingIntentResolved:
    """Published when a query is successfully interpreted into a booking intent."""
    query_id: str
    user_id: str
    resolution: Resolution
    occurred_at: datetime

    def to_message_payload(self) -> dict:
        """Serialise to RabbitMQ message format (ACL - outbound translation)."""
        booking_data = self.resolution.to_booking_request()
        return {
            "eventType": "BookingIntentResolved",
            "queryId": self.query_id,
            "userId": self.user_id,
            "intent": booking_data["intent"],
            "confidence": booking_data["confidence"],
            "entities": {
                "facility": booking_data["facility"],
                "date": booking_data["date"],
                "time": booking_data["time"],
                "duration": booking_data["duration"],
            },
            "occurredAt": self.occurred_at.isoformat(),
        }


@dataclass(frozen=True)
class QueryInterpretationFailed:
    """Published when a query cannot be interpreted."""
    query_id: str
    reason: str
    occurred_at: datetime

    def to_message_payload(self) -> dict:
        """Serialise to RabbitMQ message format."""
        return {
            "eventType": "QueryInterpretationFailed",
            "queryId": self.query_id,
            "reason": self.reason,
            "occurredAt": self.occurred_at.isoformat(),
        }
