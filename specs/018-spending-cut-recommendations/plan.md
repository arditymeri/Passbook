# Implementation Plan: Spending Cut Recommendations

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-spending-cut-recommendations/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Turn signals the app already computes — confirmed recurring series, budget status, spending
trends — plus one new user-driven signal (a Necessary/Avoidable/Unnecessary tag on individual
bills) into a single prioritized, actionable list of cost-cutting opportunities with one combined
"potential monthly savings" total. Two genuinely new pieces of backend logic are required: (1) a
persisted necessity tag on `Bill`, updated in place outside the correction/reversal mechanism
since it is not a financial fact; and (2) a monthly-equivalent recurring-cost ranking with
cumulative (first-vs-latest) price-creep detection, reusing the existing shared
`RecurringSeriesMembers` component. The over-budget and trending-up category signals, and the
final combination/dedup/total, are assembled client-side from data already exposed today
(`GET /budgets/status`, the existing `computeSpendingTrends` utility from feature 016, and the
existing `GET /bills`/`GET /incomes`), matching this repo's established pattern of pushing
presentation-layer aggregation to the frontend once the backend exposes the raw signals.

## Technical Context

**Language/Version**: Java 21 (Domain/Application/Infrastructure/Launcher); TypeScript 5 / React 18 (frontend, Vite)

**Primary Dependencies**: Spring Boot 3.4.0, MapStruct (Application DTO↔API mapping), OpenAPI Generator (Application, delegate pattern), Spring Data JPA (Infrastructure — one new nullable column on the existing `bill` table), MUI (frontend)

**Storage**: PostgreSQL — one new nullable column, `bill.necessity_tag` (varchar), added via Hibernate `ddl-auto=update` exactly like every other column in this project's current (pre-Flyway) state; no backfill needed since every existing row is valid as untagged (`NULL`). Everything else this feature reads (recurring series, bills, incomes, budgets) is existing data, computed at read time and never cached, per Constitution Principle III.

**Testing**: JUnit 5 for the new Domain logic (Constitution Principle VI): monthly-equivalent normalization per frequency, price-increase detection (above/at/below the existing tolerance rule, and explicitly not-flagged on a decrease), and the necessity-tag update service's validation/not-found paths. No frontend test runner exists anywhere in this repo (confirmed across every prior feature); the new `computeSpendingCutRecommendations` utility is hand-verified against worked examples instead, consistent with `computeSpendingTrends` (016) and `transactionImport.ts` (017).

**Target Platform**: Linux server (Docker Compose: Postgres + Kafka + app) + browser SPA

**Project Type**: Web application — existing hexagonal Maven multi-module backend (Domain/Application/Infrastructure/Launcher) + Vite React SPA frontend

**Performance Goals**: Personal-finance scale (tens of confirmed series, hundreds of bills) — both new endpoints and the frontend combination synchronously compute in well under a second, no external calls.

**Constraints**: The necessity-tag mutation MUST touch only the `necessity_tag` column — it MUST NOT go through `CorrectBillServiceImpl`'s reversal mechanism (research.md R2), and MUST be propagated onto a bill's replacement row when that bill is later corrected through the existing mechanism (research.md R3), so a tag is never silently lost. Recurring cost ranking MUST reuse `RecurringSeriesMembers`/`RecurringMatching.isWithinAmountTolerance` rather than re-deriving series membership or a new tolerance rule (research.md R4). Category signals MUST reuse the existing `GetBudgetStatusService` and `computeSpendingTrends` rather than re-implementing either (research.md R5). Adding the `necessity_tag` column via `ddl-auto=update` is consistent with — not a new departure from — this project's pre-existing, project-wide gap against the Self-Hosting Obligations section's "explicit migrations" requirement; closing that gap for the whole project is out of scope for this feature.

