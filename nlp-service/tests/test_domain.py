"""
Unit tests for the NLP bounded context domain layer.

Tests cover value object invariants, aggregate state machine,
and domain event publishing.
"""

import pytest
from datetime import datetime, timezone

from app.domain.value_objects import (
    Confidence,
    Intent,
    IntentType,
    ExtractedEntity,
    EntityType,
    Resolution,
    QueryStatus,
)
from app.domain.nlp_query import NLPQuery, IllegalStateError
from app.domain.events import BookingIntentResolved, QueryInterpretationFailed


# --- Value Object Tests ---


class TestConfidence:
    """INV-3: Confidence must be between 0.0 and 1.0 inclusive."""

    def test_valid_confidence(self):
        c = Confidence(0.94)
        assert c.value == 0.94

    def test_zero_confidence(self):
        c = Confidence(0.0)
        assert c.value == 0.0

    def test_max_confidence(self):
        c = Confidence(1.0)
        assert c.value == 1.0

    def test_negative_confidence_raises(self):
        with pytest.raises(ValueError):
            Confidence(-0.1)

    def test_above_one_raises(self):
        with pytest.raises(ValueError):
            Confidence(1.1)

    def test_high_confidence_threshold(self):
        assert Confidence(0.8).is_high_confidence(0.6) is True
        assert Confidence(0.4).is_high_confidence(0.6) is False


class TestResolution:
    """INV-1: Must contain exactly one Intent. INV-2: Must contain at least one entity."""

    def _make_intent(self, confidence=0.9):
        return Intent(
            type=IntentType.CREATE_BOOKING,
            confidence=Confidence(confidence),
        )

    def _make_entity(self):
        return ExtractedEntity(
            entity_type=EntityType.FACILITY,
            value="Sports Hall",
            raw_span="sports hall",
        )

    def test_valid_resolution(self):
        r = Resolution(
            intent=self._make_intent(),
            entities=(self._make_entity(),),
            resolved_at=datetime.now(timezone.utc),
        )
        assert r.intent.type == IntentType.CREATE_BOOKING
        assert len(r.entities) == 1

    def test_null_intent_raises(self):
        with pytest.raises(ValueError, match="exactly one Intent"):
            Resolution(
                intent=None,
                entities=(self._make_entity(),),
                resolved_at=datetime.now(timezone.utc),
            )

    def test_empty_entities_raises(self):
        with pytest.raises(ValueError, match="at least one"):
            Resolution(
                intent=self._make_intent(),
                entities=(),
                resolved_at=datetime.now(timezone.utc),
            )

    def test_to_booking_request(self):
        r = Resolution(
            intent=self._make_intent(0.94),
            entities=(
                self._make_entity(),
                ExtractedEntity(EntityType.DATE, "2026-04-03", "Friday"),
                ExtractedEntity(EntityType.TIME, "15:00", "3pm"),
            ),
            resolved_at=datetime.now(timezone.utc),
        )
        dto = r.to_booking_request()
        assert dto["intent"] == "CREATE_BOOKING"
        assert dto["confidence"] == 0.94
        assert dto["facility"] == "Sports Hall"
        assert dto["date"] == "2026-04-03"
        assert dto["time"] == "15:00"


class TestExtractedEntity:

    def test_valid_entity(self):
        e = ExtractedEntity(EntityType.FACILITY, "Sports Hall", "sports hall")
        assert e.is_valid() is True

    def test_empty_value_invalid(self):
        e = ExtractedEntity(EntityType.FACILITY, "", "sports hall")
        assert e.is_valid() is False


# --- Aggregate Tests ---


