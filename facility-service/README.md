# Facility Service — Plassey Planner (CS4135 Group 9)

**Developer:** Eryk Marcinkowski (22374248)  
**Bounded Context:** Facility Management  
**Port:** `8082`  
**Stack:** Spring Boot 3.2 · Spring Cloud 2023 · PostgreSQL · RabbitMQ · Eureka · Resilience4j

---

## Table of Contents

1. [What This Service Does](#1-what-this-service-does)
2. [Project Structure](#2-project-structure)
3. [Prerequisites](#3-prerequisites)
4. [Running the Full Stack (Docker)](#4-running-the-full-stack-docker)
5. [Running Tests](#5-running-tests)
6. [Verifying Each Criterion](#6-verifying-each-criterion)
7. [API Reference](#7-api-reference)
8. [Generating a JWT Token for Testing](#8-generating-a-jwt-token-for-testing)
9. [Domain Model and Invariants](#9-domain-model-and-invariants)
10. [RabbitMQ Event Contract](#10-rabbitmq-event-contract)

---

## 1. What This Service Does

The Facility Service is responsible for everything related to campus facilities — rooms, labs,
sports halls, and equipment. It is the single source of truth for facility data in the
Plassey Planner system.

Other services depend on it as follows:

- **Booking Service** calls `/api/v1/facilities/{id}/exists` before creating a booking to
  confirm the facility is available
- **NLP Service** calls `/api/v1/facilities/lookup/batch` to resolve a facility name from
  natural language input into a real facility ID
- **Notification Service** listens to RabbitMQ events published by this service to log
  and notify users of facility changes

The service satisfies the five assignment criteria as follows:

| Criterion | How it is met |
|-----------|---------------|
| C1 – Bounded Context | DDD aggregate root with 9 enforced domain invariants, unit + integration tests |
| C2 – Service Discovery | Registers with Eureka on startup, discoverable by other services |
| C3 – Centralised Config | Loads configuration from Spring Cloud Config Server at startup |
| C4 – Resilience | Circuit breaker and retry on RabbitMQ publisher; API stays up during outages |
| C5 – Integration | REST API, RabbitMQ events, 19 automated tests proving end-to-end behaviour |

---

## 2. Project Structure

```
facility-service/
├── config/
│   └── facility-service.yml        # Centralised config served by Config Server (C3)
├── config-server/                  # Self-contained Config Server (built from source)
├── eureka-server/                  # Self-contained Eureka Server (built from source)
├── src/
│   ├── main/java/com/plassey/facilityservice/
│   │   ├── domain/
│   │   │   ├── model/              # Facility aggregate root, MaintenanceWindow, value objects
│   │   │   ├── events/             # Domain events (FacilityCreated, StatusChanged, etc.)
│   │   │   ├── repository/         # Repository interface (no JPA dependency in domain)
│   │   │   └── service/            # FacilityValidationService
│   │   ├── application/
│   │   │   ├── dto/                # Request/response DTOs and ACL mappers
│   │   │   └── service/            # FacilityApplicationService (orchestration layer)
│   │   ├── infrastructure/
│   │   │   ├── config/             # SecurityConfig (JWT), RabbitMQConfig, DataSeeder
│   │   │   ├── messaging/          # FacilityEventPublisher with circuit breaker
│   │   │   └── persistence/        # JPA entities, Spring Data repos, domain mapper
│   │   └── api/
│   │       └── controller/         # FacilityController, GlobalExceptionHandler
│   └── test/java/com/plassey/facilityservice/
│       ├── domain/                 # FacilityAggregateTest — 11 unit tests
│       └── api/                    # FacilityControllerIntegrationTest — 8 integration tests
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

---

## 3. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | Check with `java -version` |
| Maven | 3.9+ | Check with `mvn -version` |
| Docker Desktop | 24+ | Must be running before starting the stack |

---

## 4. Running the Full Stack (Docker)

This starts all five containers: PostgreSQL, RabbitMQ, Eureka, Config Server, and the
Facility Service itself.

```bash
# First run — builds images from source (takes 3-4 minutes)
docker-compose up -d --build

# Subsequent runs — faster, uses cached images
docker-compose up -d
```

**On Windows (PowerShell), check health with:**

```powershell
curl.exe http://localhost:8082/actuator/health
```

Wait until you see `{"status":"UP"}` before making any API calls.

**Service dashboards:**

| Service | URL | Login |
|---------|-----|-------|
| Facility Service health | http://localhost:8082/actuator/health | — |
| Eureka dashboard | http://localhost:8761 | — |
| RabbitMQ management | http://localhost:15672 | guest / guest |

**Rebuilding after a code change:**

```bash
# Only rebuilds the facility service — other containers stay running
docker-compose up -d --build facility-service
```

**Shutting down:**

```bash
docker-compose down
```

---

## 5. Running Tests

The tests use an H2 in-memory database and a mocked RabbitMQ publisher.
**No Docker required** — just Java 21 and Maven.

### Run all 19 tests

**On Windows (PowerShell):**

```powershell
mvn test "-Dspring.profiles.active=test"
```

**On Mac/Linux:**

```bash
mvn test -Dspring.profiles.active=test
```

Expected output:

```
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Run unit tests only (no Spring context — very fast)

```powershell
mvn test "-Dspring.profiles.active=test" "-Dtest=FacilityAggregateTest"
```

Expected: `Tests run: 11, Failures: 0`

### Run integration tests only

```powershell
mvn test "-Dspring.profiles.active=test" "-Dtest=FacilityControllerIntegrationTest"
```

Expected: `Tests run: 8, Failures: 0`

### What each test covers

**`FacilityAggregateTest` — 11 unit tests (C1 evidence)**

| Test | Invariant tested |
|------|-----------------|
| Blank name throws exception | INV-F1: name must not be blank |
| Zero capacity throws exception | INV-F3: capacity must be greater than 0 |
| Factory always starts as AVAILABLE | INV-F9: cannot create with RETIRED status |
| RETIRED facility cannot transition | INV-L4: RETIRED is a terminal state |
| Overlapping maintenance windows rejected | INV-F6: no overlapping windows allowed |
| Maintenance window start after end rejected | INV-L5: start must be before end |
| Operating hours start after end rejected | INV-F7: valid time range required |
| Create produces FacilityCreated event | Domain events are published correctly |
| Status change produces StatusChanged event | Domain events are published correctly |
| Maintenance produces MaintenanceScheduled event | Domain events are published correctly |
| isBookable returns false when MAINTENANCE | Bookability logic is correct |

**`FacilityControllerIntegrationTest` — 8 integration tests (C5 evidence)**

| Test | What it proves |
|------|---------------|
| No token → 401 Unauthorized | Authentication is enforced |
| Create + Get round trip | Full HTTP lifecycle works end-to-end |
| Duplicate name → 409 Conflict | INV-F8 name uniqueness enforced at API level |
| Student role → 403 Forbidden | Role-based access control works |
| Exists endpoint returns bookability info | Booking Service integration contract works |
| Search with type filter | Query and filtering works correctly |
| Soft delete → status becomes RETIRED | Delete is non-destructive |
| Schedule maintenance window | Maintenance scheduling API works |

---

## 6. Verifying Each Criterion

### C1 — Bounded Context

Run the full test suite:

```powershell
mvn test "-Dspring.profiles.active=test"
```

All 19 tests should pass. The `Facility` aggregate root enforces 9 business invariants
and no external class can modify its state directly.

---

### C2 — Service Discovery

1. Start the stack with `docker-compose up -d --build`
2. Open http://localhost:8761 in your browser
3. Confirm **FACILITY-SERVICE** appears in the list with status **UP**

The service registers itself on startup via `@EnableDiscoveryClient`. Other services
in the group can discover it using the service name `facility-service` rather than
a hard-coded IP address.

---

### C3 — Centralised Configuration

Config is loaded automatically at startup from the Config Server. To verify:

```powershell
docker logs plassey-facility 2>&1 | Select-String "config"
```

You should see:

```
Fetching config from server at: http://config-server:8888
Located environment: name=facility-service, profiles=[default]
```

The file `config/facility-service.yml` in this repository is what gets served. It contains
the JWT secret, database URL, and resilience thresholds. To add a staging environment,
create `config/facility-service-staging.yml` and set `SPRING_PROFILES_ACTIVE=staging`
in docker-compose.

---

### C4 — Resilience (Circuit Breaker)

This shows the API continues working even when RabbitMQ goes down.

**Step 1** — Stop RabbitMQ to simulate an outage:

```powershell
docker stop plassey-rabbitmq
```

**Step 2** — Create a facility in Postman (see Section 7). The response should still be
**201 Created** even though RabbitMQ is unavailable.

**Step 3** — Check that the circuit breaker caught the failure:

```powershell
docker logs plassey-facility 2>&1 | Select-String "Error\|Attempting\|connect" | Select-Object -Last 5
```

**Step 4** — Restart RabbitMQ:

```powershell
docker start plassey-rabbitmq
```

The circuit breaker in `FacilityEventPublisher` retries 3 times with exponential backoff
(500ms → 1s → 2s), then opens and calls a fallback method. The HTTP response to the user
is always unaffected by RabbitMQ being down.

---

### C5 — Integration

**Run the integration tests:**

```powershell
mvn test "-Dspring.profiles.active=test" "-Dtest=FacilityControllerIntegrationTest"
```

All 8 tests should pass.

**Verify RabbitMQ events are flowing:**

1. Open http://localhost:15672 (guest / guest)
2. Click **Exchanges** → click **facility.events**
3. The Bindings section shows all 4 queues wired up with their routing keys
4. Create a facility in Postman — the Message rates graph shows a brief spike confirming
   the event was published and routed to the correct queue

---

## 7. API Reference

All endpoints are under `/api/v1/`. Every request needs a Bearer token in the
`Authorization` header. See Section 8 for how to generate one.

### Roles

| Role | What they can access |
|------|---------------------|
| STUDENT / STAFF | GET endpoints only |
| ADMIN | All endpoints |

### All endpoints

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/api/v1/facilities` | Any | Search facilities with optional filters |
| GET | `/api/v1/facilities/{id}` | Any | Get a specific facility by ID |
| GET | `/api/v1/facilities/{id}/exists` | Any | Check if a facility is bookable |
| GET | `/api/v1/facilities/lookup/batch?names=...` | Any | Find facilities by name |
| POST | `/api/v1/facilities` | ADMIN | Create a new facility |
| PUT | `/api/v1/facilities/{id}` | ADMIN | Update facility details |
| PATCH | `/api/v1/facilities/{id}/status` | ADMIN | Change operational status |
| DELETE | `/api/v1/facilities/{id}` | ADMIN | Soft-delete (sets status to RETIRED) |
| POST | `/api/v1/facilities/{id}/maintenance` | ADMIN | Schedule a maintenance window |

### Create a facility

Request body:

```json
{
    "name": "Computer Lab 2.01",
    "type": "COMPUTER_LAB",
    "capacity": 30,
    "location": {
        "building": "CS Building",
        "floor": 2,
        "room": "2.01"
    },
    "operatingHours": {
        "startTime": "09:00",
        "endTime": "21:00",
        "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
    },
    "amenities": ["PROJECTOR", "WHITEBOARD", "AIR_CONDITIONING"]
}
```

Valid types: `COMPUTER_LAB`, `SEMINAR_ROOM`, `STUDY_ROOM`, `SPORTS_AREA`, `LECTURE_ROOM`,
`LABORATORY`, `MEETING_ROOM`, `AUDITORIUM`, `LIBRARY_SPACE`, `OUTDOOR_AREA`

Valid statuses: `AVAILABLE`, `OCCUPIED`, `MAINTENANCE`, `RESTRICTED`

### Check if a facility is bookable

```
GET /api/v1/facilities/{facilityId}/exists
```

Response when available:

```json
{
    "exists": true,
    "facilityId": "7b171826-a4eb-4e3b-bbcf-aa05f71e81da",
    "name": "Computer Lab 2.01",
    "status": "AVAILABLE",
    "isBookable": true,
    "reason": null
}
```

Response when under maintenance:

```json
{
    "exists": true,
    "facilityId": "7b171826-a4eb-4e3b-bbcf-aa05f71e81da",
    "name": "Computer Lab 2.01",
    "status": "MAINTENANCE",
    "isBookable": false,
    "reason": "Facility is currently under maintenance"
}
```

### Search facilities with filters

```
GET /api/v1/facilities?type=COMPUTER_LAB&status=AVAILABLE&minCapacity=20&page=0&size=20
```

Available parameters: `type`, `status`, `minCapacity`, `maxCapacity`, `building`, `page`, `size`

### Schedule a maintenance window

```json
{
    "startTime": "2026-05-01T09:00:00Z",
    "endTime": "2026-05-01T17:00:00Z",
    "reason": "Annual electrical inspection"
}
```

---

## 8. Generating a JWT Token for Testing

All endpoints require a signed JWT. To generate one for testing:

1. Go to **https://jwt.io**
2. Set the **Payload** to:

```json
{
    "sub": "testadmin",
    "role": "ADMIN",
    "exp": 9999999999
}
```

3. In the **Verify Signature** section, replace the secret with:

```
plassey-planner-secret-key-for-hs256-minimum-32-bytes
```

4. Make sure **BASE64URL ENCODED** is switched **off**
5. Copy the long token string from the left side (starts with `eyJ...`)

In Postman: Authorization tab → Type: Bearer Token → paste the token.

For a read-only student token, change `"role": "ADMIN"` to `"role": "STUDENT"`.

---

## 9. Domain Model and Invariants

The `Facility` class is the aggregate root. All state changes go through its methods — no
external code can directly modify a facility's fields.

| ID | Rule | Where it is enforced |
|----|------|---------------------|
| INV-F1 | Name must not be blank | `Facility` constructor |
| INV-F2 | Type cannot change after creation | No setter exposed for type |
| INV-F3 | Capacity must be greater than 0 | `Facility` constructor |
| INV-L4 | A RETIRED facility cannot change status | `FacilityStatus.canTransitionTo()` |
| INV-L5 | Maintenance window start must be before end | `MaintenanceWindow` constructor |
| INV-F6 | Maintenance windows for the same facility cannot overlap | `Facility.addMaintenanceWindow()` |
| INV-F7 | Operating hours start must be before end | `OperatingHours` record constructor |
| INV-F8 | Facility names must be unique (case-insensitive) | Database constraint + pre-check in ACL |
| INV-F9 | A new facility always starts with AVAILABLE status | `Facility.create()` factory method |

---

## 10. RabbitMQ Event Contract

The service publishes domain events to the `facility.events` Topic exchange whenever
facility data changes. Downstream services subscribe to stay in sync.

| Event | Routing Key | Who consumes it |
|-------|-------------|----------------|
| FacilityCreated | `facility.created` | Notification Service, NLP Service |
| FacilityUpdated | `facility.updated` | Notification Service, NLP Service |
| FacilityStatusChanged | `facility.status.changed` | Booking Service |
| MaintenanceScheduled | `facility.maintenance.scheduled` | Booking Service |

All events include `schemaVersion: "1.0"` and ISO-8601 timestamps. Null optional fields
are omitted from the payload.

Example `FacilityStatusChanged` payload:

```json
{
    "eventType": "FacilityStatusChanged",
    "schemaVersion": "1.0",
    "facilityId": "7b171826-a4eb-4e3b-bbcf-aa05f71e81da",
    "name": "Computer Lab 2.01",
    "oldStatus": "AVAILABLE",
    "newStatus": "MAINTENANCE",
    "reason": "Floor resurfacing",
    "occurredAt": "2026-04-13T10:00:00Z"
}
```