**Scale/Scope**: Two new REST endpoints (one mutation, one read), one new persisted column, one new frontend page/section and one new frontend combining utility.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Transaction Immutability (NON-NEGOTIABLE) | PASS | The one field this feature updates in place, `necessityTag`, is deliberately scoped outside the correction/reversal mechanism because it is the user's own judgment about a bill, not a claim about what happened — see research.md R2 for the full justification. Every other field (`amount`, `time`, `categoryId`, `accountId`) remains changeable only via the existing, untouched `CorrectBillServiceImpl`/`BillCorrections` reversal path. |
| II. Ingestion Is Idempotent (NON-NEGOTIABLE) | N/A | This feature adds no new ingestion path. The necessity-tag write is a direct, single, idempotent-by-nature user action from the UI (setting a value to X twice yields the same result), not data arriving from an external source with duplicate risk. |
| III. Balance Derivation | N/A | This feature reads but never writes any balance figure, and derives every recommendation at read time — nothing is cached (mirrors feature 015's forecast). |
| IV. Currency Precision (NON-NEGOTIABLE) | PASS | New backend amount fields (`monthlyEquivalentAmount`, `originalAmount`, `increaseAmount`) are `BigDecimal` end-to-end through Domain/Application, matching every existing money field. Frontend continues the established, previously-accepted precedent (012-017) of JS `number` for display only. |
| V. Audit Trail & Observability | N/A (pre-existing gap, not worsened) | No Domain service in this codebase currently does structured logging for any state-changing operation — confirmed by checking `AddBillServiceImpl`, `CorrectBillServiceImpl`, `UpdateCategoryServiceImpl`, etc.; none log. Adding logging to only the new `UpdateBillNecessityTagServiceImpl` while every other, pre-existing mutation stays silent would be an inconsistent, one-off pattern rather than genuine compliance. This feature follows the same (gapped) convention as everything around it; closing the gap project-wide is out of scope here, same posture as the frontend-test-runner and `ddl-auto` gaps noted elsewhere in this plan. |
| VI. Test-First Development (NON-NEGOTIABLE) | PASS | New Domain business-rule logic (monthly-equivalent conversion, price-creep tolerance check, necessity-tag validation) gets JUnit tests written alongside implementation per user story. |
| VII. API Contract Stability | PASS | Two new endpoints defined in OpenAPI YAML first (`contracts/necessity-tag-api.yaml` + `contracts/necessity-tag-model.yaml`, `contracts/recurring-cost-summary-api.yaml` + `contracts/recurring-cost-summary-model.yaml`). The only change to an existing contract is one new optional/nullable field (`necessityTag`) added to the existing `bill` schema — additive, non-breaking. |
| VIII. Hexagonal Architecture Compliance | PASS | New Domain logic (`UpdateBillNecessityTagServiceImpl`, `GetRecurringCostSummaryServiceImpl`) has zero Spring/JPA/Kafka dependency; new ports (`UpdateBillNecessityTagPersistencePort`) defined in Domain and implemented in Infrastructure; Application adds thin controllers plus MapStruct mapper additions only. |

No violations requiring justification — Complexity Tracking table is empty/omitted.

## Project Structure

### Documentation (this feature)

```text
specs/018-spending-cut-recommendations/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   ├── necessity-tag-api.yaml
│   ├── necessity-tag-model.yaml
│   ├── recurring-cost-summary-api.yaml
│   └── recurring-cost-summary-model.yaml
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/bill/BillDto.java                          # MODIFIED: + necessityTag
├── data/bill/NecessityTag.java                      # NEW: enum NECESSARY | AVOIDABLE | UNNECESSARY
├── data/recurring/RecurringCostSummaryItemDto.java  # NEW
├── spi/bill/UpdateBillNecessityTagPersistencePort.java  # NEW
├── api/UpdateBillNecessityTagService.java           # NEW interface (port)
├── api/GetRecurringCostSummaryService.java          # NEW interface (port)
└── service/
    ├── bill/
    │   ├── UpdateBillNecessityTagServiceImpl.java   # NEW
    │   └── CorrectBillServiceImpl.java              # MODIFIED: replacement() carries necessityTag forward
    └── recurring/
        └── GetRecurringCostSummaryServiceImpl.java  # NEW: reuses RecurringSeriesMembers + RecurringMatching

Domain/src/test/java/at/ymeri/my/finance/domain/
├── service/bill/UpdateBillNecessityTagServiceImplTest.java  # NEW
└── service/recurring/GetRecurringCostSummaryServiceImplTest.java  # NEW

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/BillEntity.java                           # MODIFIED: + necessity_tag column
├── mapper/BillMapper.java                            # unchanged (MapStruct picks up the new same-named field automatically)
└── adapter/postgres/bill/UpdateBillNecessityTagPostgresAdapter.java  # NEW

Application/src/main/resources/swagger/bill/
├── bill-model.yaml                        # MODIFIED: + necessityTag on the `bill` schema, + necessityTag enum, + updateNecessityTagRequest
└── bill-necessity-tag-controller.yaml      # NEW: PUT /bills/{id}/necessity-tag

Application/src/main/resources/swagger/recurring/
├── recurring-model.yaml                     # MODIFIED: + recurringCostSummaryItem, recurringCostSummaryResponse
└── recurring-cost-summary-controller.yaml   # NEW: GET /recurring-series/cost-summary

Application/src/main/java/at/ymeri/my/finance/
├── controller/bill/BillNecessityTagController.java        # NEW: implements generated delegate (package matches BillCorrectionController's, not application.controller)
├── controller/recurring/RecurringCostSummaryController.java  # NEW: implements generated delegate
└── application/mapper/
    ├── BillMapper.java (Application layer)                # UNCHANGED: MapStruct auto-maps the new same-named necessityTag field once both DTO and API model declare it — confirmed no explicit method body exists for any other field either
    └── RecurringCostSummaryMapper.java                     # NEW

frontend/src/
├── types/index.ts                              # MODIFIED: + NecessityTag, RecurringCostSummaryItem, CategorySpendingOpportunity, TaggedTransactionOpportunity, SpendingCutRecommendations
├── api/client.ts                               # MODIFIED: + updateBillNecessityTag(id, tag), fetchRecurringCostSummary()
├── utils/spendingCutRecommendations.ts         # NEW: computeSpendingCutRecommendations(...)
├── components/NecessityTagControl.tsx          # NEW: small tag selector, mounted on bill rows (RecentTransactions.tsx / TransactionHistoryDialog.tsx)
├── components/SpendingCutRecommendationsPage.tsx  # NEW: ranked recurring list, tagged-transaction list, price-creep call-outs, category opportunities, combined total, empty state
└── App.tsx                                     # MODIFIED: mount the new page/section (navigation entry) + NecessityTagControl on existing bill rows
```

**Structure Decision**: Existing hexagonal web-application layout (Domain/Application/
Infrastructure/Launcher + `frontend/`), unchanged. Launcher needs no changes — no new Spring bean
requires manual wiring beyond `@Service`/`@Component` scanning already in place for every other
Domain service and Application controller.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
