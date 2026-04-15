"""
NLP Context - FastAPI Application Entry Point

Wires together all layers: domain, application, infrastructure, and API.
"""

from __future__ import annotations

import logging
logging.getLogger("py_eureka_client.eureka_client").setLevel(logging.CRITICAL)
from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import Settings
from app.api.routes import router, set_service
from app.application.service import QueryInterpretationService
from app.infrastructure.openai_translator import OpenAIResponseTranslator
from app.infrastructure.postgres_repository import PostgresNLPQueryRepository
from app.infrastructure.rabbitmq_publisher import RabbitMQPublisher
from app.infrastructure.facility_client import FacilityServiceClient


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

settings = Settings.from_env()

# Infrastructure instances (initialised during startup)
_db_pool = None
_publisher = None

import py_eureka_client.eureka_client as eureka_client

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"Starting nlp-service ({settings.environment})")
    """Manage application startup and shutdown."""
    global _db_pool, _publisher

    logger.info(f"Starting {settings.service_name} ({settings.environment})")

    # Connect to PostgreSQL
    _db_pool = await asyncpg.create_pool(
        settings.database_url,
        min_size=2,
        max_size=10,
    )
    repository = PostgresNLPQueryRepository(_db_pool)
    await repository.initialise()
    logger.info("PostgreSQL connected")

    # Connect to RabbitMQ
    _publisher = RabbitMQPublisher(settings.rabbitmq_url)
    try:
        await _publisher.connect()
    except Exception as e:
        logger.warning(f"RabbitMQ not available at startup: {e}. Events will not be published.")

    # Create ACL and application service
    translator = OpenAIResponseTranslator(
        api_key=settings.openai_api_key,
        model=settings.openai_model,
    )
    
    facility_client = FacilityServiceClient(base_url=settings.facility_service_url)

    service = QueryInterpretationService(
        translator=translator,
        repository=repository,
        publisher=_publisher,
        facility_client=facility_client,
    )
    set_service(service)

    logger.info(f"{settings.service_name} ready on port {settings.service_port}")
    
    # During startup  
    try:
        await eureka_client.init_async(
            eureka_server=settings.eureka_url,
            app_name="nlp-service",
            instance_port=8000,
        )
        logger.info("Registered with Eureka")
    except Exception as e:
        logger.warning(f"Eureka registration failed (service will still start): {e}")

    yield
    # During shutdown 
    await eureka_client.stop_async()
    logger.info("Deregistered from Eureka")

    # Shutdown
    logger.info("Shutting down...")
    if _publisher:
        await _publisher.disconnect()
    if _db_pool:
        await _db_pool.close()
    logger.info("Shutdown complete")


app = FastAPI(
    title="NLP Service - Plassey Planner",
    description="Natural language booking interface for the Plassey Planner facility booking system.",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)
