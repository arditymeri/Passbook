# Implementation Plan: Recurring Transaction Detection

**Branch**: `claude/project-status-s0au7m` (spec directory `010-recurring-transaction-detection`) | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-recurring-transaction-detection/spec.md`

## Summary

Detect recurring bill/income series from transaction history (same category + description +
cadence + amount, three matching occurrences — two if one is already flagged `recurring`),
surface a 14-day "Upcoming" view with overdue flagging, and warn when a newly recorded occurrence
of a confirmed series costs more or less than the last one. Series recognition never mutates a
bill or income row: detection, prediction, and price-change comparison are all computed at read
time from `GetBillService.getAll()` / `GetIncomeService.getAll()` — the same *human-facing*,
correction-aware read path every other feature already treats as "one row per logical
transaction, current value." The only new persisted state is the recognized series itself
(what it is, and whether the user has confirmed or dismissed it) — not any derived date, amount,
or balance.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern) — backend; existing `@mui/material` v5 component set — frontend (no new frontend dependencies)

**Storage**: PostgreSQL — one new table, `recurring_series` (created automatically by Hibernate `ddl-auto: update`, same zero-migration pattern every prior feature has used). No changes to the `bill` or `income` tables — series membership is computed by re-matching on `(category_id, description)`, never stored as a foreign key on the transaction rows.

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration) — backend; TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — frontend

**Target Platform**: Linux server (Docker Compose stack) + web browser, desktop-primary

**Project Type**: Web application (hexagonal-architecture backend, Maven multi-module + React SPA frontend)

**Performance Goals**: Detection and the Upcoming view compute over the full bill/income history in memory, same "load everything, derive in memory" approach every prior derived-balance feature (007, 009) already uses — acceptable at the hundreds-of-rows personal scale this app targets.

**Constraints**:
- `BigDecimal` throughout Domain and Infrastructure for all monetary values (Principle IV) — amount-tolerance comparisons never use floating point
- Domain module stays framework-free; detection, prediction, and price-change logic live in Domain services composed from existing Domain-level read services (`GetBillService`, `GetIncomeService`) plus new SPI ports for the one new entity (Principle VIII)
- All new endpoints are net-new paths — no existing bill/income/budget contract changes (Principle VII)
- Detecting a series never edits a bill or income row and never requires editing one — the existing `recurring`/`recurringFrequency` fields are read as an optional hint, never written to by this feature
- Does not modify `BillCorrections`/`IncomeCorrections` (008) or `EnvelopeBalances`/allocation code (009) — only reads through their already-published Domain read services

**Scale/Scope**: 1 new entity (`RecurringSeries`), 5 new Domain services (detect, confirm, dismiss, list, upcoming-dashboard), 5 new REST endpoints, 1 new frontend section on the existing dashboard plus a small proposals/management list — no new full-page view needed

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Never edits, corrects, or removes a bill/income row. The only write this feature performs is to its own new `recurring_series` decision record (proposed/confirmed/dismissed) — not a financial transaction. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass | Predicted next date/amount and price-change flags are computed at read time from current transaction history on every request — nothing is cached or stored as a running total. The one thing that *is* stored (a series' recognized identity and confirm/dismiss decision) is user decision state, the same category as a `Budget` or `Category` row, not a derived balance. |
| IV. Currency Precision | ✅ Pass | All amounts and tolerance comparisons use `BigDecimal`. |
| V. Audit Trail | ⚠ Pre-existing gap, not worsened | Same known gap called out in every prior feature's plan — no actor/timestamp audit log exists anywhere in the app yet. Confirm/dismiss decisions are not audit-logged, consistent with the rest of the app's current state. |
| VI. Test-First Development | ⚠ Required | Detection matching (grouping, cadence tolerance, amount tolerance, occurrence-count threshold), prediction, and price-change comparison MUST have Domain unit tests before/alongside implementation, per every prior feature's precedent. |
| VII. API Contract Stability | ✅ Pass | Five new endpoints, all net-new paths under `/recurring-series`. No existing bill/income/budget/account contract is touched. |
| VIII. Hexagonal Architecture Compliance | ✅ Pass | New `RecurringSeries` persistence is mediated through new SPI ports, implemented in Infrastructure. Detection composes existing Domain API services (`GetBillService`, `GetIncomeService`) rather than reaching into their SPI ports or Infrastructure directly — Domain composing published Domain services, not a framework leak. |

**Gate decision**: PASS. Test-First (VI) is mandatory, matching every prior feature. Audit Trail (V) remains a flagged, pre-existing, unresolved gap rather than something this feature must fix.

## Project Structure

### Documentation (this feature)

```text
specs/010-recurring-transaction-detection/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   ├── recurring-model.yaml               # New OpenAPI schemas
│   ├── recurring-get-controller.yaml      # GET /recurring-series, GET /recurring-series/dashboard
│   ├── recurring-detect-controller.yaml   # POST /recurring-series/detect
│   ├── recurring-confirm-controller.yaml  # POST /recurring-series/{id}/confirm
│   └── recurring-dismiss-controller.yaml  # POST /recurring-series/{id}/dismiss
└── tasks.md                     # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/recurring/
├── recurring-model.yaml                   # NEW
├── recurring-get-controller.yaml          # NEW
├── recurring-detect-controller.yaml       # NEW
├── recurring-confirm-controller.yaml      # NEW
└── recurring-dismiss-controller.yaml      # NEW

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/recurring/
│   ├── TransactionType.java                # NEW — BILL | INCOME
│   ├── RecurringSeriesStatus.java          # NEW — PROPOSED | CONFIRMED | DISMISSED
│   ├── RecurringSeriesDto.java             # NEW
│   ├── UpcomingRecurringItemDto.java       # NEW — derived, not stored
│   ├── PriceChangeAlertDto.java            # NEW — derived, not stored
│   └── RecurringDashboardResult.java       # NEW — { upcoming, recentPriceChanges }
├── api/
│   ├── GetRecurringSeriesService.java      # NEW
│   ├── DetectRecurringSeriesService.java   # NEW
│   ├── ConfirmRecurringSeriesService.java  # NEW
│   ├── DismissRecurringSeriesService.java  # NEW
│   └── GetUpcomingRecurringService.java    # NEW
├── spi/recurring/
│   ├── GetRecurringSeriesPersistencePort.java     # NEW
│   ├── AddRecurringSeriesPersistencePort.java     # NEW
│   └── UpdateRecurringSeriesStatusPersistencePort.java  # NEW
└── service/recurring/
    ├── RecurringMatching.java              # NEW — shared helper (grouping, cadence/amount
    │                                        #        tolerance, next-date prediction)
    ├── DetectRecurringSeriesServiceImpl.java     # NEW
    ├── ConfirmRecurringSeriesServiceImpl.java    # NEW
    ├── DismissRecurringSeriesServiceImpl.java    # NEW
    ├── GetRecurringSeriesServiceImpl.java        # NEW
    └── GetUpcomingRecurringServiceImpl.java      # NEW

Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/
├── RecurringMatchingTest.java                    # NEW
├── DetectRecurringSeriesServiceImplTest.java      # NEW
├── GetUpcomingRecurringServiceImplTest.java       # NEW
├── ConfirmRecurringSeriesServiceImplTest.java     # NEW
└── DismissRecurringSeriesServiceImplTest.java     # NEW

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/RecurringSeriesEntity.java              # NEW
├── repository/RecurringSeriesRepository.java      # NEW
├── mapper/RecurringSeriesMapper.java              # NEW
└── adapter/postgres/recurring/
    └── RecurringSeriesPostgresAdapter.java        # NEW — implements all three recurring ports

