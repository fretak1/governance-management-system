# Governance Policy Management System

A backend system for managing governance policies with full audit logging, built using a microservices architecture with event-driven communication.

---

## Architecture Overview

The system consists of two independent Spring Boot microservices that communicate asynchronously via Apache Kafka.

```
Client
  │
  ▼
┌─────────────────────────┐
│     Governance Service  │  ← REST API (port 8080)
│  - Policy CRUD          │
│  - Lifecycle management │
└────────────┬────────────┘
             │ Publishes events
             ▼
┌─────────────────────────┐
│       Apache Kafka      │  ← Topic: governance-events
└────────────┬────────────┘
             │ Consumes events
             ▼
┌─────────────────────────┐
│      Audit Service      │  ← REST API (port 8082)
│  - Event consumption    │
│  - Audit log storage    │
└─────────────────────────┘
             │
             ▼
┌─────────────────────────┐
│       PostgreSQL        │  ← Two schemas: governance_db, audit_db
└─────────────────────────┘
```

### Services

| Service | Port | Responsibility |
|---------|------|----------------|
| **Governance Service** | `8080` | Manages policy lifecycle, publishes Kafka events |
| **Audit Service** | `8082` | Consumes Kafka events, stores immutable audit logs |

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
| Apache Kafka | Asynchronous event messaging |
| PostgreSQL 15 | Data persistence |
| Hibernate / JPA | ORM for database access |
| Lombok | Boilerplate code reduction |
| springdoc-openapi | Swagger / OpenAPI documentation |
| Docker | Infrastructure (PostgreSQL + Kafka) |

---

## Prerequisites

- Java 21
- Docker Desktop (running)
- Maven wrapper included (no need to install Maven separately)

---

## How to Run the System

### Step 1 — Start Infrastructure (PostgreSQL + Kafka)

From the root of the project:

```bash
docker-compose up -d
```

This will start:
- **PostgreSQL** on port `5432` (with `governance_db` and `audit_db` schemas auto-created)
- **Zookeeper** on port `2181`
- **Kafka** on port `9092`

Verify containers are running:

```bash
docker-compose ps
```

---

### Step 2 — Start the Governance Service

Open a terminal in the `governance-service` directory:

```bash
cd governance-service
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # macOS/Linux
```

The service will start on **http://localhost:8080**

---

### Step 3 — Start the Audit Service

Open a **separate terminal** in the `audit-service` directory:

```bash
cd audit-service
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # macOS/Linux
```

The service will start on **http://localhost:8082**

---

## API Reference

### Governance Service — `http://localhost:8080`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/policies` | Create a new policy |
| `GET` | `/policies` | Get all policies |
| `GET` | `/policies/{id}` | Get a policy by ID |
| `POST` | `/policies/{id}/submit` | Submit a policy for approval |
| `POST` | `/policies/{id}/approve` | Approve a policy |
| `POST` | `/policies/{id}/reject` | Reject a policy |

**Example — Create a Policy:**
```bash
curl -X POST http://localhost:8080/policies \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Data Retention Policy",
    "description": "All data must be deleted after two years",
    "createdBy": "admin"
  }'
```

**Example — Submit for Approval:**
```bash
curl -X POST "http://localhost:8080/policies/1/submit?actor=admin"
```

**Example — Approve a Policy:**
```bash
curl -X POST "http://localhost:8080/policies/1/approve?actor=manager"
```

---

### Audit Service — `http://localhost:8082`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/audits` | Get all audit logs |
| `GET` | `/audits/policy/{policyId}` | Get audit logs for a specific policy |

---

## Swagger UI (Interactive API Docs)

Both services expose an interactive Swagger UI where you can test all endpoints directly from your browser — no extra tools needed.

| Service | Swagger UI |
|---------|-----------|
| Governance Service | http://localhost:8080/swagger-ui/index.html |
| Audit Service | http://localhost:8082/swagger-ui/index.html |

---

## Kafka Events

The Governance Service publishes an event to the `governance-events` topic on every policy action.

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
  "timestamp": "2026-03-14T10:30:00"
}
```

The Audit Service automatically consumes these events and persists them as audit records in the database.

---

## Database Schema

### Governance Service — `policies` table

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated identifier |
| `title` | VARCHAR | Policy title (required) |
| `description` | TEXT | Policy description |
| `status` | VARCHAR | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED` |
| `created_by` | VARCHAR | Creator username (required) |
| `created_at` | TIMESTAMP | Auto-set on creation |

### Audit Service — `audit_logs` table

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
# Governance Service (33 tests)
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
