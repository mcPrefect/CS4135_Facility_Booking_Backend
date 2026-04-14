"""
NLP Context - Aggregate Root

NLPQuery is the aggregate root for the NLP bounded context.
It encapsulates the full lifecycle of a natural language interpretation request.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Optional, List

from app.domain.value_objects import (
    QueryStatus,
    Resolution,
)
from app.domain.events import BookingIntentResolved, QueryInterpretationFailed


class NLPQuery:
    """
    Aggregate Root for the NLP bounded context.

    Invariant INV-4: Cannot transition to RESOLVED without a valid Resolution.
    Invariant INV-5: Status transitions are one-directional:
                     PENDING -> RESOLVED or PENDING -> FAILED.
    """

    def __init__(
        self,
        query_id: str,
        user_id: str,
        raw_text: str,
        status: QueryStatus = QueryStatus.PENDING,
        resolution: Optional[Resolution] = None,
        created_at: Optional[datetime] = None,
        failure_reason: Optional[str] = None,
    ):
        self.query_id = query_id
        self.user_id = user_id
        self.raw_text = raw_text
        self.status = status
        self.resolution = resolution
        self.created_at = created_at or datetime.now(timezone.utc)
        self.failure_reason = failure_reason
        self._domain_events: List = []

    @staticmethod
    def create(user_id: str, raw_text: str) -> NLPQuery:
        """Factory method to create a new NLPQuery in PENDING state."""
        return NLPQuery(
            query_id=str(uuid.uuid4()),
            user_id=user_id,
            raw_text=raw_text,
            status=QueryStatus.PENDING,
        )

    def interpret(self, resolution: Resolution) -> None:
        """
        Resolve the query with a successful interpretation.

        INV-4: Rejects null or invalid resolutions.
        INV-5: Only allowed from PENDING state.
        """
        if self.status != QueryStatus.PENDING:
            raise IllegalStateError(
                f"Cannot resolve query in {self.status.value} state. "
                f"Only PENDING queries can be resolved."
            )
        if resolution is None:
            raise ValueError("Resolution cannot be null")

        self.resolution = resolution
        self.status = QueryStatus.RESOLVED

        self._domain_events.append(
            BookingIntentResolved(
                query_id=self.query_id,
                user_id=self.user_id,
                resolution=resolution,
                occurred_at=datetime.now(timezone.utc),
            )
        )

    def mark_failed(self, reason: str) -> None:
        """
        Mark the query as failed.

        INV-5: Only allowed from PENDING state.
        """
        if self.status != QueryStatus.PENDING:
            raise IllegalStateError(
                f"Cannot fail query in {self.status.value} state. "
                f"Only PENDING queries can be marked as failed."
            )

        self.failure_reason = reason
        self.status = QueryStatus.FAILED

        self._domain_events.append(
            QueryInterpretationFailed(
                query_id=self.query_id,
                reason=reason,
                occurred_at=datetime.now(timezone.utc),
            )
        )

    def collect_events(self) -> List:
        """Collect and clear pending domain events."""
        events = list(self._domain_events)
        self._domain_events.clear()
        return events


class IllegalStateError(Exception):
    """Raised when an operation violates the aggregate's state machine."""
    pass
