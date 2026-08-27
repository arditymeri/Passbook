# Implementation Plan: Savings Goals

**Branch**: `claude/project-status-s0au7m` (spec directory `011-savings-goals`) | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-savings-goals/spec.md`

## Summary

Let a user create a savings goal (name, target amount, optional target date) linked to exactly
one existing account. Nothing about progress is stored: saved amount is the linked account's
current derived balance (via the existing `GetAccountService`), and percent complete, remaining
amount, achieved status, and pace status (on-pace / behind-pace / overdue, only when a target date
is set) are all computed fresh on every read from that balance plus the goal's own `targetAmount`,
`createdAt`, and `targetDate`. The only new persisted state is the goal record itself — name,
target amount, optional target date, and the one account it's linked to — mirroring how
`RecurringSeries` (010) persists only recognized-series identity, never any derived value.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern) — backend; existing `@mui/material` v5 component set — frontend (no new frontend dependencies)

**Storage**: PostgreSQL — one new table, `savings_goal` (created automatically by Hibernate `ddl-auto: update`, same zero-migration pattern every prior feature has used). No changes to the `account`, `bill`, or `income` tables — a goal's saved amount is computed by re-reading its linked account's current balance, never stored as a snapshot or foreign key back onto transaction rows.

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration) — backend; TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — frontend

**Target Platform**: Linux server (Docker Compose stack) + web browser, desktop-primary

**Project Type**: Web application (hexagonal-architecture backend, Maven multi-module + React SPA frontend)

**Performance Goals**: Deriving progress for the goals list re-reads each linked account's balance (already an O(bills + incomes) in-memory scan per account, per 007) — acceptable at the hundreds-of-rows personal scale this app targets, same "load everything, derive in memory" approach as every prior derived-balance feature (007, 009, 010).

**Constraints**:
- `BigDecimal` throughout Domain and Infrastructure for all monetary values (Principle IV) — target amount, saved amount, and remaining amount are never floating-point
- Domain module stays framework-free; goal CRUD and progress derivation live in Domain services composed from the existing Domain-level `GetAccountService` read service plus new SPI ports for the one new entity (Principle VIII)
- All new endpoints are net-new paths — no existing account/bill/income contract changes (Principle VII)
- Creating, updating, or deleting a goal never writes to `account`, `bill`, or `income` rows — the linkage is read-only from the goal's perspective
- Does not modify `BillCorrections`/`IncomeCorrections` (008), `EnvelopeBalances`/allocation code (009), or `RecurringSeries` (010) — only reads through `GetAccountService`, already published for other features to reuse

**Scale/Scope**: 1 new entity (`SavingsGoal`), 4 new Domain services (add, get/list with derived status, update, delete), 4 new REST endpoints, 1 new frontend page (goal list + create/edit/delete), no dashboard-widget footprint since goals are a first-class managed list like Accounts/Budgeting rather than a passive summary

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Never edits, corrects, or removes a bill/income row. Goal create/update/delete only touches the new `savings_goal` planning record — not a financial transaction — consistent with the spec's Assumptions section. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass | Saved amount, percent complete, remaining amount, achieved status, and pace status are all computed at read time from the linked account's current balance (via `GetAccountService`, itself already balance-deriving per 007) and the goal's own dates — nothing is cached or stored as a running total. The one thing that *is* stored (name, target amount, optional target date, account link) is user-authored planning state, the same category as a `Budget` or `RecurringSeries` row, not a derived balance. |
| IV. Currency Precision | ✅ Pass | Target amount, saved amount, and remaining amount all use `BigDecimal`. |
| V. Audit Trail | ⚠ Pre-existing gap, not worsened | Same known gap called out in every prior feature's plan — no actor/timestamp audit log exists anywhere in the app yet. Goal create/update/delete are not audit-logged, consistent with the rest of the app's current state. |
| VI. Test-First Development | ⚠ Required | Progress derivation (percent complete, remaining, achieved, pace status across ahead/on-pace/behind/overdue) and the one-active-goal-per-account validation MUST have Domain unit tests before/alongside implementation, per every prior feature's precedent. |
| VII. API Contract Stability | ✅ Pass | Four new endpoints, all net-new paths under `/savings-goals`. No existing account/bill/income/budget/recurring-series contract is touched. |
| VIII. Hexagonal Architecture Compliance | ✅ Pass | New `SavingsGoal` persistence is mediated through new SPI ports, implemented in Infrastructure. Progress derivation composes the existing Domain API service `GetAccountService` rather than reaching into its SPI port or Infrastructure directly — Domain composing a published Domain service, not a framework leak. |

**Gate decision**: PASS. Test-First (VI) is mandatory, matching every prior feature. Audit Trail (V) remains a flagged, pre-existing, unresolved gap rather than something this feature must fix.

## Project Structure

### Documentation (this feature)

```text
specs/011-savings-goals/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   ├── goal-model.yaml               # New OpenAPI schemas
│   ├── goal-get-controller.yaml      # GET /savings-goals, GET /savings-goals/{id}
│   ├── goal-add-controller.yaml      # POST /savings-goals
│   ├── goal-update-controller.yaml   # PUT /savings-goals/{id}
│   └── goal-delete-controller.yaml   # DELETE /savings-goals/{id}
└── tasks.md                     # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/goal/
├── goal-model.yaml                   # NEW
├── goal-get-controller.yaml          # NEW
├── goal-add-controller.yaml          # NEW
├── goal-update-controller.yaml       # NEW
└── goal-delete-controller.yaml       # NEW

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/goal/
│   ├── PaceStatus.java                 # NEW — ON_PACE | BEHIND_PACE | OVERDUE
│   ├── SavingsGoalDto.java             # NEW — persisted fields only
│   └── SavingsGoalStatusDto.java       # NEW — persisted fields + derived savedAmount,
│                                        #        percentComplete, remainingAmount, achieved,
│                                        #        paceStatus (mirrors BudgetStatusDto's split
│                                        #        from BudgetDto)
├── api/
│   ├── AddSavingsGoalService.java      # NEW
│   ├── GetSavingsGoalService.java      # NEW — returns List<SavingsGoalStatusDto> / one by id
│   ├── UpdateSavingsGoalService.java   # NEW
│   └── DeleteSavingsGoalService.java   # NEW
├── spi/goal/
│   ├── AddSavingsGoalPersistencePort.java     # NEW
│   ├── GetSavingsGoalPersistencePort.java     # NEW
│   ├── UpdateSavingsGoalPersistencePort.java  # NEW
│   └── DeleteSavingsGoalPersistencePort.java  # NEW
└── service/goal/
    ├── SavingsGoalProgress.java            # NEW — shared pure helper (percentComplete,
    │                                        #        remainingAmount, achieved, paceStatus)
    ├── AddSavingsGoalServiceImpl.java       # NEW — rejects a second active goal on the same account
    ├── GetSavingsGoalServiceImpl.java       # NEW — composes GetAccountService per goal
    ├── UpdateSavingsGoalServiceImpl.java    # NEW — name/targetAmount/targetDate only
    └── DeleteSavingsGoalServiceImpl.java    # NEW

Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/
├── SavingsGoalProgressTest.java             # NEW
├── AddSavingsGoalServiceImplTest.java       # NEW
├── GetSavingsGoalServiceImplTest.java       # NEW
├── UpdateSavingsGoalServiceImplTest.java    # NEW
└── DeleteSavingsGoalServiceImplTest.java    # NEW

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/SavingsGoalEntity.java              # NEW
├── repository/SavingsGoalRepository.java      # NEW — + findByAccountId
├── mapper/SavingsGoalMapper.java              # NEW
└── adapter/postgres/goal/
    └── SavingsGoalPostgresAdapter.java        # NEW — implements all four goal ports

