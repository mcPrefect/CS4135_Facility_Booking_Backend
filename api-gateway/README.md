# API Gateway (Spring Cloud Gateway)

Java package: `com.facilitybooking.apigateway`.

Single entry point for the Plassey Planner SPA. Routes `/api/v1/**` and `/api/nlp/**` to downstream services.

## JWT validation

The gateway validates **HS256** JWTs using the same secret as `user-service` (`jwt.secret` / env `JWT_SECRET`). Public paths (no `Authorization` header required):

- `POST /api/v1/auth/login`, `POST /api/v1/auth/register`
- `GET /api/nlp/health`
- `/actuator/**`
- Internal forward `/__gateway/not-found`

All other `/api/**` traffic requires `Authorization: Bearer <token>`.

Validated requests receive forwarded headers for downstream services:

- `X-User-Id` — from JWT `userId` claim (fallback: subject)
- `X-User-Role` — from JWT `role` claim

## Run locally

```bash
cd api-gateway
mvn spring-boot:run
```

- Gateway: [http://localhost:8080](http://localhost:8080)
- Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## Docker

Build context is this directory (`api-gateway/`):

```bash
docker build -t facility-api-gateway .
```

## Environment variables

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `SERVER_PORT` | `8080` | Gateway listen port |
| `JWT_SECRET` | (see `application.yml`) | Must match user-service signing key |
| `CORS_ALLOWED_ORIGINS` | `localhost:3000` and `5173` | Comma-separated browser origins |
| `USER_SERVICE_URI` | `http://localhost:8081` | User / auth / admin |
| `FACILITY_SERVICE_URI` | `http://localhost:8082` | Facility |
| `BOOKING_SERVICE_URI` | `http://localhost:8083` | Booking |
| `APPROVAL_SERVICE_URI` | `http://localhost:8084` | Approval |
| `NOTIFICATION_SERVICE_URI` | `http://localhost:8085` | Notification + logs |
| `NLP_SERVICE_URI` | `http://localhost:8000` | NLP |

## Reliability

Booking, approval, and notification routes use GET retries and timeouts. Unmatched `/api/**` returns JSON `404` from `GatewayErrorController`.
