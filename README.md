# Plassey Planner - Backend

Microservices backend for the University of Limerick facility booking system. Students and staff can book campus spaces through a REST API or a natural language interface powered by the OpenAI API.

## Team

| Member | ID | Service |
|---|---|---|
| Jing Peng | 22301658 | User Service |
| Eryk Marcinkowski | 22374248 | Facility Service |
| Darren Nugent | 22365893 | Booking & Approval Service |
| Kevin Burke | 22355634 | Notification & Logging Service |
| Michael Cronin | 22336842 | NLP Service |
| Muadh Muhsin Zibiri | 22235302 | Frontend & API Gateway |

## Services

| Service | Port | Description |
|---|---|---|
| api-gateway | 8080 | Single entry point, JWT validation, routing |
| user-service | 8081 | Registration, login, JWT issuance |
| facility-service | 8082 | Facility catalogue and availability |
| booking-service | 8083 | Booking lifecycle |
| approval-service | 8084 | Admin approval workflow |
| notification-service | 8085 | Event-driven in-app notifications |
| nlp-service | 8000 | Natural language booking via OpenAI |

Infrastructure: PostgreSQL (5432), RabbitMQ (5672/15672), Eureka (8761), Config Server (8888)

## Running the system

**Requirements:** Docker and Docker Compose

```bash
# 1. Clone the repo
git clone https://github.com/mcPrefect/CS4135_Facility_Booking_Backend.git
cd CS4135_Facility_Booking_Backend

# 2. Add your OpenAI API key (required for the NLP service)
echo "OPENAI_API_KEY=sk-your-key-here" > .env

# 3. Start everything
docker compose up --build
```

All databases are created automatically on first run. Wait about 2-3 minutes for all services to come up then check:

```bash
docker compose ps           # all 11 containers should show Up
curl http://localhost:8080/actuator/health
```

To wipe and start fresh:
```bash
docker compose down -v
docker compose up --build
```

## Testing the full flow

Run the end-to-end test script (requires `jq`):

```bash
./test-flow.sh
```

This registers users, creates a facility, sends an NLP booking request, approves it as admin, and verifies the notifications come through.

Or manually with curl — register an admin, create a facility, then:

```bash
# Send a natural language booking request
curl -s -X POST http://localhost:8080/api/nlp/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d '{"rawText": "Book the Sports Hall next Friday at 3pm for 1 hour"}' | jq .

# Approve the booking (use bookingId from GET /api/v1/bookings)
curl -s -X PATCH http://localhost:8080/api/v1/approvals/<bookingId>/approve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"reason":"Approved"}' | jq .
```

## Useful URLs

- API Gateway: http://localhost:8080
- Eureka dashboard: http://localhost:8761
- RabbitMQ management: http://localhost:15672 (guest/guest)
