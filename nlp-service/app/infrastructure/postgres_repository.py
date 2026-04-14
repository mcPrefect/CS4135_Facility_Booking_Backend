"""
NLP Context - PostgreSQL Repository Implementation

Concrete implementation of NLPQueryRepository using asyncpg and PostgreSQL.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Optional, List

import asyncpg

from app.domain.nlp_query import NLPQuery
from app.domain.repository import NLPQueryRepository
from app.domain.value_objects import (
    QueryStatus,
    Resolution,
    Intent,
    IntentType,
    Confidence,
    ExtractedEntity,
    EntityType,
)

logger = logging.getLogger(__name__)

CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS nlp_queries (
    query_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    raw_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution JSONB,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_nlp_queries_user_id ON nlp_queries(user_id);
CREATE INDEX IF NOT EXISTS idx_nlp_queries_status ON nlp_queries(status);
"""


class PostgresNLPQueryRepository(NLPQueryRepository):
    """PostgreSQL-backed repository for NLPQuery aggregates."""

    def __init__(self, pool: asyncpg.Pool):
        self.pool = pool

    async def initialise(self):
        """Create the table if it doesn't exist."""
        async with self.pool.acquire() as conn:
            await conn.execute(CREATE_TABLE_SQL)
        logger.info("NLP queries table initialised")

    async def save(self, query: NLPQuery) -> NLPQuery:
        """Persist an NLPQuery aggregate in a single transaction."""
        resolution_json = None
        if query.resolution:
            resolution_json = json.dumps(self._serialise_resolution(query.resolution))

        async with self.pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO nlp_queries (query_id, user_id, raw_text, status, resolution, failure_reason, created_at)
                VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7)
                ON CONFLICT (query_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    resolution = EXCLUDED.resolution,
                    failure_reason = EXCLUDED.failure_reason
                """,
                query.query_id,
                query.user_id,
                query.raw_text,
                query.status.value,
                resolution_json,
                query.failure_reason,
                query.created_at,
            )
        return query

    async def find_by_id(self, query_id: str) -> Optional[NLPQuery]:
        """Retrieve an NLPQuery by its ID."""
        async with self.pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT * FROM nlp_queries WHERE query_id = $1", query_id
            )
        if row is None:
            return None
        return self._row_to_entity(row)

    async def find_by_user_id(self, user_id: str, limit: int = 20) -> List[NLPQuery]:
        """Retrieve queries for a given user, most recent first."""
        async with self.pool.acquire() as conn:
            rows = await conn.fetch(
                "SELECT * FROM nlp_queries WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2",
                user_id,
                limit,
            )
        return [self._row_to_entity(row) for row in rows]

    def _row_to_entity(self, row: asyncpg.Record) -> NLPQuery:
        """Reconstruct an NLPQuery aggregate from a database row."""
        resolution = None
        if row["resolution"]:
            resolution_data = (
                json.loads(row["resolution"])
                if isinstance(row["resolution"], str)
                else row["resolution"]
            )
            resolution = self._deserialise_resolution(resolution_data)

        return NLPQuery(
            query_id=row["query_id"],
            user_id=row["user_id"],
            raw_text=row["raw_text"],
            status=QueryStatus(row["status"]),
            resolution=resolution,
            failure_reason=row["failure_reason"],
            created_at=row["created_at"],
        )

    @staticmethod
    def _serialise_resolution(resolution: Resolution) -> dict:
        return {
            "intent": {
                "type": resolution.intent.type.value,
                "confidence": resolution.intent.confidence.value,
            },
            "entities": [
                {
                    "entityType": e.entity_type.value,
                    "value": e.value,
                    "rawSpan": e.raw_span,
                }
                for e in resolution.entities
            ],
            "resolvedAt": resolution.resolved_at.isoformat(),
        }

    @staticmethod
    def _deserialise_resolution(data: dict) -> Resolution:
        intent = Intent(
            type=IntentType(data["intent"]["type"]),
            confidence=Confidence(data["intent"]["confidence"]),
        )
        entities = tuple(
            ExtractedEntity(
                entity_type=EntityType(e["entityType"]),
                value=e["value"],
                raw_span=e["rawSpan"],
            )
            for e in data["entities"]
        )
        resolved_at = datetime.fromisoformat(data["resolvedAt"])
        return Resolution(intent=intent, entities=entities, resolved_at=resolved_at)