Application/src/main/java/at/ymeri/my/finance/
├── controller/recurring/
│   ├── RecurringGetController.java         # NEW
│   ├── RecurringDetectController.java      # NEW
│   ├── RecurringConfirmController.java     # NEW
│   └── RecurringDismissController.java     # NEW
└── application/mapper/RecurringSeriesMapper.java  # NEW (Application-layer MapStruct mapper)

integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/
└── RecurringSeriesControllerIntegrationTest.java  # NEW

frontend/src/
├── types/index.ts                          # MODIFY — add RecurringSeries, UpcomingRecurringItem,
│                                            #          PriceChangeAlert, RecurringDashboard types
├── api/client.ts                           # MODIFY — add fetchRecurringSeries, detectRecurringSeries,
│                                            #          confirmRecurringSeries, dismissRecurringSeries,
│                                            #          fetchRecurringDashboard
├── hooks/
│   └── useRecurringSeries.ts               # NEW — mirrors useBudgetAllocations.ts
├── components/
│   ├── UpcomingRecurring.tsx                # NEW — dashboard section: upcoming + overdue + price
│   │                                        #        change alerts
│   └── RecurringSeriesProposals.tsx         # NEW — pending proposals (confirm/dismiss) +
│   │                                        #        confirmed series list (stop tracking)
└── App.tsx                                 # MODIFY — mount UpcomingRecurring on the dashboard,
                                             #          add a way to open RecurringSeriesProposals
```

**Structure Decision**: Web application (existing hexagonal-architecture backend + React SPA
frontend). No new modules or top-level directories. Unlike 007/009 this feature does not need a
new full-page view — the Upcoming section slots into the existing dashboard alongside
`CategorySpend`/`BudgetStatus`, and the proposals/management list opens as a dialog (mirrors
`MoveAllocationDialog`/`RepeatAllocationsDialog` from 009) rather than a page with its own nav
entry, since it's a secondary, infrequent action.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
