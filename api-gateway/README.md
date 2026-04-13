# API Gateway (Spring Cloud Gateway)

Single entry point for the Plassey Planner SPA and external clients. Forwards `/api/v1/**` to downstream services using ports from the team README.

## Run locally

```bash
cd api-gateway
mvn spring-boot:run
```

- Gateway: [http://localhost:8080](http://localhost:8080)
- Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## Environment variables

| Variable | Default | Service |
|----------|---------|---------|
| `SERVER_PORT` | `8080` | This gateway |
| `USER_SERVICE_URI` | `http://localhost:8081` | User / auth |
| `FACILITY_SERVICE_URI` | `http://localhost:8082` | Facility |
| `BOOKING_SERVICE_URI` | `http://localhost:8083` | Booking |
| `APPROVAL_SERVICE_URI` | `http://localhost:8084` | Approval |
| `NOTIFICATION_SERVICE_URI` | `http://localhost:8085` | Notification |
| `NLP_SERVICE_URI` | `http://localhost:8000` | NLP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Vite dev server (comma-separated) |

Downstream services must expose the same path prefixes (e.g. `/api/v1/auth/login` on user-service). Adjust routes in `application.yml` when OpenAPI paths differ.

## JWT

This scaffold does not validate JWT at the gateway; services (or a later filter) can enforce tokens. Add Spring Security resource server here when the team is ready.
