# Governance Policy Management System

A production-grade backend system for managing governance policies with full audit logging, built using a microservices architecture with an API Gateway, event-driven communication, and internal gRPC service-to-service communication.

---

## Architecture Overview

All client requests enter through a centralized **API Gateway** which handles authentication, rate limiting, and circuit breaking before routing to backend services. Services communicate asynchronously via **Apache Kafka** and synchronously via **gRPC**.

```
Client (Postman / Browser)
          │
          ▼ HTTP (port 8888)
┌──────────────────────────────────────────┐
│              API GATEWAY                  │
│  - JWT Authentication Filter             │
│  - Redis Rate Limiter (10 req/60s)        │
│  - Resilience4j Circuit Breaker           │
│  - Spring Cloud LoadBalancer (lb://)      │
└───────────┬──────────────┬───────────────┘
            │              │
            ▼              ▼
┌────────────────┐  ┌──────────────────┐  ┌──────────────┐
│ governance-svc │  │   audit-svc      │  │  user-svc    │
│  Port: 8080    │  │   Port: 8082     │  │  Port: 8083  │
│                │  │                  │  │              │
│  OutboxPoller  │  │  Kafka Consumer  │  │  JWT Issuer  │
│  (every 5s)   │  │  gRPC Server     │  │              │
└───────┬────────┘  └──────────────────┘  └──────────────┘
        │ gRPC (port 9090)       ▲
        └────────────────────────┘
        │
        ▼
┌────────────────┐
│  Apache Kafka  │  ← Topic: governance-events
└────────────────┘

┌──────────────────────────┐  ┌──────────────┐
│  Eureka Server :8761     │  │  Redis :6379 │
│  Service Discovery       │  │  Rate Limit  │
└──────────────────────────┘  └──────────────┘

┌─────────────────────────────────────────┐
│  PostgreSQL :5432                        │
│  governance_db  │  audit_db  │  user_db  │
└─────────────────────────────────────────┘
```

### Services

| Service | Port | Responsibility |
|---------|------|----------------|
| **Eureka Server** | `8761` | Service discovery and registry |
| **API Gateway** | `8888` | Single entry point — JWT auth, rate limiting, circuit breaking, routing |
| **Governance Service** | `8080` | Policy lifecycle management, Kafka event publishing |
| **Audit Service** | `8082` | Kafka event consumption, immutable audit logs, gRPC server |
| **User Service** | `8083` | User registration, authentication, JWT token issuance |

### Policy Lifecycle

```
DRAFT → PENDING_APPROVAL → APPROVED
                         → REJECTED
```

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.6 | Backend framework |
| Spring Cloud Gateway | API Gateway (reactive WebFlux) |
| Spring Cloud Netflix Eureka | Service discovery and registration |
| Spring Cloud LoadBalancer | Client-side load balancing |
| Resilience4j | Circuit breaker pattern |
| Redis | Token-bucket rate limiting |
| Apache Kafka | Asynchronous event messaging |
| gRPC / Protobuf | Internal synchronous service-to-service communication |
| PostgreSQL 15 | Data persistence |
| Hibernate / JPA | ORM for database access |
| JJWT 0.12.6 | JWT token creation and validation |
| Lombok | Boilerplate code reduction |
| springdoc-openapi | Swagger / OpenAPI documentation |
| Docker | Infrastructure (PostgreSQL + Kafka + Redis) |

---

## Design Patterns Implemented

| Pattern | Applied In | Problem Solved |
|---------|-----------|----------------|
| **API Gateway** | `api-gateway` | Single entry point, cross-cutting concerns |
| **Service Discovery** | Eureka + all services | Dynamic service registration and routing |
| **JWT Authentication** | `api-gateway` filter | Centralized, stateless authentication |
| **Circuit Breaker** | `api-gateway` (Resilience4j) | Cascade failure prevention |
| **Token Bucket Rate Limiting** | `api-gateway` + Redis | API abuse and flood prevention |
| **gRPC Contract** | `governance-service` → `audit-service` | Typed, high-performance internal communication |
| **Transactional Outbox** | `governance-service` | Guaranteed at-least-once Kafka event delivery |

---

## Prerequisites

- Java 21
- Docker Desktop (running)
- Maven wrapper included — no need to install Maven separately

---

## How to Run the System

### Step 1 — Start Infrastructure (PostgreSQL + Kafka + Redis)

From the root of the project:

```bash
docker-compose up -d
```

This will start:
- **PostgreSQL** on port `5432` (with `governance_db`, `audit_db`, and `user_db` schemas auto-created)
- **Zookeeper** on port `2181`
- **Kafka** on port `9092`
- **Redis** on port `6379`

---

### Step 2 — Start all Services (5 terminals)

Start each service in a separate terminal in the following order:

