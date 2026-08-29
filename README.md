# MyFinance

**An open-source, self-hosted personal finance app that tells you where the money went, whether
you're on budget, and whether you'll be okay — without making you type your transactions in by hand.**

Most personal finance tools ask you to be a diligent data-entry clerk. That's the part nobody
sustains. MyFinance treats the transaction pipeline as the product: bank data flows in, gets
categorised, and the dashboards are the payoff. Manual entry exists as a fallback and a
correction path, not as the way data normally arrives.

**Your data stays on your machine.** Every instance is single-tenant, run by you, on your own
hardware. There is no account to sign up for and no server of ours holding your ledger.

> **Status: pre-1.0, and honest about it.** The self-filling pipeline described above is the
> direction, not yet the reality — today transactions are entered through the UI. See
> [Roadmap](#roadmap) for what's built and what isn't, and
> [Before you self-host](#before-you-self-host) for what's missing before you should trust it
> with data you care about.

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, PostgreSQL, Kafka |
| Frontend | React 18, TypeScript, Vite |
| Infrastructure | Docker Compose |

The backend follows a hexagonal (ports & adapters) architecture across Maven modules, and REST
contracts are written as OpenAPI YAML before implementation. Development is spec-driven — every
feature has a specification in [`specs/`](specs/), and the project's engineering principles are
in [`.specify/memory/constitution.md`](.specify/memory/constitution.md).

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

> **⚠ Development configuration only.** Hibernate DDL auto is set to `update`, so the schema is
> inferred and applied on startup. This is convenient locally and **unsafe for real data** —
> it cannot express a migration, silently ignores destructive changes, and gives you no way to
> upgrade an existing database across versions predictably. Explicit migrations (Flyway or
> Liquibase) are a prerequisite for the first release intended for others to run.
>
> Database credentials are currently hardcoded in `Launcher/src/main/resources/application.properties`.
> They must move to environment variables before any real deployment.

**pgAdmin credentials** (http://localhost:5050):

| Field | Value |
|-------|-------|
| Email | admin@example.com |
| Password | admin |

---

## Before you self-host

The project is not yet ready to hold data you would be upset to lose. Known gaps, tracked as
release blockers in the [constitution](.specify/memory/constitution.md):

- **No schema migrations.** See the warning above. Upgrading between versions is not yet safe.
- **No secrets management.** Credentials live in `application.properties`.
- **No authentication.** The API is unauthenticated and assumes a trusted network. Do not expose
  it to the internet. This is by design — every instance is single-tenant, one household — but it
  means network placement is your only access control.
- **No backup/restore tooling or versioned releases** yet.
- **Integration tests are currently disabled** (WIP), so migration safety is unverified.
- **Transactions have no currency field.** Accounts carry `currencies` and `defaultCurrency`, but
  every transaction amount is implicitly in its account's default currency. Cross-currency
  transactions cannot be represented.

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

All paths are prefixed with `/api/v1`.

| Area | Endpoints |
|------|-----------|
| Categories | `GET/POST /categories`, `GET/PUT/DELETE /categories/{id}` |
| Expenses | `POST /createBill`, `GET /bills`, `GET /bill/{id}`, `PUT/DELETE /bills/{id}`, `GET /bills/{id}/history` |
| Income | `GET/POST /incomes`, `GET /incomes/{id}`, `PUT/DELETE /incomes/{id}`, `GET /incomes/{id}/history` |
| Accounts | `GET/POST /accounts`, `GET/PUT/DELETE /accounts/{id}` |
| Analysis | `GET /analysis/monthly`, `GET /analysis/period` |
| Budgets & envelopes | `GET/POST /budgets`, `DELETE /budgets/{id}`, `GET /budgets/status`, `POST /budgets/repeat`, `POST /budgets/transfer` |
| Savings goals | `GET/POST /savings-goals`, `GET/PUT/DELETE /savings-goals/{id}` |
| Recurring detection | `POST /recurring-series/detect`, `GET /recurring-series`, `GET /recurring-series/dashboard`, `POST /recurring-series/{id}/confirm`, `POST /recurring-series/{id}/dismiss` |
| Forecast | `GET /cash-flow-forecast` |

Net worth trend, spending trends, and transaction search are currently computed **client-side**
in `frontend/src/utils/` and have no backend endpoints.

`DELETE` on a bill or income is served by the correction controller, not a hard delete.
Corrections never mutate or destroy a transaction — they write a compensating reversal entry that
references the original, so history stays reconstructable. Account balances are always derived
from transaction history rather than stored as a running total.

Full interactive documentation is at **http://localhost:8080/swagger-ui.html** when the backend is running.

---

## Roadmap

The near-term work is the pipeline, not more dashboards. Today there are many features that
*consume* transactions and exactly one that *produces* them (a manual form).

1. **Statement import** — CSV, and CAMT.053 / MT940 for European banks. Requires no third party
   and removes most of the typing. Ingestion is idempotent, so re-importing overlapping date
   ranges is safe rather than duplicating.
2. **Auto-categorisation** — merchant string to category, learned from your corrections.
3. **Auto-posting confirmed recurring series** — detection already exists and stops at detection;
   confirmed series should write their own transactions.
4. **Bank synchronisation** — via a PSD2/Open Banking aggregator, read-only by construction (no
   payment initiation). This is planned as the project's paid, optional service, since aggregators
   charge per connection. It relays bookings and holds connection credentials only — **never your
   ledger, balances, or transaction history**, which stay on your instance.

Explicitly out of scope: double-entry bookkeeping, multiple users within one instance, tax
reporting, payment initiation, and investment performance tracking. See the constitution for the
reasoning.

---

## License

Copyright © 2023–2026 Ardit Ymeri.

MyFinance is free software, licensed under the **GNU Affero General Public License v3.0 or later**
(AGPL-3.0-or-later). You may use, study, modify, and redistribute it under those terms. See
[`LICENSE`](LICENSE) for the full text.

The AGPL was chosen deliberately over a permissive licence. Its distinguishing clause is
**section 13**: if you run a modified version of MyFinance and let other people use it over a
network, you must offer those users the source of your modified version. Self-hosting for
yourself, your household, or internal use triggers nothing — you are not distributing to anyone.
It only matters if you offer a modified MyFinance *as a service to others*, in which case your
changes must be shared back rather than kept proprietary.

MyFinance is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. Read the licence
before trusting it with financial records that matter to you.

### If you deploy a modified version

Section 13 obliges you to offer *your* users the source of the version they are actually using.
The UI carries a permanent "Get the source code" link in the footer for this purpose. If you have
modified MyFinance and are letting others use it, point that link at your own repository by
setting `VITE_SOURCE_URL` when building the frontend:

```bash
VITE_SOURCE_URL=https://github.com/you/your-fork npm run build
```

Unset, it links to upstream — correct for unmodified deployments, and not sufficient if you have
changed the code.

---

## Project Structure

```
.
├── Domain/             Business logic, domain services, port interfaces
├── Application/        REST controllers (generated from OpenAPI YAML), mappers
├── Infrastructure/     JPA entities, repositories, persistence adapters
├── Events/             Kafka consumer — the seam for transaction ingestion
├── Launcher/           Spring Boot entry point
├── integration-tests/  TestContainers-based integration tests
├── frontend/           React 18 + TypeScript dashboard (Vite)
├── specs/              Feature specifications and implementation plans
├── .specify/memory/    Project constitution — vision and engineering principles
└── docker-compose.yaml Full-stack infrastructure definition
```
