# Passbook

**An open-source, self-hosted personal finance app that tells you where the money went, whether
you're on budget, and whether you'll be okay — without making you type your transactions in by hand.**

Most personal finance tools ask you to be a diligent data-entry clerk. That's the part nobody
sustains. Passbook treats the transaction pipeline as the product: bank data flows in, gets
categorised, and the dashboards are the payoff. Manual entry exists as a fallback and a
correction path, not as the way data normally arrives.

**Your data stays on your machine.** Every instance is single-tenant, run by you, on your own
hardware. There is no account to sign up for and no server of ours holding your ledger.

> **Status: v0.1.0 — the first release meant for someone other than its author to run.** The
> self-filling pipeline described above is the direction, not yet the reality: today transactions
> are entered through the UI. See [Roadmap](#roadmap) for what's built and what isn't,
> [CHANGELOG.md](CHANGELOG.md) for what changed, and
> [Before you self-host](#before-you-self-host) for what is still missing.

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

**First, once: create your `.env`.** Passbook ships no credentials, and will not start without
them.

```bash
cp .env.example .env
$EDITOR .env          # set POSTGRES_PASSWORD, JWT_SECRET, PGADMIN_DEFAULT_PASSWORD
```

Generate a good `JWT_SECRET` with `openssl rand -base64 32`. `.env` is gitignored and never
enters the Docker image.

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
# In a separate terminal, run the backend. Set DATABASE_URL to localhost: the default targets
# the `postgres` Compose service, which is not resolvable from your host.
DATABASE_URL=jdbc:postgresql://localhost:5432/myfinance ./mvnw -pl Launcher spring-boot:run
```

> The backend reads `POSTGRES_PASSWORD` and `JWT_SECRET` from your shell environment here, not
> from `.env` — `.env` is read by Docker Compose. Export them, or use `env $(cat .env | xargs)`.

```bash
# In another terminal, run the frontend
cd frontend
npm install
npm run dev
```

> Requires **Java 21** on your machine. Check with `java -version`.

---

## Deploying

To reach an instance over the internet — a test instance on GitHub Codespaces, a VPS, or anything
else with Docker — see **[docs/DEPLOYING.md](docs/DEPLOYING.md)**.

`docker-compose.deploy.yaml` is a separate, smaller stack: Postgres, the backend, and Caddy
serving the built frontend and proxying `/api` so both answer on one origin. No Kafka, no
Control Center, no Kafdrop, no pgAdmin, and nothing but the web server binds a port. The
`docker-compose.yaml` described above stays the development rig.

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

Schema changes are explicit, versioned Flyway migrations in
`Infrastructure/src/main/resources/db/migration/`, applied automatically at startup. Hibernate
runs with `ddl-auto=validate`, so a database that disagrees with the code stops startup instead of
being silently reshaped.

Upgrading an instance that predates migrations is safe: its existing schema is adopted as the
baseline, with no table dropped, recreated or emptied. See [docs/UPGRADING.md](docs/UPGRADING.md).

**Back up your data.** [docs/BACKUP.md](docs/BACKUP.md) — one command, and the restore is
exercised against a real PostgreSQL on every CI build rather than only being written down.

**pgAdmin** (http://localhost:5050) uses `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD`
from your `.env`.

---

## Before you self-host

v0.1.0 closes the release blockers the [constitution](.specify/memory/constitution.md) tracked as
Self-Hosting Obligations: schema migrations are explicit, credentials come from your environment,
backup/restore is documented and tested, releases are versioned with an upgrade path, and the
instance is protected by an admin login.

What is still true, and worth knowing before you trust it with records that matter:

- **No transport encryption of its own.** The app speaks plain HTTP and assumes localhost or a
  trusted network. TLS is yours to terminate: [docs/DEPLOYING.md](docs/DEPLOYING.md) covers doing
  that with Codespaces port forwarding or a Cloudflare Tunnel, both of which handle it for you.
  Do not put the app itself on a public port without something in front of it.
- **Transactions have no currency field.** Accounts carry `currencies` and `defaultCurrency`, but
  every transaction amount is implicitly in its account's default currency. Cross-currency
  transactions cannot be represented.
- **One integration test class is still disabled** (`BillGetControllerIntegrationTest`). The other
  nine run against a real PostgreSQL on every CI build.
- **It is 0.1.0.** The API is not frozen, and the pipeline that fills itself is still the roadmap
  rather than the reality.

Found a security problem? Please report it privately — see [`SECURITY.md`](SECURITY.md),
which also lists the limitations above as known and accepted, so you can tell them apart
from real vulnerabilities.

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
| Recurring auto-posting | `POST /recurring-series/post-due`, `POST /recurring-series/{id}/stop` |
| Forecast | `GET /cash-flow-forecast` |
| Statement import | `POST /statements/preview`, `POST /statements/ingest` |
| System | `GET /system/version` |

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

1. **Statement import** — **CSV delivered** (unreleased, on `main`): statements are parsed server-side and
   ingested idempotently, so re-importing overlapping date ranges is safe rather than duplicating,
   and two genuinely identical rows on the same day are both kept. **Still ahead**: CAMT.053 and
   MT940 for European banks, which attach to the same server-side seam.
2. **Auto-categorisation** — merchant string to category, learned from your corrections.
3. **Auto-posting confirmed recurring series** — **delivered** (unreleased, on `main`): a confirmed
   series now writes its own transactions as they come due, daily and on demand, catching up
   whatever was missed while the app was down. Nothing is posted for a period before you confirmed
   the series, and an occurrence already recorded is refused by the database rather than posted
   twice. When a statement import brings in the bank's own version of a transaction the app
   predicted, the prediction is superseded by a compensating entry, so the charge counts once.

   The honest shape of it: **a series posting to an account you never import will keep posting
   until you stop it.** Nothing outside the app confirms those transactions ever happened, so the
   ledger drifts toward what the app expects rather than what your bank did. Stop a series when it
   ends — the Stop action leaves everything already posted in place, which is what makes it
   different from dismissing a proposal.
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

Passbook is free software, licensed under the **GNU Affero General Public License v3.0 or later**
(AGPL-3.0-or-later). You may use, study, modify, and redistribute it under those terms. See
[`LICENSE`](LICENSE) for the full text.

The AGPL was chosen deliberately over a permissive licence. Its distinguishing clause is
**section 13**: if you run a modified version of Passbook and let other people use it over a
network, you must offer those users the source of your modified version. Self-hosting for
yourself, your household, or internal use triggers nothing — you are not distributing to anyone.
It only matters if you offer a modified Passbook *as a service to others*, in which case your
changes must be shared back rather than kept proprietary.

Passbook is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. Read the licence
before trusting it with financial records that matter to you.

### If you deploy a modified version

Section 13 obliges you to offer *your* users the source of the version they are actually using.
The UI carries a permanent "Get the source code" link in the footer for this purpose. If you have
modified Passbook and are letting others use it, point that link at your own repository by
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
├── Infrastructure/     JPA entities, repositories, persistence adapters, Flyway migrations
├── Events/             Kafka consumer — the seam for transaction ingestion
├── Launcher/           Spring Boot entry point
├── integration-tests/  TestContainers-based integration tests
├── frontend/           React 18 + TypeScript dashboard (Vite)
├── docs/               Operator guides — backup/restore, upgrading
├── specs/              Feature specifications and implementation plans
├── .specify/memory/    Project constitution — vision and engineering principles
└── docker-compose.yaml Full-stack infrastructure definition
```
