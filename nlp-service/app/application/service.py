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
    ):
        self.translator = translator
        self.repository = repository
        self.publisher = publisher

    async def interpret(self, user_id: str, raw_text: str) -> NLPQuery:
        """
        Full interpretation workflow:
        - Create NLPQuery aggregate
        - Call OpenAI via ACL
        - Validate confidence threshold
        - Persist aggregate
        - Publish domain events
        """
        query = NLPQuery.create(user_id=user_id, raw_text=raw_text)

        try:
            resolution = await self.translator.interpret(raw_text)

            if not resolution.intent.is_high_confidence(MIN_CONFIDENCE_THRESHOLD):
                query.mark_failed(
                    f"Confidence {resolution.intent.confidence.value:.2f} "
                    f"below threshold {MIN_CONFIDENCE_THRESHOLD}"
                )
            else:
                query.interpret(resolution)

        except InterpretationException as e:
            logger.warning(f"Interpretation failed for query {query.query_id}: {e}")
            query.mark_failed(str(e))

        except Exception as e:
            logger.error(f"Unexpected error interpreting query {query.query_id}: {e}")
            query.mark_failed(f"Internal error: {str(e)}")

        # Persist aggregate (single transaction)
        await self.repository.save(query)

        # Publish domain events only after successful persistence
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
