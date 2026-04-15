# NLP Service — Plassey Planner

**CS4135 Group 9 | Michael Cronin**

The NLP Service is the polyglot bounded context for the Plassey Planner facility booking system. It accepts natural language input from users and converts it into structured booking intent and entities using the OpenAI API. It is implemented in Python/FastAPI, while all other team services use Spring Boot.

---


1. Create a `.env` file in the `nlp-service/` directory:
   ```env
   OPENAI_API_KEY=your-openai-api-key-here
   ENVIRONMENT=development
   DATABASE_URL=postgresql://postgres:postgres@nlp-db:5432/nlp_service
   RABBITMQ_URL=amqp://guest:guest@rabbitmq:5672/
   EUREKA_URL=http://eureka-server:8761/eureka
   ```

---

## Running the Service

```bash
sudo docker compose up --build
```

This starts three containers:
- `nlp-service` — FastAPI app on port 8000
- `nlp-db` — PostgreSQL on port 5432
- `rabbitmq` — RabbitMQ on port 5672 (management UI on port 15672)

---

## API Endpoints

### Health Check
```
GET /api/nlp/health
```
Returns `{"status": "UP", "service": "nlp-service"}`

### Interpret Natural Language Query
```
POST /api/nlp/query
Headers: X-User-Id: <user-id>
Body:    {"rawText": "Book me the sports hall on Friday at 3pm"}
```

Example successful response:
```json
{
  "queryId": "ea30c445-a134-4515-bb1e-0b87d3767d53",
  "status": "RESOLVED",
  "resolution": {
    "intent": { "type": "CREATE_BOOKING", "confidence": 0.9 },
    "entities": [
      { "entityType": "FACILITY", "value": "Sports Hall", "rawSpan": "sports hall" },
      { "entityType": "DATE", "value": "2026-04-17", "rawSpan": "Friday" },
      { "entityType": "TIME", "value": "15:00", "rawSpan": "3pm" }
    ],
    "resolvedAt": "2026-04-14T12:41:30.428385+00:00"
  },
  "error": null
}
```

Supported intents: `CREATE_BOOKING`, `CHECK_AVAILABILITY`, `CANCEL_BOOKING`, `QUERY_STATUS`

---

## Running Tests

```bash
sudo docker compose exec nlp-service pytest test/ -v
```

---

## Architecture

The service follows a layered DDD architecture:

```
app/
├── api/           # FastAPI routes (API layer)
├── application/   # Use case orchestration (Application layer)
├── domain/        # Aggregates, value objects, events (Domain layer)
└── infrastructure/
    ├── openai_translator.py    # Anti-Corruption Layer (OpenAI boundary)
    ├── postgres_repository.py  # Repository implementation
    └── rabbitmq_publisher.py   # Event publisher
```

On a successful query interpretation, a `QueryInterpretationResolved` event is published to the `nlp.events` RabbitMQ exchange for downstream services (e.g. Booking Context) to consume.

