"""
NLP Context - Anti-Corruption Layer (OpenAI Boundary)

Translates the unstructured OpenAI API responses into clean domain value objects.
This is the inbound ACL that protects the domain model from external API instability.
"""

from __future__ import annotations

import json
import logging
import re
from datetime import datetime, timezone

import pybreaker
from openai import AsyncOpenAI

from app.domain.value_objects import (
    Intent,
    IntentType,
    Confidence,
    ExtractedEntity,
    EntityType,
    Resolution,
)

logger = logging.getLogger(__name__)

# Circuit breaker: opens after 3 consecutive OpenAI failures,
# resets after 30 seconds. Protects the service from cascading
# failures when the external OpenAI API is unavailable.
openai_breaker = pybreaker.CircuitBreaker(
    fail_max=3,
    reset_timeout=30,
    name="openai_circuit_breaker",
)

SYSTEM_PROMPT = """You are a university facility booking assistant. Parse the user's natural language input and extract booking intent and entities.

Respond ONLY with valid JSON in this exact format:
{{
  "intent": "CREATE_BOOKING" | "CHECK_AVAILABILITY" | "CANCEL_BOOKING" | "QUERY_STATUS",
  "confidence": <float between 0.0 and 1.0>,
  "entities": [
    {{"type": "FACILITY", "value": "<normalised facility name>", "raw": "<original text>"}},
    {{"type": "DATE", "value": "<YYYY-MM-DD>", "raw": "<original text>"}},
    {{"type": "TIME", "value": "<HH:MM in 24h format>", "raw": "<original text>"}},
    {{"type": "DURATION", "value": "<duration in minutes>", "raw": "<original text>"}}
  ]
}}

Rules:
- Convert relative dates (e.g., "Friday", "tomorrow") to absolute ISO dates based on today's date.
- Convert times to 24-hour format (e.g., "3pm" -> "15:00").
- Normalise facility names to title case (e.g., "sports hall" -> "Sports Hall", "cs3004Bb" -> "CS3004B").
- Only include entities that are explicitly mentioned or clearly implied.
- If the intent is unclear, set confidence below 0.5.
- Today's date is: {today}
"""


class OpenAIResponseTranslator:
    """
    Anti-Corruption Layer: translates OpenAI completions into domain value objects.
    Includes a circuit breaker to handle OpenAI API unavailability gracefully.
    """

    def __init__(self, api_key: str, model: str = "gpt-4o-mini"):
        self.client = AsyncOpenAI(api_key=api_key)
        self.model = model

    async def interpret(self, raw_text: str) -> Resolution:
        """
        Send raw text to OpenAI and translate the response into a Resolution.
        The circuit breaker will open if OpenAI fails 3 consecutive times,
        immediately rejecting requests until the timeout resets.

        Raises InterpretationException if the response cannot be cleanly translated.
        """
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")

        try:
            response = await openai_breaker.call_async(
                self.client.chat.completions.create,
                model=self.model,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT.format(today=today)},
                    {"role": "user", "content": raw_text},
                ],
                temperature=0.1,
                max_tokens=500,
            )

            raw_content = response.choices[0].message.content.strip()
            logger.info(f"OpenAI raw response: {raw_content!r}")

            return self._translate(raw_content)

        except pybreaker.CircuitBreakerError:
            logger.error("Circuit breaker OPEN — OpenAI API unavailable")
            raise InterpretationException(
                "OpenAI service unavailable - circuit breaker open"
            )
        except InterpretationException:
            raise
        except Exception as e:
            logger.error(f"OpenAI API call failed: {e}")
            raise InterpretationException(f"OpenAI API error: {str(e)}")

    def _translate(self, raw_content: str) -> Resolution:
        """Parse and validate the OpenAI JSON response into domain objects."""
        content = re.sub(r"^```(?:json)?\s*", "", raw_content.strip())
        content = re.sub(r"\s*```$", "", content).strip()

        try:
            data = json.loads(content)
        except json.JSONDecodeError as e:
            raise InterpretationException(
                f"OpenAI returned invalid JSON: {str(e)}"
            )

        # Validate and map intent type (INV-6)
        raw_intent = data.get("intent", "").upper()
        try:
            intent_type = IntentType(raw_intent)
        except ValueError:
            raise InterpretationException(
                f"Unrecognised intent type: {raw_intent}"
            )

        # Map confidence (INV-3 enforced in Confidence constructor)
        raw_confidence = data.get("confidence", 0.0)
        try:
            confidence = Confidence(float(raw_confidence))
        except (ValueError, TypeError) as e:
            raise InterpretationException(f"Invalid confidence value: {e}")

        intent = Intent(type=intent_type, confidence=confidence)

        # Map extracted entities
        raw_entities = data.get("entities", [])
        entities = []
        for raw_entity in raw_entities:
            entity_type_str = raw_entity.get("type", "").upper()
            try:
                entity_type = EntityType(entity_type_str)
            except ValueError:
                logger.warning(f"Skipping unknown entity type: {entity_type_str}")
                continue

            entity = ExtractedEntity(
                entity_type=entity_type,
                value=str(raw_entity.get("value", "")),
                raw_span=str(raw_entity.get("raw", "")),
            )
            if entity.is_valid():
                entities.append(entity)

        if not entities:
            raise InterpretationException(
                "No valid entities could be extracted from the query"
            )

        # INV-1 and INV-2 enforced in Resolution constructor
        return Resolution(
            intent=intent,
            entities=tuple(entities),
            resolved_at=datetime.now(timezone.utc),
        )


class InterpretationException(Exception):
    """Raised when the ACL cannot translate an OpenAI response into domain objects."""
    pass