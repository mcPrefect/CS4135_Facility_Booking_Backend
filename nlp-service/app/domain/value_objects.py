"""
NLP Context - Value Objects

Immutable domain objects without identity. These are equal by value, not by reference.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List, Optional


class IntentType(str, Enum):
    """Supported intent types for natural language queries."""
    CREATE_BOOKING = "CREATE_BOOKING"
    CHECK_AVAILABILITY = "CHECK_AVAILABILITY"
    CANCEL_BOOKING = "CANCEL_BOOKING"
    QUERY_STATUS = "QUERY_STATUS"


class EntityType(str, Enum):
    """Types of entities that can be extracted from a query."""
    FACILITY = "FACILITY"
    DATE = "DATE"
    TIME = "TIME"
    DURATION = "DURATION"


class QueryStatus(str, Enum):
    """Lifecycle state of an NLP query."""
    PENDING = "PENDING"
    RESOLVED = "RESOLVED"
    FAILED = "FAILED"


@dataclass(frozen=True)
class Confidence:
    """
    Value object representing the certainty of an intent classification.

    Invariant INV-3: Confidence must be between 0.0 and 1.0 inclusive.
    """
    value: float

    def __post_init__(self):
        if not (0.0 <= self.value <= 1.0):
            raise ValueError(
                f"Confidence must be between 0.0 and 1.0, got {self.value}"
            )

    def is_high_confidence(self, threshold: float = 0.6) -> bool:
        return self.value >= threshold


@dataclass(frozen=True)
class Intent:
    """
    Value object representing the classified purpose of a query.

    Invariant INV-6: Intent type must be a recognised IntentType enum value.
    """
    type: IntentType
    confidence: Confidence

    def is_high_confidence(self, threshold: float = 0.6) -> bool:
        return self.confidence.is_high_confidence(threshold)


@dataclass(frozen=True)
class ExtractedEntity:
    """
    Value object representing a structured piece of data parsed from a query.
    """
    entity_type: EntityType
    value: str
    raw_span: str

    def is_valid(self) -> bool:
        return bool(self.value and self.value.strip())


@dataclass(frozen=True)
class Resolution:
    """
    Composite value object: the complete structured output of an NLP interpretation.

    Invariant INV-1: A Resolution must contain exactly one Intent.
    Invariant INV-2: A Resolution must contain at least one ExtractedEntity.
    """
    intent: Intent
    entities: tuple  # tuple of ExtractedEntity for immutability
    resolved_at: datetime

    def __post_init__(self):
        if self.intent is None:
            raise ValueError("Resolution must contain exactly one Intent")
        if not self.entities or len(self.entities) == 0:
            raise ValueError(
                "Resolution must contain at least one ExtractedEntity"
            )

    def get_entity(self, entity_type: EntityType) -> Optional[ExtractedEntity]:
        """Retrieve the first entity matching the given type."""
        for entity in self.entities:
            if entity.entity_type == entity_type:
                return entity
        return None

    def to_booking_request(self) -> dict:
        """Translate the resolution into a BookingDTO for downstream consumption."""
        facility = self.get_entity(EntityType.FACILITY)
        date = self.get_entity(EntityType.DATE)
        time = self.get_entity(EntityType.TIME)
        duration = self.get_entity(EntityType.DURATION)

        return {
            "intent": self.intent.type.value,
            "confidence": self.intent.confidence.value,
            "facility": facility.value if facility else None,
            "date": date.value if date else None,
            "time": time.value if time else None,
            "duration": duration.value if duration else None,
        }
