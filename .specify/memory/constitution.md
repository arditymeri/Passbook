<!--
SYNC IMPACT REPORT
==================
Version change: 2.0.0 → 2.1.0 (MINOR — out-of-scope boundary narrowed, no principle
  added/removed/renamed)

Redefined:
  - Deliberately Out of Scope, "Multi-user within a single instance" bullet — the blanket
    "No auth" was written to rule out multi-tenancy (separate accounts, `userId`, row-level data
    isolation), but read literally it also blocked a single shared instance-level credential gate,
    which creates no tenancy and no `userId` at all. Narrowed to explicitly permit that gate while
    keeping multi-user/userId/row-level-tenancy/signup out of scope, unchanged.

Added:
  - Self-Hosting Obligations — a bullet requiring an instance-level auth gate before distributing
    to third parties (an unauthenticated instance reachable on a network is not an acceptable
    default once "distribution to third parties is a commitment" applies).

Rationale: feature 020 (single-user authentication — one admin credential protecting the whole
  instance) was blocked by the prior literal wording despite not being "multi-user" in the sense
  the bullet exists to prevent. This amendment resolves that gate failure ahead of feature 020's
  planning phase.

Templates: plan-template.md derives its gates from this file (no hardcoded principle list) — no
  change required. spec-template.md, tasks-template.md — no change required (neither references
  the out-of-scope list directly).
-->

# Passbook Constitution

## Vision

**Passbook is an open-source, self-hosted personal finance app that answers all three
questions — where the money went, whether I'm on budget, and whether I'll be okay — without
making you type your transactions in by hand.**

Three jobs, one spine. The breadth (analysis, budgets, envelopes, goals, search, net worth,
forecast, trends) is the payoff; the spine is a transaction pipeline that fills itself. Manual
entry is a fallback and an exception path, never the primary way data arrives.

**Audience**: individuals running their own instance. Every instance is single-tenant. The
trust story is structural, not promissory: the data never leaves the operator's machine.

**Sustainability**: bank synchronisation is the paid service. The aggregator charges per
connection, so the paid line is the part that genuinely costs money to operate. The sync relay
forwards bookings and holds connection credentials; it MUST NOT hold user ledgers, balances, or
transaction history.

## Core Principles

### I. Transaction Immutability (NON-NEGOTIABLE)

Financial transactions MUST never be silently modified or deleted after they are persisted.
Corrections MUST be made via compensating/reversal entries that reference the original
(`correctsTransactionId`, `reversal`). The original row remains untouched. Any soft-delete or
status flag that hides a transaction from balances MUST preserve the original row and create a
reversal so the ledger nets to zero on the corrected amount.

**Rationale**: An imported bank booking is a statement of fact and is not the operator's to
edit. Corrections are opinions layered on facts. Keeping them distinct is what makes
re-importing safe and reconciliation possible.

### II. Ingestion Is Idempotent (NON-NEGOTIABLE)

Any transaction arriving from outside the UI MUST carry a stable external identity (bank
transaction id, or a deterministic hash of the fields when the source provides none). Re-ingesting
an already-seen identity MUST be a no-op, not a duplicate. Overlapping statement ranges are the
normal case, not an error.

**Rationale**: This is the load-bearing invariant of the vision. A pipeline that fills itself is
worthless if it also duplicates; the operator would be back to manual reconciliation, which is
the problem being solved.

### III. Balance Derivation

Current balances MUST be computed by summing transaction history, never read from a stored
running total. `account.balance` is the **opening** balance — the starting point before any
recorded transaction — and is the only balance figure permitted in storage. Current balance =
opening + income − bills, computed at read time. A materialised current balance is permitted
only as a read-optimisation and MUST be invalidated atomically with the transaction causing it.

**Rationale**: A mutable current-balance column drifts from history and corrupts silently.
Deriving it makes reconciliation against a bank statement a straightforward comparison.

### IV. Currency Precision (NON-NEGOTIABLE)

Monetary amounts MUST be `java.math.BigDecimal` (backend) or an equivalent fixed-point type
elsewhere. Floating-point types for money are FORBIDDEN. Rounding MUST use `RoundingMode.HALF_EVEN`
and be applied only at presentation, never mid-calculation. Currency codes follow ISO 4217.

**Rationale**: IEEE-754 error compounds across thousands of transactions into balance
discrepancies.

### V. Audit Trail & Observability

Every state-changing operation on financial data (create, reverse, categorise, import,
reconcile) MUST record a UTC ISO-8601 timestamp, the source of the change (manual entry,
import, rule engine, recurring auto-post), and the resulting state change. Structured logging
(JSON) is REQUIRED in production. Account identifiers and amounts MUST NOT appear in
unstructured log strings at WARN or above without masking.

**Rationale**: When the pipeline writes transactions on the operator's behalf, "why is this row
here?" must be answerable. Source provenance matters more than actor identity in a single-tenant
app — but it becomes far more important, not less, once rows arrive automatically.

### VI. Test-First Development (NON-NEGOTIABLE)

All financial calculation and business-rule logic in Domain MUST be covered by unit tests
written before or alongside the implementation. Integration tests MUST verify persistence
adapters against a real database (TestContainers); mocking the database layer in integration
tests is FORBIDDEN.

