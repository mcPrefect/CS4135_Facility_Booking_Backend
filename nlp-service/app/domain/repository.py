"""
NLP Context - Repository Interface

this ia  the abstract interface for NLPQuery persistence. Infrastructure layer provides
the concrete implementation (PostgreSQL).
"""

from abc import ABC, abstractmethod
from typing import Optional, List

from app.domain.nlp_query import NLPQuery


class NLPQueryRepository(ABC):
    """Repository interface for NLPQuery aggregate persistence."""

    @abstractmethod
    async def save(self, query: NLPQuery) -> NLPQuery:
        """Persist an NLPQuery aggregate."""
        ...

    @abstractmethod
    async def find_by_id(self, query_id: str) -> Optional[NLPQuery]:
        """Retrieve an NLPQuery by its ID."""
        ...

    @abstractmethod
    async def find_by_user_id(self, user_id: str, limit: int = 20) -> List[NLPQuery]:
        """Retrieve queries for a given user."""
        ...
