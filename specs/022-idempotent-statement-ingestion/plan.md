# Implementation Plan: Idempotent Statement Ingestion

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-idempotent-statement-ingestion/spec.md`

## Summary

Give Passbook the idempotent ingestion Principle II has demanded since ratification and never had:
an `external_id` on transactions, a partial unique index that makes re-ingestion a no-op, and a
server-side statement import that derives identity in exactly one place.

The design turns on one decision — **identity is a hash of (account, date, amount, description,
direction) plus an occurrence index within its own identity group**. That is what lets two genuine
coffees on the same day both survive while re-importing the statement stays a no-op, and research R1
works through why it converges to the same history whichever order overlapping statements arrive in.

The second decision that shapes everything: **uniqueness is enforced by the write, not by a lookup
before it.** A single `INSERT … ON CONFLICT DO NOTHING RETURNING` names exactly the rows that landed,
so the per-row report FR-011 requires comes from the write itself. Check-then-write is the obvious
approach and it is wrong under concurrency — both writers look, both see nothing, both write — which
is why FR-005 excludes that whole family.

Feature 017's client-side duplicate detection is deleted rather than kept. Two implementations of
"is this a duplicate" that can disagree is worse than either alone.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5 / React 18 (frontend)

**Primary Dependencies**: Spring Boot 3.4.0; Flyway 10.20.1 (from feature 021); Hibernate 6.6.2;
`spring-jdbc` 6.2.0 — already on Infrastructure's classpath transitively, so
`NamedParameterJdbcTemplate` needs no new dependency; **`org.apache.commons:commons-csv` 1.12.0** — the one
new dependency, explicitly versioned since Spring Boot does not manage it; resolution through this
environment's proxy was verified during planning; OpenAPI Generator 7.0.1
(delegate pattern); MapStruct 1.5.5

**Storage**: PostgreSQL. One nullable `varchar(255)` column added to `bill` and `income`, plus a
partial unique index on each. No backfill, no data movement.

**Testing**: JUnit 5 in Domain for parsing, identity derivation and occurrence indexing — all pure
computation, all runnable here. Testcontainers PostgreSQL in `integration-tests` for the migration,
the constraint, and a genuinely concurrent double-import test (Principle VI: real database, mocking
it is forbidden). No frontend test runner exists; `npm run build` type-checks.

**Target Platform**: Linux/macOS self-hosted, Docker Compose

**Project Type**: Multi-module Maven backend (hexagonal) + separate Vite SPA

**Performance Goals**: Not a performance feature. One constraint follows from the design: a
statement is ingested in a single round trip, not one per row (research R3) — a 400-row statement
must not be 400 database calls.

**Constraints**:

- **No Docker daemon here.** The migration, the constraint and the concurrency guarantee cannot be
  exercised locally. Unusually for this project, the *hardest* logic can be — identity derivation is
  pure Domain computation.
- **`V1` is frozen.** Flyway checksums applied migrations; "just add the column to the baseline" is
  the tempting wrong move and breaks every instance that has already run it.
- **Identity is write-once and never backfilled.** Retrofitting identities onto history that was
  never ingested asserts a provenance that did not happen.
- **Domain must reach the database only through ports**, so the `ON CONFLICT` SQL lives in an
  Infrastructure adapter — permitted explicitly by the constitution, forbidden in Domain.

**Scale/Scope**: Single household, single instance. Roughly: 1 migration, 1 new dependency, 2 new
Domain services + their ports, 1 new persistence adapter with raw SQL, 2 endpoints, ~6 new frontend
edits, and 3 deletions from feature 017.

## Constitution Check

*Constitution v2.1.0. Evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | Verdict | Reasoning |
|---|---|---|
| **I. Transaction Immutability** | ✅ Upheld | Ingestion is create-only. Nothing in this feature updates or deletes a transaction. A restated statement line arrives as a new transaction; correcting the old one stays the operator's decision through the existing reversal path (spec Edge Cases). |
| **II. Ingestion Is Idempotent** | ✅ **This feature is its implementation** | Principle II has been NON-NEGOTIABLE since ratification with nothing implementing it: no external-identity field exists anywhere, and the only import is a browser-side heuristic. FR-001–FR-006 are that principle stated as requirements. |
| **III. Balance Derivation** | ✅ N/A | No balance is stored or cached. Ingested transactions feed the same read-time derivation as every other transaction. |
| **IV. Currency Precision** | ✅ Upheld | Amounts stay `BigDecimal` end to end; the contract carries them as decimal *strings* rather than JSON numbers, matching how feature 019's sync model already avoids float coercion. The new column is `varchar`. |
| **V. Audit Trail** | ✅ Upheld | `AddBillPostgresAdapter` already stamps `recorded_at` at write time when the caller has not set one; ingested rows get a true write-time timestamp exactly as manual ones do. `external_id` additionally records *that* a row was ingested rather than typed — provenance the app has never had. |
| **VI. Test-First** | ✅ Upheld, and well-served | Unusually favourable here: identity derivation, occurrence indexing and CSV parsing are pure Domain computation, testable in plain JUnit with no context. The database guarantees get Testcontainers integration tests against a real PostgreSQL, as the principle requires. |
| **VII. API Contract Stability** | ✅ Upheld | Two new endpoints, specified in OpenAPI before implementation (`contracts/statement-ingestion-controller.yaml`). Purely additive — no existing endpoint changes shape. |
| **VIII. Hexagonal Architecture** | ✅ Upheld — see note | Domain gains two services and a port; the raw SQL lives in an Infrastructure adapter, which the Development Workflow permits exclusively there. |

**Principle VIII note — why parsing sits in Domain without a port.** Principle VIII requires ports
for *I/O*. Turning a string into a list of rows is computation: there is no external system to
mediate. A port here would exist for symmetry alone, which the constitution's prohibition on
speculative generality rules out. Keeping it in Domain also keeps it under fast, context-free tests,
which is what the principle wants. Should a future format need streaming or network access, that is
when the port earns its place (research R6).

**Pipeline-first bias — this is the point.** The Development Workflow says a feature consuming
transaction data should state how that data arrives without manual entry, and that the ratio is
watched. Roughly ten features consume transactions; two produce them, and both need supervision.
This is the first feature that makes transactions arrive in bulk, safely, without the operator
deciding what they already have.

**One design consequence worth stating at gate time, not discovering later.** The occurrence-index
design has a failure mode: a bank that splits a single calendar day across two statement files can
lose a repeated transaction (research R2, worked through in full). Every alternative is worse —
strict hashing loses *every* repeated transaction rather than a rare one, and asking the operator
puts data-entry work back in front of them. A source-supplied transaction id avoids it entirely and
is preferred whenever the statement carries one. **A cheap mitigation exists** — letting the operator
force-include a row the preview marked already-recorded — and is deliberately **not** in this plan,
because it is not in the spec's requirements and adding it silently would be scope expansion. It is
raised for the operator's decision.

**Gate result: PASS.** No violations requiring justification; Complexity Tracking is therefore empty
and omitted.

## Project Structure

### Documentation (this feature)

```text
specs/022-idempotent-statement-ingestion/
├── plan.md                                     # This file
├── spec.md                                     # Feature specification
├── research.md                                 # Phase 0 — 10 decisions, incl. the R1 convergence proof
├── data-model.md                               # Phase 1 — one column, and what is deliberately not modelled
├── quickstart.md                               # Phase 1 — 7 scenarios + what runs locally vs in CI
├── contracts/
│   └── statement-ingestion-controller.yaml     # Phase 1 — POST /statements/preview, /statements/ingest
├── checklists/requirements.md                  # From /speckit-specify — all items pass
└── tasks.md                                    # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
Infrastructure/
├── src/main/resources/db/migration/
│   └── V2__add_external_transaction_identity.sql # NEW — column + partial unique index, per table
├── src/main/java/.../entity/
│   ├── BillEntity.java                           # + externalId
│   └── IncomeEntity.java                         # + externalId
└── src/main/java/.../adapter/postgres/ingestion/
    └── IngestTransactionsPostgresAdapter.java    # NEW — the ON CONFLICT … RETURNING batch insert