**Rationale**: Finance bugs are invisible until they compound. This binds harder now than it did
as a solo project: a broken migration or balance bug reaches other people's data.

### VII. API Contract Stability

Public REST contracts MUST be defined in OpenAPI YAML before implementation. Breaking changes
(field removal, type change, endpoint removal) MUST go under a new version path (`/v2/...`).
Additive optional fields are non-breaking. Deprecated endpoints MUST remain functional for at
least one release cycle.

**Rationale**: The frontend and any third-party sync client depend on stable contracts.

### VIII. Hexagonal Architecture Compliance

The Domain module MUST NOT depend on JPA, Kafka, HTTP, or any Spring runtime machinery, and
Domain logic MUST be exercisable in plain JUnit with no application context. Spring *stereotype
annotations* (`@Service`, `@Component`) are permitted in Domain for wiring; anything requiring a
running context is not. All I/O MUST be mediated through port interfaces defined in Domain and
implemented in Application or Infrastructure.

**Rationale**: Context-free domain tests run in milliseconds and keep financial invariants legible.
Banning the annotations too was never enforced and bought nothing beyond what this rule already
gives.

## Financial Data Standards

- **Date/Time**: All timestamps stored and transmitted as UTC. Display conversion at the
  presentation layer only. Use `java.time.Instant` or `OffsetDateTime` in Domain; never
  `java.util.Date` or `Calendar`.
- **Account Types**: `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `CASH`, `INVESTMENT`. Sign convention
  is carried by the transaction (bill vs. income), not by an account's normal-balance rule.
- **Multi-Currency (KNOWN GAP)**: Accounts carry `currencies` and `defaultCurrency` (ISO 4217).
  **Transactions carry no currency field** — every amount is implicitly in the account's default
  currency. Cross-currency transactions are therefore not representable. This is the first
  standard to close if the self-hosted audience is international, and it MUST be closed before
  bank sync ingests from a multi-currency institution.

## Deliberately Out of Scope

Not oversights. Reopening any of these requires the amendment process below.

- **Double-entry / journal-line accounting.** This is a personal finance app, not a bookkeeping
  system. Bills and incomes are single-line records.
- **Multi-user within a single instance.** No `userId`, no row-level tenancy, no per-account data
  isolation, no signup flow. One instance, one household. Sharing is achieved by running an
  instance, not by signing up. This does **not** rule out a single shared instance-level
  credential gate (one admin username/password protecting the whole instance) — that creates no
  `userId` and no tenancy; it only protects the one instance that already exists, and is required
  before self-hosting reaches anyone but the operator (see Self-Hosting Obligations).
- **Managed hosting of user ledgers.** The paid service is sync only. Holding other people's
  transaction history would impose GDPR/DPIA obligations the project is not structured to carry.
- **Tax reporting, payment initiation, investment performance tracking.**

## Self-Hosting Obligations

Distribution to third parties is a commitment. Before any release intended for others to run:

- **Schema migrations MUST be explicit** (Flyway or Liquibase). `spring.jpa.hibernate.ddl-auto=update`
  is FORBIDDEN outside local development — upgrading a stranger's data cannot ride on Hibernate
  inferring intent.
- **No credentials in version control.** All secrets via environment variables.
- **Versioned releases with a documented upgrade path**, and documented backup/restore.
- **Integration tests MUST be enabled and green.** They are the only guard on other people's
  migrations.
- **An instance-level authentication gate MUST be enabled.** An unauthenticated instance reachable
  on a network is not an acceptable default once run by anyone but the operator testing locally.
  This is the single shared credential gate permitted under Deliberately Out of Scope, not a
  multi-user system.

## Development Workflow

- **Spec-first**: Every feature MUST have a reviewed `spec.md` before implementation. OpenAPI YAML
  for new endpoints is part of the spec, not the implementation.
- **Constitution Check**: Every plan MUST verify compliance with Principles I–VIII before Phase 0
  research is complete.
- **Pipeline-first bias**: A feature that consumes transaction data SHOULD state how that data
  arrives without manual entry. Consumer features are not forbidden, but the ratio is watched.
- **Code Review gate**: PRs touching Domain financial logic require a reviewer to confirm
  Principles I (immutability), II (idempotent ingestion), and IV (currency precision).
- **No direct SQL in Domain**: raw SQL/JPQL belongs exclusively in Infrastructure adapters.
- **No floating-point money**: CI MUST fail (ArchUnit or equivalent) if `double`/`float` money
  fields appear in Domain DTOs or entities.

## Governance

This Constitution supersedes all other documented practices in this repository. It describes what
the code does and what the project commits to — a principle that the code does not honour is a bug
in one of the two, and MUST be resolved rather than left standing. Amendments require:

1. A written rationale explaining why the current principle is insufficient.
2. An impact assessment against existing features and tests.
3. A migration plan if existing code must change to comply.
4. Version increment per semantic rules (MAJOR for removals/redefinitions, MINOR for additions,
   PATCH for clarifications).

All PRs and feature reviews MUST verify compliance. Complexity MUST be justified by a
constitutional principle; gold-plating or speculative generality is prohibited.

Refer to `CLAUDE.md` for runtime development commands and project structure guidance.

**Version**: 2.1.0 | **Ratified**: 2026-05-23 | **Last Amended**: 2026-09-01
