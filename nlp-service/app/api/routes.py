"""
NLP Context - API Layer (FastAPI Routes)

REST endpoints for the NLP service.
Endpoint: POST /api/nlp/query
"""

from __future__ import annotations

from pydantic import BaseModel, Field
from fastapi import APIRouter, HTTPException, Header
from typing import Optional, List

from app.application.service import QueryInterpretationService, QueryNotFoundException
from app.domain.value_objects import QueryStatus

router = APIRouter(prefix="/api/nlp", tags=["NLP"])

# Will be injected from main.py
_service: Optional[QueryInterpretationService] = None


def set_service(service: QueryInterpretationService):
    global _service
    _service = service


# --- Request / Response Schemas ---

class QueryRequest(BaseModel):
    rawText: str = Field(..., min_length=1, max_length=1000, description="Natural language booking query")


class EntityResponse(BaseModel):
    entityType: str
    value: str
    rawSpan: str


class IntentResponse(BaseModel):
    type: str
    confidence: float


class ResolutionResponse(BaseModel):
    intent: IntentResponse
    entities: List[EntityResponse]
    resolvedAt: str


class QueryResponse(BaseModel):
    queryId: str
    status: str
    resolution: Optional[ResolutionResponse] = None
    error: Optional[str] = None


# --- Endpoints ---

@router.post("/query", response_model=QueryResponse, status_code=200)
async def interpret_query(
    request: QueryRequest,
    x_user_id: str = Header(default="anonymous", alias="X-User-Id"),
):
    """
    Interpret a natural language booking query.

    The X-User-Id header is set by the API Gateway after JWT validation.
    """
    if _service is None:
        raise HTTPException(status_code=503, detail="Service not initialised")

    query = await _service.interpret(user_id=x_user_id, raw_text=request.rawText)

    response = QueryResponse(
        queryId=query.query_id,
        status=query.status.value,
    )

    if query.status == QueryStatus.RESOLVED and query.resolution:
        response.resolution = ResolutionResponse(
            intent=IntentResponse(
                type=query.resolution.intent.type.value,
                confidence=query.resolution.intent.confidence.value,
            ),
            entities=[
                EntityResponse(
                    entityType=e.entity_type.value,
                    value=e.value,
                    rawSpan=e.raw_span,
                )
                for e in query.resolution.entities
            ],
            resolvedAt=query.resolution.resolved_at.isoformat(),
        )

    if query.status == QueryStatus.FAILED:
        response.error = query.failure_reason

    return response


@router.get("/query/{query_id}", response_model=QueryResponse)
async def get_query(query_id: str):
    """Retrieve a previously submitted query by ID."""
    if _service is None:
        raise HTTPException(status_code=503, detail="Service not initialised")

    try:
        query = await _service.get_query(query_id)
    except QueryNotFoundException:
        raise HTTPException(status_code=404, detail=f"Query {query_id} not found")

    response = QueryResponse(
        queryId=query.query_id,
        status=query.status.value,
    )

    if query.status == QueryStatus.RESOLVED and query.resolution:
        response.resolution = ResolutionResponse(
            intent=IntentResponse(
                type=query.resolution.intent.type.value,
                confidence=query.resolution.intent.confidence.value,
            ),
            entities=[
                EntityResponse(
                    entityType=e.entity_type.value,
                    value=e.value,
                    rawSpan=e.raw_span,
                )
                for e in query.resolution.entities
            ],
            resolvedAt=query.resolution.resolved_at.isoformat(),
        )

    if query.status == QueryStatus.FAILED:
        response.error = query.failure_reason

    return response


@router.get("/health")
async def health_check():
    """Health check endpoint for service discovery."""
    return {"status": "UP", "service": "nlp-service"}
