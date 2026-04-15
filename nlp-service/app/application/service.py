"""
NLP Context - Application Service

QueryInterpretationService orchestrates the interpretation workflow:
1. Creates an NLPQuery aggregate
2. Invokes the ACL to interpret via OpenAI
3. Updates the aggregate state
4. Persists the aggregate
5. Publishes domain events
"""

from __future__ import annotations

import logging

from app.domain.nlp_query import NLPQuery
from app.domain.repository import NLPQueryRepository
from app.infrastructure.openai_translator import (
    OpenAIResponseTranslator,
    InterpretationException,
)
from app.infrastructure.rabbitmq_publisher import RabbitMQPublisher
from app.infrastructure.facility_client import FacilityServiceClient

logger = logging.getLogger(__name__)

MIN_CONFIDENCE_THRESHOLD = 0.6


class QueryInterpretationService:
    """
    Domain Service: orchestrates the NLP interpretation process.

    Keeps the aggregate and infrastructure concerns coordinated while
    the domain model stays free of infrastructure dependencies.
    """

    def __init__(
        self,
        translator: OpenAIResponseTranslator,
        repository: NLPQueryRepository,
        publisher: RabbitMQPublisher,
        facility_client: FacilityServiceClient = None,
    ):
        self.translator = translator
        self.repository = repository
        self.publisher = publisher
        self.facility_client = facility_client

    async def interpret(self, user_id: str, raw_text: str, jwt_token: str = None) -> NLPQuery:
        query = NLPQuery.create(user_id=user_id, raw_text=raw_text)

        try:
            resolution = await self.translator.interpret(raw_text)

            if not resolution.intent.is_high_confidence(MIN_CONFIDENCE_THRESHOLD):
                query.mark_failed(
                    f"Confidence {resolution.intent.confidence.value:.2f} "
                    f"below threshold {MIN_CONFIDENCE_THRESHOLD}"
                )
            else:
                # Attempt to resolve facility name to UUID if client is available
                if self.facility_client and jwt_token:
                    facility_entities = [
                        e for e in resolution.entities
                        if e.entity_type.value == "FACILITY"
                    ]
                    for entity in facility_entities:
                        facility_id = await self.facility_client.resolve_facility_id(
                            entity.value, jwt_token
                        )
                        if facility_id:
                            logger.info(f"Resolved '{entity.value}' to facilityId: {facility_id}")
                        else:
                            logger.warning(f"Could not resolve facility name: '{entity.value}'")

                query.interpret(resolution)

        except InterpretationException as e:
            logger.warning(f"Interpretation failed for query {query.query_id}: {e}")
            query.mark_failed(str(e))

        except Exception as e:
            logger.error(f"Unexpected error interpreting query {query.query_id}: {e}")
            query.mark_failed(f"Internal error: {str(e)}")

        await self.repository.save(query)

        for event in query.collect_events():
            try:
                await self.publisher.publish_event(event)
            except Exception as e:
                logger.error(f"Failed to publish event: {e}")

        return query

    async def get_query(self, query_id: str) -> NLPQuery:
        """Retrieve a query by ID."""
        query = await self.repository.find_by_id(query_id)
        if query is None:
            raise QueryNotFoundException(f"Query {query_id} not found")
        return query

    async def get_user_queries(self, user_id: str, limit: int = 20):
        """Retrieve recent queries for a user."""
        return await self.repository.find_by_user_id(user_id, limit)


class QueryNotFoundException(Exception):
    """Raised when a query cannot be found."""
    pass
