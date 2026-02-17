# Facility Booking System - Backend

Microservices backend for the University of Limerick facility booking system.

## Team Memebrs

- Member 1: [Jing Peng] - [22301658] - User Service
- Member 2: [Eryk Marcinkowski] - [22374248] - Facility Service
- Member 3: [Darren Nugent] - [22365893] - Booking Service
- Member 4: [Kevin Burke] - [22355634] - Notification Service
- Member 5: [Michael Cronin] - [22336842] - NLP Service
- Member 6: [Muadh Muhsin Zibiri] - [22235302] - Approval Service

## Architecture

**Microservices:**
- **user-service** (Port 8081) - Authentication & user management
- **facility-service** (Port 8082) - Facility CRUD operations
- **booking-service** (Port 8083) - Booking lifecycle management
- **approval-service** (Port 8084) - Booking approval workflow
- **notification-service** (Port 8085) - Event-driven notifications
- **nlp-service** (Port 8000) - Natural language booking interface

**Infrastructure:**
- **api-gateway** (Port 8080) - API routing and authentication
- PostgreSQL (Port 5432) - Persistent data storage
- RabbitMQ (Port 5672) - Asynchronous messaging

## Project Structure
```
├── user-service/          # User authentication (Jing Peng)
├── facility-service/      # Facility management (Eryk Marcinkowski)
├── booking-service/       # Booking operations (Darren Nugent)
├── approval-service/      # Approval workflow (Muadh Muhsin Zibiri)
├── notification-service/  # Notifications (Kevin Burke)
├── nlp-service/          # NLP processing (Michael Cronin)
├── api-gateway/          # API Gateway
└── docs/                 # Architecture documentation
    ├── architecture/     # System diagrams
    ├── api-specs/       # OpenAPI specifications
    └── requirements/    # Requirements documents
```