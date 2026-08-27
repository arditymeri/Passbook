# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# --- Frontend ---
# Install dependencies
cd frontend && npm install

# Start dev server (http://localhost:5173)
cd frontend && npm run dev

# Production build
cd frontend && npm run build

# --- Backend ---
# Build all modules
./mvnw clean package

# Run the application (requires Docker services running)
./mvnw -pl Launcher spring-boot:run

# Start all infrastructure (Postgres, Kafka, app)
docker-compose up

# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw -pl Domain test
./mvnw -pl integration-tests test

# Run a specific test class
./mvnw -Dtest=AddBillServiceImplTest test

# Run a specific test method
./mvnw -Dtest=AddBillServiceImplTest#addBill test

# Regenerate OpenAPI code (done automatically during build)
./mvnw -pl Application generate-sources
```

## Architecture Overview

This is a **Hexagonal Architecture (Ports & Adapters)** Spring Boot 3.4.0 / Java 21 personal finance app with a React frontend, split into Maven modules (backend) and a separate frontend app:

```
root/
├── frontend/       — React 18 + TypeScript SPA (Vite)
├── Domain          — Business logic, service interfaces (API ports), DTOs, persistence ports (SPI)
├── Application     — REST controllers (generated from OpenAPI YAML), MapStruct mappers (DTO ↔ API model)
├── Infrastructure  — Persistence adapters (SPI implementations), JPA entities, Spring Data repositories
├── Events          — Kafka consumer (listens to "booking.topic")
├── Launcher        — Spring Boot entry point; assembles all modules, holds application.properties
└── integration-tests — TestContainers-based integration tests (currently WIP / @Disabled)
```

**Dependency rule:** Domain has no upward dependencies. Application and Infrastructure both depend on Domain. Launcher depends on everything.

### Adding a New Feature (e.g., a new domain concept)

1. **Domain** — Define service interfaces, DTOs, and persistence port interfaces.
2. **Application** — Write an OpenAPI YAML spec in `Application/src/main/resources/swagger/<feature>/`, then run `generate-sources`. Implement the generated delegate interface in a controller class. Add a MapStruct mapper.
3. **Infrastructure** — Implement the persistence port with a `*PostgresAdapter`, a `@Entity` class, and a Spring Data `JpaRepository`.
4. **Launcher** — No changes needed unless new Spring beans require explicit wiring.

### OpenAPI Code Generation

REST controller interfaces and API model classes are **generated** from YAML specs in `Application/src/main/resources/swagger/`. The generator uses the **delegate pattern** (`interfaceOnly=true`, `delegatePattern=true`), so hand-written controllers implement the generated `*Delegate` interface. Generated sources land in `Application/target/generated-sources/` and are committed. Use `skipOverwrite=true`, so regenerating only creates new files — existing generated files are not overwritten.

### Kafka

The `Events` module contains a `BookingConsumer` annotated with `@KafkaListener` on topic `booking.topic` (group `booking.group.id`). Offset reset is set to `earliest` (resets to beginning on partition assignment). A second topic `transaction.topic` is configured in Docker Compose but not yet consumed.

### Infrastructure

- PostgreSQL at `jdbc:postgresql://postgres:5432/myfinance` (user `diti`)
- Hibernate DDL auto: `update` (schema managed automatically)
- Swagger UI available at `/swagger-ui.html` when app is running
- pgAdmin at `:5050`, Kafdrop at `:9000`, Confluent Control Center at `:9021`

## Frontend

Located in `frontend/`. Built with **React 18 + TypeScript** using **Vite**.

```
frontend/
├── src/
│   ├── assets/       — Static assets
│   ├── App.tsx       — Root component
│   └── main.tsx      — Entry point
├── public/           — Public static files
├── index.html
├── vite.config.ts
└── tsconfig.json
```

- Dev server runs at `http://localhost:5173`
- Communicates with the Spring Boot backend at `http://localhost:8080`
- Add new pages/components under `src/`; configure the Vite dev proxy in `vite.config.ts` to forward `/api` requests to the backend

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
at `specs/011-savings-goals/plan.md`.
<!-- SPECKIT END -->
