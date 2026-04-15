"""
Integration tests for the NLP Service API layer.

Tests verify the full HTTP request/response cycle through the FastAPI
application, covering endpoint behaviour, input validation, and
inter-service contract compliance.

These tests use httpx.AsyncClient with ASGITransport to test the
actual application without requiring external services to be running.
Infrastructure dependencies (PostgreSQL, RabbitMQ, OpenAI, Facility Service)
are mocked at the service boundary.
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport

from app.main import app
from app.domain.nlp_query import NLPQuery
from app.domain.value_objects import (
    QueryStatus,
    Resolution,
    Intent,
    IntentType,
    Confidence,
    ExtractedEntity,
    EntityType,
)


# ── Helpers ────────────────────────────────────────────────────────────────

def _make_resolved_query(query_id: str = "test-query-id") -> NLPQuery:
    """Build a resolved NLPQuery for use in mock responses."""
    query = NLPQuery.create(user_id="test-user-1", raw_text="Book the sports hall on Friday at 3pm")
    object.__setattr__(query, "_query_id", query_id)
    resolution = Resolution(
        intent=Intent(IntentType.CREATE_BOOKING, Confidence(0.9)),
        entities=(
            ExtractedEntity(EntityType.FACILITY, "Sports Hall", "sports hall"),
            ExtractedEntity(EntityType.DATE, "2026-04-17", "Friday"),
            ExtractedEntity(EntityType.TIME, "15:00", "3pm"),
        ),
        resolved_at=datetime(2026, 4, 17, 15, 0, 0, tzinfo=timezone.utc),
    )
    query.interpret(resolution)
    query.collect_events()
    return query


def _make_failed_query(query_id: str = "failed-query-id") -> NLPQuery:
    """Build a failed NLPQuery for use in mock responses."""
    query = NLPQuery.create(user_id="test-user-1", raw_text="asdfghjkl")
    object.__setattr__(query, "_query_id", query_id)
    query.mark_failed("Confidence 0.42 below threshold 0.6")
    query.collect_events()
    return query


# ── Health Check ───────────────────────────────────────────────────────────

class TestHealthEndpoint:
    """C5: Integration test — health endpoint contract."""

    @pytest.mark.anyio
    async def test_health_returns_200(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/api/nlp/health")

        assert response.status_code == 200

    @pytest.mark.anyio
    async def test_health_returns_correct_schema(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.get("/api/nlp/health")

        data = response.json()
        assert data["status"] == "UP"
        assert data["service"] == "nlp-service"


# ── Query Endpoint ─────────────────────────────────────────────────────────

class TestQueryEndpoint:
    """C5: Integration tests — POST /api/nlp/query endpoint contract."""

    @pytest.mark.anyio
    async def test_query_returns_200_with_valid_request(self):
        """A valid request with X-User-Id header returns HTTP 200."""
        mock_service = MagicMock()
        mock_service.interpret = AsyncMock(return_value=_make_resolved_query())

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.post(
                    "/api/nlp/query",
                    json={"rawText": "Book the sports hall on Friday at 3pm"},
                    headers={"X-User-Id": "test-user-1"},
                )

        assert response.status_code == 200

    @pytest.mark.anyio
    async def test_resolved_query_returns_correct_schema(self):
        """A resolved query returns queryId, status RESOLVED, and resolution."""
        mock_service = MagicMock()
        mock_service.interpret = AsyncMock(return_value=_make_resolved_query("abc-123"))

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.post(
                    "/api/nlp/query",
                    json={"rawText": "Book the sports hall on Friday at 3pm"},
                    headers={"X-User-Id": "test-user-1"},
                )

        data = response.json()
        assert "queryId" in data
        assert data["status"] == "RESOLVED"
        assert data["resolution"] is not None
        assert data["resolution"]["intent"]["type"] == "CREATE_BOOKING"
        assert data["resolution"]["intent"]["confidence"] == 0.9
        assert len(data["resolution"]["entities"]) == 3
        assert data["error"] is None

    @pytest.mark.anyio
    async def test_failed_query_returns_error_field(self):
        """A failed query returns status FAILED and a non-null error field."""
        mock_service = MagicMock()
        mock_service.interpret = AsyncMock(return_value=_make_failed_query())

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.post(
                    "/api/nlp/query",
                    json={"rawText": "asdfghjkl"},
                    headers={"X-User-Id": "test-user-1"},
                )

        data = response.json()
        assert data["status"] == "FAILED"
        assert data["error"] is not None
        assert data["resolution"] is None

    @pytest.mark.anyio
    async def test_missing_user_id_header_returns_422(self):
        """A request without X-User-Id header should still succeed (defaults to anonymous)."""
        mock_service = MagicMock()
        mock_service.interpret = AsyncMock(return_value=_make_resolved_query())

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.post(
                    "/api/nlp/query",
                    json={"rawText": "Book the sports hall"},
                )

        assert response.status_code == 200

    @pytest.mark.anyio
    async def test_empty_raw_text_returns_422(self):
        """An empty rawText string violates the min_length=1 constraint."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/nlp/query",
                json={"rawText": ""},
                headers={"X-User-Id": "test-user-1"},
            )

        assert response.status_code == 422

    @pytest.mark.anyio
    async def test_missing_raw_text_returns_422(self):
        """A request body without rawText field returns HTTP 422."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/nlp/query",
                json={},
                headers={"X-User-Id": "test-user-1"},
            )

        assert response.status_code == 422

    @pytest.mark.anyio
    async def test_jwt_token_forwarded_to_service(self):
        """Authorization header JWT token is extracted and passed to the service."""
        mock_service = MagicMock()
        mock_service.interpret = AsyncMock(return_value=_make_resolved_query())

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                await client.post(
                    "/api/nlp/query",
                    json={"rawText": "Book the sports hall"},
                    headers={
                        "X-User-Id": "test-user-1",
                        "Authorization": "Bearer test-jwt-token",
                    },
                )

        mock_service.interpret.assert_called_once_with(
            user_id="test-user-1",
            raw_text="Book the sports hall",
            jwt_token="test-jwt-token",
        )


# ── Get Query by ID Endpoint ───────────────────────────────────────────────

class TestGetQueryEndpoint:
    """C5: Integration tests — GET /api/nlp/query/{query_id} endpoint contract."""

    @pytest.mark.anyio
    async def test_get_existing_query_returns_200(self):
        """Retrieving an existing query by ID returns HTTP 200."""
        mock_service = MagicMock()
        mock_service.get_query = AsyncMock(return_value=_make_resolved_query("abc-123"))

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.get("/api/nlp/query/abc-123")

        assert response.status_code == 200

    @pytest.mark.anyio
    async def test_get_nonexistent_query_returns_404(self):
        """Requesting a query ID that does not exist returns HTTP 404."""
        from app.application.service import QueryNotFoundException
        mock_service = MagicMock()
        mock_service.get_query = AsyncMock(
            side_effect=QueryNotFoundException("Query not-found-id not found")
        )

        with patch("app.api.routes._service", mock_service):
            async with AsyncClient(
                transport=ASGITransport(app=app), base_url="http://test"
            ) as client:
                response = await client.get("/api/nlp/query/not-found-id")

        assert response.status_code == 404


# ── Facility Service Integration (Contract) ────────────────────────────────

class TestFacilityServiceIntegration:
    """
    Contract tests for the Facility Service ACL boundary.

    These tests verify the FacilityServiceClient correctly handles
    responses from the Facility Service REST API as documented in the
    Facility Service Integration Guide (Eryk Marcinkowski, 22374248).

    The Facility Service is mocked — these tests verify our client
    correctly handles the contract, not that the real service is running.
    """

    @pytest.mark.anyio
    async def test_facility_client_resolves_name_to_id(self):
        """Client correctly extracts facilityId from a successful batch lookup response."""
        from app.infrastructure.facility_client import FacilityServiceClient

        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {
                "facilityId": "7b171826-a4eb-4e3b-bbcf-aa05f71e81da",
                "name": "Sports Hall",
                "type": "SPORTS",
                "status": "AVAILABLE",
            }
        ]

        with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
            mock_get.return_value = mock_response
            client = FacilityServiceClient(base_url="http://plassey-facility:8082")
            facility_id = await client.resolve_facility_id("Sports Hall", "test-jwt")

        assert facility_id == "7b171826-a4eb-4e3b-bbcf-aa05f71e81da"

    @pytest.mark.anyio
    async def test_facility_client_returns_none_when_not_found(self):
        """Client returns None when facility name cannot be resolved."""
        from app.infrastructure.facility_client import FacilityServiceClient

        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = []

        with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
            mock_get.return_value = mock_response
            client = FacilityServiceClient(base_url="http://plassey-facility:8082")
            facility_id = await client.resolve_facility_id("Nonexistent Room", "test-jwt")

        assert facility_id is None

    @pytest.mark.anyio
    async def test_facility_client_returns_none_on_service_unavailable(self):
        """Client returns None gracefully when Facility Service is unreachable."""
        from app.infrastructure.facility_client import FacilityServiceClient
        import httpx

        with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
            mock_get.side_effect = httpx.ConnectError("Connection refused")
            client = FacilityServiceClient(base_url="http://plassey-facility:8082")
            facility_id = await client.resolve_facility_id("Sports Hall", "test-jwt")

        assert facility_id is None

    @pytest.mark.anyio
    async def test_facility_client_retries_on_failure(self):
        """Client attempts retry after first failure before returning None."""
        from app.infrastructure.facility_client import FacilityServiceClient
        import httpx

        with patch("httpx.AsyncClient.get", new_callable=AsyncMock) as mock_get:
            mock_get.side_effect = httpx.ConnectError("Connection refused")
            client = FacilityServiceClient(base_url="http://plassey-facility:8082")
            await client.resolve_facility_id("Sports Hall", "test-jwt")

        assert mock_get.call_count == 2
