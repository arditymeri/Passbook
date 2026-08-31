# Implementation Plan: Device Sync via File Export

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-device-sync-export/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Let a user move their full data set between independent Passbook instances by exporting a
single JSON snapshot file from one device and importing it into another — no server-to-server
connection, matching the self-hosted trust model. Transactions (bills/incomes, including
correction-replacement and reversal rows) merge via the identity Constitution Principle II
already requires; five mutable entities (account, category, budget, recurring series, savings
goal) get a new `updatedAt` column so merge can pick whichever side changed more recently; bills
get a `necessityTagUpdatedAt` for the one thing about them sync ever arbitrates; and bills/incomes
get a new `recordedAt` (write-time, distinct from the transaction's own `time`) so that the one
genuinely hard case — two devices independently correcting the same original bill before ever
syncing — has a real, deterministic tie-breaker instead of silently producing two "current" values
for one bill. Entity matching falls back to each entity's existing database unique constraint
(name, or category+year+month) when two devices independently created "the same" thing before
their first sync, so merge never trips over a uniqueness violation it could have resolved.
Unlike feature 017's client-orchestrated CSV import, this merge is real new Domain business logic
(natural-key fallback, last-modified-wins, the correction tie-breaker, a fixed entity-processing
order) and lives behind two new endpoints — `GET /sync/export` and `POST /sync/import/preview` +
`POST /sync/import/apply`, both wrapping one shared, pure `computeMergePlan` — so the "what would
happen" preview and the "make it happen" apply can never drift apart.

## Technical Context

**Language/Version**: Java 21 (Domain/Application/Infrastructure/Launcher); TypeScript 5 / React 18 (frontend, Vite)

**Primary Dependencies**: Spring Boot 3.4.0, MapStruct (Application DTO↔API mapping), OpenAPI Generator (Application, delegate pattern), Spring Data JPA (Infrastructure — seven new nullable timestamp columns across five existing tables plus `bill`/`income`), MUI (frontend)

**Storage**: PostgreSQL — additive schema only: `updated_at` on `account`, `category`, `budget`, `recurring_series`, `savings_goal`; `necessity_tag_updated_at` and `recorded_at` on `bill`; `recorded_at` on `income`. All nullable, added via Hibernate `ddl-auto=update` per this project's current (pre-Flyway) state — same posture as every prior feature, not a new departure. No backfill: existing rows get `NULL`, which the merge logic treats as "older than anything with a real timestamp" (research.md R4).

**Testing**: JUnit 5 for the new Domain logic (Constitution Principle VI) — this is the most test-critical feature in the project so far: natural-key fallback matching (three different strategies across entity types), last-modified-wins per mutable entity type, the correction tie-breaker, dependency-ordered processing, and idempotent re-import. No frontend test runner exists anywhere in this repo (confirmed across every prior feature); the new export/import UI is hand-verified, consistent with 015-018.

**Target Platform**: Linux server (Docker Compose: Postgres + Kafka + app) + browser SPA. The export/import UI is the first feature needing the browser to *trigger a file download* (Blob + anchor `download`) — no such mechanism exists in this frontend yet, only file *upload* (established by feature 017's `<input type="file">` + `FileReader`).

**Project Type**: Web application — existing hexagonal Maven multi-module backend (Domain/Application/Infrastructure/Launcher) + Vite React SPA frontend

**Performance Goals**: Personal-finance scale (tens of accounts/categories, thousands of transactions across a few years) — a single JSON snapshot at that scale is a few MB at most; assembling, transmitting, and merging it synchronously in well under a few seconds, no pagination or streaming needed (matches how `GET /bills` already returns every bill unfiltered in one response today).

**Constraints**: Merge writes MUST go through each entity's persistence port directly (`Add*PersistencePort`, or the existing `save`-based `upsert`), never through the validating `Add*Service`/`Update*Service` layer — re-running origin-device validation (e.g. a duplicate-name rejection) against data that already passed it on the origin device risks incorrectly rejecting a legitimate incoming entity (research.md R5). Entity types MUST be processed in dependency order (research.md R6) so a referencing row is never written before what it references. An import MUST never delete anything locally (FR-011) — `computeMergePlan` only ever produces inserts and updates. The exported file MUST remain plain, human-readable JSON (FR-012) — no encryption dependency introduced.

**Scale/Scope**: Three new REST endpoints, seven new nullable timestamp columns across six tables, one substantial new Domain merge service (the largest single piece of new business logic in the project to date), and a new frontend Sync page (export button + import file-picker/preview/confirm flow).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Transaction Immutability (NON-NEGOTIABLE) | PASS | Imported bill/income rows are inserted verbatim (`recordedAt` aside), preserving `correctsTransactionId`/`reversal` exactly as recorded on the source device — merge never mutates an existing transaction row's financial facts. The correction tie-breaker (FR-006) never deletes the losing sibling; it stays permanently visible via `getHistory()`, exactly as an ordinary superseded correction already is. |
| II. Ingestion Is Idempotent (NON-NEGOTIABLE) | PASS | This feature is a direct, deliberate application of this principle to a new ingestion source (another device's export) rather than a bank statement — re-importing an already-seen transaction id is explicitly a no-op (FR-009), using the exact identity mechanism this principle already mandates. |
| III. Balance Derivation | PASS | Merge never writes a computed balance; only each account's stored *opening* balance (a mutable field, last-write-wins like any other) and the underlying bill/income rows a balance is summed from. |
| IV. Currency Precision (NON-NEGOTIABLE) | PASS | Every amount field in the snapshot is a decimal string on the wire (matching `correctBillRequest`'s existing convention) and `BigDecimal` end-to-end through Domain/Application — never a JSON number. |
| V. Audit Trail & Observability | N/A (pre-existing gap, not worsened) | Same posture as feature 018: no Domain service in this codebase does structured logging for state changes today. Incidentally, this feature *adds* a genuine append-timestamp (`recordedAt`) that didn't exist before, a small net improvement to observability, not a new logging obligation taken on. |
| VI. Test-First Development (NON-NEGOTIABLE) | PASS | `computeMergePlan` and its natural-key/tie-breaker logic get thorough JUnit coverage per entity type and conflict scenario, written alongside implementation. |
| VII. API Contract Stability | PASS | Three wholly new endpoints, OpenAPI-first (`contracts/sync-api.yaml` + `contracts/sync-model.yaml`). The only changes to *existing* contracts are additive optional fields (`updatedAt`, `recordedAt`, `necessityTagUpdatedAt`) on already-existing GET responses — non-breaking. |
| VIII. Hexagonal Architecture Compliance | PASS | The merge engine (`computeMergePlan` + apply) lives in a new Domain `service/sync/` package with zero Spring/JPA/Kafka dependency, reading/writing exclusively through existing and lightly-extended SPI ports; Application adds thin controllers + MapStruct mappers only. |

No violations requiring justification — Complexity Tracking table is empty/omitted. The scale of
schema change (seven columns across six tables) is the minimum FR-004/FR-006 already require, not
speculative.

## Project Structure

### Documentation (this feature)

```text
specs/019-device-sync-export/
├── plan.md               # This file (/speckit-plan command output)
├── research.md            # Phase 0 output (/speckit-plan command)
├── data-model.md          # Phase 1 output (/speckit-plan command)
├── quickstart.md          # Phase 1 output (/speckit-plan command)
├── contracts/             # Phase 1 output (/speckit-plan command)
│   ├── sync-api.yaml
│   └── sync-model.yaml
└── tasks.md               # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/bill/BillDto.java                         # MODIFIED: + recordedAt, necessityTagUpdatedAt
├── data/income/IncomeDto.java                      # MODIFIED: + recordedAt
├── data/account/AccountDto.java                    # MODIFIED: + updatedAt
├── data/category/CategoryDto.java                  # MODIFIED: + updatedAt
├── data/budget/BudgetDto.java                      # MODIFIED: + updatedAt
├── data/recurring/RecurringSeriesDto.java           # MODIFIED: + updatedAt
├── data/goal/SavingsGoalDto.java                    # MODIFIED: + updatedAt
├── data/sync/SyncSnapshotDto.java                   # NEW
├── data/sync/MergePlanDto.java                      # NEW (+ EntityMergeCounts.java, CorrectionConflict.java)
├── data/sync/ImportSummaryDto.java                  # NEW
├── api/ExportSyncSnapshotService.java                # NEW interface (port)
├── api/PreviewSyncImportService.java                 # NEW interface (port)
├── api/ApplySyncImportService.java                   # NEW interface (port)
├── api/GetBudgetService.java                         # MODIFIED: + getAll()
└── service/sync/
    ├── ExportSyncSnapshotServiceImpl.java             # NEW: reads every GetXxxService.getAll()
    ├── SyncEntityMatching.java                        # NEW: natural-key fallback matching, one method per entity type
    ├── ComputeMergePlanService.java                    # NEW: pure — no writes; dependency-ordered per-entity-type diff + correction tie-breaker
    ├── ApplyMergePlanService.java                       # NEW: writes a MergePlanDto through persistence ports directly, in dependency order
    ├── PreviewSyncImportServiceImpl.java                 # NEW: computeMergePlan → summarize, no writes
    └── ApplySyncImportServiceImpl.java                   # NEW: computeMergePlan → apply → summarize, one transaction

Domain/src/test/java/at/ymeri/my/finance/domain/service/sync/
├── ComputeMergePlanServiceTest.java                  # NEW: per-entity-type insert/update/unchanged, all three natural-key fallbacks, correction tie-breaker, idempotent re-plan
└── ApplyMergePlanServiceTest.java                    # NEW: dependency ordering, never-deletes guarantee

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/AccountEntity.java                        # MODIFIED: + updated_at
├── entity/CategoryEntity.java                       # MODIFIED: + updated_at
├── entity/BudgetEntity.java                         # MODIFIED: + updated_at
├── entity/RecurringSeriesEntity.java                 # MODIFIED: + updated_at
├── entity/SavingsGoalEntity.java                     # MODIFIED: + updated_at
├── entity/BillEntity.java                            # MODIFIED: + necessity_tag_updated_at, recorded_at
├── entity/IncomeEntity.java                          # MODIFIED: + recorded_at
├── adapter/postgres/account/UpdateAccountPostgresAdapter.java       # MODIFIED: bump updatedAt
├── adapter/postgres/category/UpdateCategoryPostgresAdapter.java     # MODIFIED: bump updatedAt
├── adapter/postgres/budget/SetBudgetPostgresAdapter.java            # MODIFIED: bump updatedAt
├── adapter/postgres/recurring/RecurringSeriesPostgresAdapter.java   # MODIFIED: bump updatedAt on status change + set on create
├── adapter/postgres/goal/SavingsGoalPostgresAdapter.java            # MODIFIED: bump updatedAt
├── adapter/postgres/bill/AddBillPostgresAdapter.java                # MODIFIED: set recordedAt on insert
├── adapter/postgres/bill/UpdateBillNecessityTagPostgresAdapter.java # MODIFIED: set necessityTagUpdatedAt
├── adapter/postgres/income/AddIncomePostgresAdapter.java            # MODIFIED: set recordedAt on insert
├── mapper/BillMapper.java, CategoryMapper.java, etc.                # MODIFIED: pick up new fields (mostly automatic, same-named)
└── repository/BudgetRepository.java                                 # unchanged — getAll() already exists

Application/src/main/resources/swagger/sync/
├── sync-export-controller.yaml       # NEW: GET /sync/export
├── sync-import-controller.yaml       # NEW: POST /sync/import/preview, /sync/import/apply
└── sync-model.yaml                   # NEW: syncSnapshot + importSummary schemas
Application/src/main/resources/swagger/{bill,income,account,category,budget,recurring}/*-model.yaml
                                       # MODIFIED: + updatedAt/recordedAt/necessityTagUpdatedAt on existing schemas (additive)

Application/src/main/java/at/ymeri/my/finance/
├── controller/sync/SyncExportController.java     # NEW
├── controller/sync/SyncImportController.java     # NEW
└── application/mapper/SyncMapper.java             # NEW

frontend/src/
├── types/index.ts                       # MODIFIED: + SyncSnapshot, ImportSummary, EntityMergeCounts, updatedAt/recordedAt fields on existing types
├── api/client.ts                        # MODIFIED: + fetchSyncExport(), previewSyncImport(snapshot), applySyncImport(snapshot)
├── utils/downloadFile.ts                # NEW: Blob + anchor `download` helper (first use of this pattern in the app)
├── components/SyncPage.tsx              # NEW: Export button; Import file picker → preview summary → confirm/cancel
└── App.tsx                              # MODIFIED: mount a Sync entry point (settings-style page, not a dashboard card)
```

**Structure Decision**: Existing hexagonal web-application layout (Domain/Application/
Infrastructure/Launcher + `frontend/`), unchanged. Launcher needs no changes — no new Spring bean
requires manual wiring beyond `@Service`/`@Component` scanning already in place.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