```bash
# Terminal 1 — Eureka Server (start first)
cd eureka-server
.\mvnw.cmd spring-boot:run

# Terminal 2 — User Service
cd user-service
.\mvnw.cmd spring-boot:run

# Terminal 3 — Audit Service
cd audit-service
.\mvnw.cmd spring-boot:run

# Terminal 4 — Governance Service
cd governance-service
.\mvnw.cmd spring-boot:run

# Terminal 5 — API Gateway (start last)
cd api-gateway
.\mvnw.cmd spring-boot:run
```

> **Note:** On macOS/Linux, replace `.\mvnw.cmd` with `./mvnw`.

Once all services are running, verify they are all registered at the **Eureka Dashboard**: http://localhost:8761

---

## Authentication

All protected endpoints require a **JWT Bearer token** obtained from the User Service via the API Gateway.

**Default admin credentials:**
- Username: `admin`
- Password: `admin123`

**Step 1 — Login to get a token:**
```bash
curl -X POST http://localhost:8888/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
```

**Response:**
```json
{ "token": "eyJhbGciOiJIUzM4NCJ9..." }
```

**Step 2 — Use the token in all subsequent requests:**
```bash
curl -H "Authorization: Bearer <your-token>" http://localhost:8888/policies
```

---

## API Reference

All requests go through the API Gateway on port `8888`.

### Policy Endpoints — `/policies/**`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/policies` | ✅ | Create a new policy |
| `GET` | `/policies` | ✅ | Get all policies |
| `GET` | `/policies/{id}` | ✅ | Get a policy by ID |
| `POST` | `/policies/{id}/submit` | ✅ | Submit a policy for approval |
| `POST` | `/policies/{id}/approve` | ✅ | Approve a policy |
| `POST` | `/policies/{id}/reject` | ✅ | Reject a policy |

**Example — Create a Policy:**
```bash
curl -X POST http://localhost:8888/policies \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Data Retention Policy",
    "description": "All data must be deleted after two years",
    "createdBy": "admin"
  }'
```

**Example — Approve a Policy:**
```bash
curl -X POST http://localhost:8888/policies/1/approve \
  -H "Authorization: Bearer <token>"
```

> The actor identity is automatically extracted from the JWT token by the gateway and forwarded as the `X-Username` header to the backend service.

---

### Audit Endpoints — `/audits/**`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/audits` | ✅ | Get all audit logs |
| `GET` | `/audits/policy/{policyId}` | ✅ | Get audit logs for a specific policy |

---

### Auth Endpoints — `/auth/**`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/auth/register` | ❌ | Register a new user |
| `POST` | `/auth/login` | ❌ | Login and receive a JWT token |

---

## Swagger UI

Both backend services expose an interactive Swagger UI for internal API exploration.

| Service | Swagger UI |
|---------|-----------|
| Governance Service | http://localhost:8080/swagger-ui/index.html |
| Audit Service | http://localhost:8082/swagger-ui/index.html |

---

## Kafka Events

The Governance Service publishes events to the `governance-events` topic using the **Transactional Outbox Pattern**, guaranteeing that events are only published after the database transaction has been committed.

**Event Types:**

| Event | Trigger |
|-------|---------|
| `policy-created` | A new policy is created |
| `policy-submitted` | A policy is submitted for approval |
| `policy-approved` | A policy is approved |
| `policy-rejected` | A policy is rejected |

**Event Payload:**
```json
{
  "eventType": "policy-approved",
  "policyId": 15,
  "actor": "manager",
  "timestamp": "2026-06-16T10:30:00"
}
```

---

## Database Schema

### Governance Service — `governance_db`

**`policies` table**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated identifier |
| `title` | VARCHAR | Policy title (required) |
| `description` | TEXT | Policy description |
| `status` | VARCHAR | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED` |
| `created_by` | VARCHAR | Creator username (required) |
| `created_at` | TIMESTAMP | Auto-set on creation |

**`outbox_events` table** (Transactional Outbox Pattern)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated identifier |
| `event_type` | VARCHAR | Type of governance event |
| `payload` | TEXT | JSON-serialized event payload |
| `status` | VARCHAR | `PENDING` or `PUBLISHED` |
| `created_at` | TIMESTAMP | Time the event was created |

### Audit Service — `audit_db`

**`audit_logs` table**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated identifier |
| `event_type` | VARCHAR | Type of governance event |
| `policy_id` | BIGINT | Reference to the related policy |
| `actor` | VARCHAR | User who performed the action |
| `timestamp` | TIMESTAMP | Time the event occurred |

---

## Running Tests

```bash
# Governance Service (35 tests)
cd governance-service
.\mvnw.cmd clean test

# Audit Service (9 tests)
cd audit-service
.\mvnw.cmd clean test
```

> **Note:** Docker containers (PostgreSQL + Kafka) must be running before executing integration tests.

---

## Stopping the System

```bash
# Stop all containers
docker-compose down

# Stop and remove all data volumes
docker-compose down -v
```