Application/src/main/java/at/ymeri/my/finance/
├── controller/goal/
│   ├── SavingsGoalGetController.java       # NEW
│   ├── SavingsGoalAddController.java       # NEW
│   ├── SavingsGoalUpdateController.java    # NEW
│   └── SavingsGoalDeleteController.java    # NEW
└── application/mapper/SavingsGoalMapper.java  # NEW (Application-layer MapStruct mapper)

integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/
└── SavingsGoalControllerIntegrationTest.java  # NEW

frontend/src/
├── types/index.ts                          # MODIFY — add PaceStatusValue, SavingsGoalStatus types
├── api/client.ts                           # MODIFY — add fetchSavingsGoals, createSavingsGoal,
│                                            #          updateSavingsGoal, deleteSavingsGoal
├── hooks/
│   └── useSavingsGoals.ts                  # NEW — mirrors useBudgetAllocations.ts
├── components/
│   ├── SavingsGoalsPage.tsx                 # NEW — full page: goal cards with progress bars,
│   │                                        #        pace/achieved badges, edit/delete actions
│   └── SavingsGoalForm.tsx                  # NEW — create/edit dialog (name, target amount,
│   │                                        #        target date, account select)
└── App.tsx                                 # MODIFY — add "Goals" nav button + 'goals' view,
                                             #          mirrors 'accounts'/'budgeting' view pattern
```

**Structure Decision**: Web application (existing hexagonal-architecture backend + React SPA
frontend). No new modules or top-level directories. Unlike 010 (a passive dashboard widget), goals
are a first-class managed list with create/edit/delete — so this follows the `AccountsPage.tsx` /
`BudgetingPage.tsx` full-page pattern (007/009) rather than the dashboard-widget pattern, opened
via a new "Goals" nav button.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
