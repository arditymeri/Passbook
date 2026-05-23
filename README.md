# MyFinance

A personal finance application with a Spring Boot backend and a React frontend.

Track income, expenses, spending by category, and monthly budgets — all visible in a single-page dashboard.

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, PostgreSQL, Kafka |
| Frontend | React 18, TypeScript, Vite |
| Infrastructure | Docker Compose |

---

## Running the Full Stack

### Option A — Everything in Docker (recommended)

Builds the backend JAR inside a container and starts all services (Postgres, Kafka, the Spring Boot app).

```bash
docker-compose up --build
```

Then start the frontend separately (the Vite dev server cannot run inside Docker for local development):

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** in your browser.

> The Vite dev server proxies all `/api` requests to the backend at `localhost:8080`, so there are no CORS issues.

---

### Option B — Backend on host, infrastructure in Docker

Start only the infrastructure services (Postgres + Kafka), then run the Spring Boot app from your IDE or terminal:

```bash
# Start only Postgres and Kafka (skip the finance-app container)
docker-compose up postgres kafka kafdrop
```

```bash
# In a separate terminal, run the backend
./mvnw -pl Launcher spring-boot:run
```

```bash
# In another terminal, run the frontend
cd frontend
npm install
npm run dev
```

> Requires **Java 21** on your machine. Check with `java -version`.

---

## Ports

| Service | URL |
|---------|-----|
| Frontend (Vite dev) | http://localhost:5173 |
| Backend (Spring Boot) | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| pgAdmin | http://localhost:5050 |
| Kafdrop (Kafka UI) | http://localhost:9000 |
| Confluent Control Center | http://localhost:9021 |

---

## Database

PostgreSQL runs at `localhost:5432`, database `myfinance`, user `diti`.

Hibernate DDL auto is set to `update` — the schema is created and migrated automatically on first startup. No manual migration scripts are needed.

**pgAdmin credentials** (http://localhost:5050):

| Field | Value |
|-------|-------|
| Email | admin@example.com |
| Password | admin |

---

## Running Tests

```bash
# All tests
./mvnw test

# Domain unit tests only
./mvnw -pl Domain test

# Integration tests (requires Docker)
./mvnw -pl integration-tests test
```

---

## API Overview

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/categories` | Create a spending category |
| `GET  /api/v1/categories` | List all categories |
| `POST /api/v1/bills` | Record an expense |
| `GET  /api/v1/bills` | List all expenses |
| `POST /api/v1/incomes` | Record an income entry |
| `GET  /api/v1/incomes` | List all income entries |
| `POST /api/v1/accounts` | Create an account |
| `GET  /api/v1/analysis/monthly` | Monthly income/expense summary |
| `GET  /api/v1/analysis/period` | Summary for a date range |
| `POST /api/v1/budgets` | Set or update a monthly category budget |
| `GET  /api/v1/budgets/status` | Budget vs. actual spending for a month |

Full interactive documentation is available at **http://localhost:8080/swagger-ui.html** when the backend is running.

---

## Project Structure

```
.
├── Domain/             Business logic, domain services, port interfaces
├── Application/        REST controllers (generated from OpenAPI YAML), mappers
├── Infrastructure/     JPA entities, repositories, persistence adapters
├── Events/             Kafka consumer
├── Launcher/           Spring Boot entry point
├── integration-tests/  TestContainers-based integration tests
├── frontend/           React 18 + TypeScript dashboard (Vite)
├── specs/              Feature specifications and implementation plans
└── docker-compose.yaml Full-stack infrastructure definition
```
