"""
NLP Context - Configuration

Externalised configuration loaded from environment variables.
Supports environment separation (dev, test, prod) for Part C criterion C3.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass
class Settings:
    """Application settings loaded from environment variables."""

    # Service
    service_name: str = "nlp-service"
    service_port: int = 8000
    environment: str = "development"

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"

    # PostgreSQL
    database_url: str = "postgresql://postgres:postgres@localhost:5432/nlp_service"

    # RabbitMQ
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"

    # NLP
    min_confidence_threshold: float = 0.6
    
    eureka_url: str = "http://eureka-server:8761/eureka"

    @classmethod
    def from_env(cls) -> Settings:
        """Load settings from environment variables."""
        return cls(
            eureka_url= os.getenv("EUREKA_URL", "http://eureka-server:8761/eureka"),
            service_name=os.getenv("SERVICE_NAME", "nlp-service"),
            service_port=int(os.getenv("SERVICE_PORT", "8000")),
            environment=os.getenv("ENVIRONMENT", "development"),
            openai_api_key=os.getenv("OPENAI_API_KEY", ""),
            openai_model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            database_url=os.getenv(
                "DATABASE_URL",
                "postgresql://postgres:postgres@localhost:5432/nlp_service",
            ),
            rabbitmq_url=os.getenv(
                "RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"
            ),
            min_confidence_threshold=float(
                os.getenv("MIN_CONFIDENCE_THRESHOLD", "0.6")
            ),
        )