class TestNLPQuery:
    """Tests for aggregate root state machine and invariants."""

    def _make_resolution(self):
        return Resolution(
            intent=Intent(IntentType.CREATE_BOOKING, Confidence(0.94)),
            entities=(
                ExtractedEntity(EntityType.FACILITY, "Sports Hall", "sports hall"),
            ),
            resolved_at=datetime.now(timezone.utc),
        )

    def test_create_query(self):
        q = NLPQuery.create(user_id="user-1", raw_text="Book me a room")
        assert q.status == QueryStatus.PENDING
        assert q.user_id == "user-1"
        assert q.raw_text == "Book me a room"
        assert q.resolution is None

    def test_interpret_success(self):
        q = NLPQuery.create(user_id="user-1", raw_text="Book the sports hall")
        resolution = self._make_resolution()
        q.interpret(resolution)

        assert q.status == QueryStatus.RESOLVED
        assert q.resolution is not None
        assert q.resolution.intent.type == IntentType.CREATE_BOOKING

    def test_interpret_publishes_event(self):
        q = NLPQuery.create(user_id="user-1", raw_text="Book the sports hall")
        q.interpret(self._make_resolution())

        events = q.collect_events()
        assert len(events) == 1
        assert isinstance(events[0], BookingIntentResolved)
        assert events[0].query_id == q.query_id

    def test_mark_failed(self):
        q = NLPQuery.create(user_id="user-1", raw_text="asdfghjkl")
        q.mark_failed("Unable to parse")

        assert q.status == QueryStatus.FAILED
        assert q.failure_reason == "Unable to parse"

    def test_mark_failed_publishes_event(self):
        q = NLPQuery.create(user_id="user-1", raw_text="gibberish")
        q.mark_failed("Low confidence")

        events = q.collect_events()
        assert len(events) == 1
        assert isinstance(events[0], QueryInterpretationFailed)

    def test_cannot_resolve_twice(self):
        """INV-5: RESOLVED -> RESOLVED is not allowed."""
        q = NLPQuery.create(user_id="user-1", raw_text="Book a room")
        q.interpret(self._make_resolution())

        with pytest.raises(IllegalStateError):
            q.interpret(self._make_resolution())

    def test_cannot_fail_after_resolved(self):
        """INV-5: RESOLVED -> FAILED is not allowed."""
        q = NLPQuery.create(user_id="user-1", raw_text="Book a room")
        q.interpret(self._make_resolution())

        with pytest.raises(IllegalStateError):
            q.mark_failed("Too late")

    def test_cannot_resolve_after_failed(self):
        """INV-5: FAILED -> RESOLVED is not allowed."""
        q = NLPQuery.create(user_id="user-1", raw_text="Book a room")
        q.mark_failed("Bad input")

        with pytest.raises(IllegalStateError):
            q.interpret(self._make_resolution())

    def test_cannot_interpret_with_null_resolution(self):
        """INV-4: Cannot resolve without a valid Resolution."""
        q = NLPQuery.create(user_id="user-1", raw_text="Book a room")

        with pytest.raises(ValueError):
            q.interpret(None)

    def test_collect_events_clears_list(self):
        q = NLPQuery.create(user_id="user-1", raw_text="Book a room")
        q.interpret(self._make_resolution())

        events_1 = q.collect_events()
        events_2 = q.collect_events()

        assert len(events_1) == 1
        assert len(events_2) == 0


# --- Domain Event Tests ---


class TestDomainEvents:

    def test_booking_intent_resolved_payload(self):
        resolution = Resolution(
            intent=Intent(IntentType.CREATE_BOOKING, Confidence(0.94)),
            entities=(
                ExtractedEntity(EntityType.FACILITY, "Sports Hall", "sports hall"),
                ExtractedEntity(EntityType.DATE, "2026-04-03", "Friday"),
            ),
            resolved_at=datetime(2026, 4, 1, 12, 0, 0, tzinfo=timezone.utc),
        )
        event = BookingIntentResolved(
            query_id="q-1",
            user_id="u-1",
            resolution=resolution,
            occurred_at=datetime(2026, 4, 1, 12, 0, 0, tzinfo=timezone.utc),
        )
        payload = event.to_message_payload()

        assert payload["eventType"] == "BookingIntentResolved"
        assert payload["queryId"] == "q-1"
        assert payload["intent"] == "CREATE_BOOKING"
        assert payload["confidence"] == 0.94
        assert payload["entities"]["facility"] == "Sports Hall"
        assert payload["entities"]["date"] == "2026-04-03"

    def test_interpretation_failed_payload(self):
        event = QueryInterpretationFailed(
            query_id="q-2",
            reason="Low confidence",
            occurred_at=datetime(2026, 4, 1, 12, 0, 0, tzinfo=timezone.utc),
        )
        payload = event.to_message_payload()

        assert payload["eventType"] == "QueryInterpretationFailed"
        assert payload["reason"] == "Low confidence"