Domain/
├── pom.xml                                       # + commons-csv (parsing is Domain computation)
└── src/main/java/at/ymeri/my/finance/domain/
    ├── data/ingestion/                           # NEW — StatementRow, RowOutcome, IngestionResult
    ├── api/
    │   ├── ParseStatementService.java            # NEW — CSV text → rows (+ derived identity)
    │   └── IngestTransactionsService.java        # NEW — rows → outcomes. Reusable by a future consumer
    ├── service/ingestion/
    │   ├── ParseStatementServiceImpl.java        # NEW — parsing, rejection reasons
    │   ├── ExternalIdentityFactory.java          # NEW — the hash + occurrence index. The heart of it.
    │   ├── SuggestCategoryService.java           # NEW — 017's rule, moved server-side
    │   └── IngestTransactionsServiceImpl.java    # NEW
    └── spi/ingestion/
        └── IngestTransactionsPersistencePort.java # NEW — "insert these, tell me which landed"

Application/
├── pom.xml                                       # + openapi-generator execution: statement-ingestion
├── src/main/resources/swagger/statement/
│   └── statement-ingestion-controller.yaml       # NEW — copied from contracts/
└── src/main/java/.../controller/statement/
    └── StatementIngestionController.java         # NEW — implements the generated delegate

integration-tests/src/test/java/.../
├── StatementIngestionIntegrationTest.java        # NEW — quickstart scenarios 1, 2, 3, 6, 7
└── ConcurrentIngestionIntegrationTest.java       # NEW — SC-004, genuinely concurrent

