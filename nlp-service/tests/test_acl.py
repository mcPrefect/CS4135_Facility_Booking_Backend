"""
Unit tests for the Anti-Corruption Layer (OpenAI response translator).

Tests verify that the ACL correctly translates raw JSON into domain objects
and rejects malformed or invalid responses.
"""

import pytest
from datetime import datetime, timezone

from app.infrastructure.openai_translator import (
    OpenAIResponseTranslator,
    InterpretationException,
)
from app.domain.value_objects import IntentType, EntityType


class TestOpenAIResponseTranslator:
    """Tests for the _translate method (ACL logic) without calling OpenAI."""

    def setup_method(self):
        # API key doesn't matter for _translate tests
        self.translator = OpenAIResponseTranslator(api_key="test-key")

    def test_valid_response(self):
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 0.94,
            "entities": [
                {"type": "FACILITY", "value": "Sports Hall", "raw": "sports hall"},
                {"type": "DATE", "value": "2026-04-03", "raw": "Friday"},
                {"type": "TIME", "value": "15:00", "raw": "3pm"}
            ]
        }'''
        resolution = self.translator._translate(raw)

        assert resolution.intent.type == IntentType.CREATE_BOOKING
        assert resolution.intent.confidence.value == 0.94
        assert len(resolution.entities) == 3
        assert resolution.entities[0].entity_type == EntityType.FACILITY
        assert resolution.entities[0].value == "Sports Hall"

    def test_response_with_markdown_fences(self):
        raw = '''```json
{
    "intent": "CHECK_AVAILABILITY",
    "confidence": 0.85,
    "entities": [
        {"type": "FACILITY", "value": "Computer Lab", "raw": "computer lab"}
    ]
}
```'''
        resolution = self.translator._translate(raw)
        assert resolution.intent.type == IntentType.CHECK_AVAILABILITY

    def test_invalid_json_raises(self):
        with pytest.raises(InterpretationException, match="invalid JSON"):
            self.translator._translate("not json at all")

    def test_unknown_intent_type_raises(self):
        raw = '''{
            "intent": "UNKNOWN_INTENT",
            "confidence": 0.9,
            "entities": [
                {"type": "FACILITY", "value": "Room 1", "raw": "room 1"}
            ]
        }'''
        with pytest.raises(InterpretationException, match="Unrecognised intent"):
            self.translator._translate(raw)

    def test_no_entities_raises(self):
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 0.9,
            "entities": []
        }'''
        with pytest.raises(InterpretationException, match="No valid entities"):
            self.translator._translate(raw)

    def test_invalid_confidence_raises(self):
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 1.5,
            "entities": [
                {"type": "FACILITY", "value": "Room 1", "raw": "room 1"}
            ]
        }'''
        with pytest.raises(InterpretationException, match="Invalid confidence"):
            self.translator._translate(raw)

    def test_unknown_entity_types_skipped(self):
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 0.9,
            "entities": [
                {"type": "FACILITY", "value": "Sports Hall", "raw": "sports hall"},
                {"type": "UNKNOWN_TYPE", "value": "something", "raw": "something"}
            ]
        }'''
        resolution = self.translator._translate(raw)
        assert len(resolution.entities) == 1
        assert resolution.entities[0].entity_type == EntityType.FACILITY

    def test_empty_value_entities_skipped(self):
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 0.9,
            "entities": [
                {"type": "FACILITY", "value": "Sports Hall", "raw": "sports hall"},
                {"type": "DATE", "value": "", "raw": ""}
            ]
        }'''
        resolution = self.translator._translate(raw)
        assert len(resolution.entities) == 1

    def test_all_entities_invalid_raises(self):
        """If all entities are invalid after filtering, should raise."""
        raw = '''{
            "intent": "CREATE_BOOKING",
            "confidence": 0.9,
            "entities": [
                {"type": "UNKNOWN", "value": "x", "raw": "x"},
                {"type": "DATE", "value": "", "raw": ""}
            ]
        }'''
        with pytest.raises(InterpretationException, match="No valid entities"):
            self.translator._translate(raw)