frontend/src/
├── utils/transactionImport.ts                    # parseImportFile, detectDuplicates,
│                                                 #   suggestCategory DELETED
├── api/client.ts                                 # + previewStatement, ingestStatement (multipart)
├── types/index.ts                                # + StatementPreview, IngestionResult, RowStatus
└── components/ImportTransactionsDialog.tsx       # same shape, every judgement now server-side
```

**Structure Decision**: the existing hexagonal layout, unchanged. Three placements were deliberate:

1. **Parsing and identity derivation in Domain, not behind a port** — computation, not I/O
   (Principle VIII note above). This is what makes the feature's hardest logic testable on a machine
   with no database.
2. **Two Domain services rather than one.** `ParseStatementService` and `IngestTransactionsService`
   split so that a future caller with already-structured input — the `BookingConsumer` stub that
   today logs and returns — can use ingestion without fabricating CSV. That is FR-016, and splitting
   now costs nothing.
3. **The raw SQL in an Infrastructure adapter**, which the Development Workflow permits exclusively
   there and forbids in Domain.

**On the `commons-csv` dependency**: Domain has no `<dependencies>` block of its own and inherits
the parent's, so the dependency is declared in `Domain/pom.xml` where the code that uses it lives —
not in the parent, which would put a CSV library on every module's classpath for no reason.

## Phase Ordering Note

`/speckit-tasks` will sequence this, but three constraints are not obvious from the story priorities
and must survive into `tasks.md`:

1. **Verify the generated multipart signature before writing the controller.** Every existing
   endpoint here is JSON-in/JSON-out; `multipart/form-data` under the delegate pattern is untried in
   this project, and the generated parameter type is not predictable. Generate and *look*, exactly
   as the `JsonNullable` surprises in features 018 and 019 taught. Doing this first costs minutes;
   discovering it after the controller is written costs a rewrite.
2. **The `ON CONFLICT` conflict target must repeat the partial index predicate.** Inferring a
   partial unique index requires `ON CONFLICT (account_id, external_id) WHERE external_id IS NOT
   NULL`. Omit the `WHERE` and PostgreSQL raises *"no unique or exclusion constraint matching the ON
   CONFLICT specification"* — at runtime, in CI, not at compile time.
3. **The frontend deletions come last, in the same increment as the server path landing.** Removing
   `detectDuplicates` before `/statements/preview` works leaves the app with no duplicate detection
   at all; removing it after leaves two implementations that can disagree. It belongs in one commit
   with the dialog rewrite.
