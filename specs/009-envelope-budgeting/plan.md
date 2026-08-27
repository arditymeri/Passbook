# Implementation Plan: Envelope Budgeting

**Branch**: `claude/project-status-s0au7m` (spec directory `009-envelope-budgeting`) | **Date**: 2026-08-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-envelope-budgeting/spec.md`

## Summary

Reframe feature 002's per-category monthly `Budget` row as an **allocation entry**, and add the
running, cumulative figures envelope budgeting needs on top of it: an **unallocated balance**
(income to date minus allocations to date) and, per category, an **envelope balance** (allocated to
date minus spent to date). Both are derived at read time from existing data — allocations, bills,
and incomes — the same pattern `GetAccountServiceImpl` (007) already uses for account balances, and
the same reversal-netting that `GetBudgetStatusServiceImpl` already relies on for month-scoped
actuals continues to work unchanged for the cumulative figures (008's bill/income corrections are
plain rows with negated amounts, so summing nets them out with no special-casing).

Two new user actions need a place to live that the existing `Budget` upsert can't represent without
retrofitting negative values onto a row that's meant to mean "assigned this month, ≥ 0": moving
money between two categories, and repeating a month's assignment amounts as new top-ups. The first
is modeled as a new, small, append-only `AllocationTransfer` record (mirroring how 008 modeled bill
corrections as new rows, not mutated ones) rather than as a signed delta on the existing `Budget`
row. The second reuses the existing upsert with an additive amount.

There is currently no frontend for creating or viewing allocations at all — feature 002 only ever
shipped a read-only "Budget vs. Actual" panel; budgets have only ever been created via Swagger UI.
This feature is the first real budgeting UI the app gets.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern) — backend; existing `@mui/material` v5 component set — frontend (no new frontend dependencies)

**Storage**: PostgreSQL — existing `budget`, `bill`, `income` tables (no schema change to any of them); one new table, `allocation_transfer`, created automatically by Hibernate `ddl-auto: update` (same zero-migration pattern every prior feature has used)

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration) — backend; TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — frontend

**Target Platform**: Linux server (Docker Compose stack) + web browser, desktop-primary

**Project Type**: Web application (hexagonal-architecture backend, Maven multi-module + React SPA frontend)

**Performance Goals**: Budgeting view (allocations, unallocated balance, all envelope balances) renders in under 2 seconds on personal-scale data — same bar 007 set for the Accounts page, using the same "load everything, derive in memory" approach (no pagination needed at hundreds-of-rows scale)

**Constraints**:
- `BigDecimal` throughout Domain and Infrastructure for all monetary values (Principle IV) — no new `double`/`float` fields
- Domain module stays framework-free; all derivation logic lives in Domain services composed from SPI ports (Principle VIII)
- New/changed OpenAPI fields and endpoints are additive only — no `/v2`, no field removals (Principle VII)
- Amount fields in the new allocation/transfer/repeat forms MUST use `type="text"` with string state, matching every existing money-input form (004/006/007 pattern)
- Does not read from or modify feature 008's bill/income correction code paths beyond consuming `GetBillPersistencePort.getAll()` / `GetIncomePersistencePort.getAll()` the same way `GetAccountServiceImpl` already does — no changes to `Application/src/main/java/.../controller/bill|income/*Correction*`

**Scale/Scope**: 1 reused entity (`Budget`, unchanged schema), 1 new entity (`AllocationTransfer`), 3 new/extended Domain services, 2 new REST endpoints + 2 extended response schemas, 1 new frontend page (`BudgetingPage`) plus a new hook and API client functions (there is no existing allocation-creation UI to extend)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Allocation entries remain upserts, same as feature 002 (they are planning data, not ledger transactions). The new capability this feature adds — moving money between envelopes — is recorded as a new, never-mutated `AllocationTransfer` row, not as an edit to an existing one. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature — bills/incomes remain single-entry; not introduced or altered here. |
| III. Account Integrity & Balance Derivation | ✅ Pass | This feature's core purpose: unallocated balance and per-category envelope balances are computed at read time from allocation, transfer, bill, and income history — nothing is stored as a mutable running total. |
| IV. Currency Precision | ✅ Pass | All new fields (`AllocationTransfer.amount`, derived balances) are `BigDecimal` end-to-end. |
| V. Audit Trail | ⚠ Pre-existing gap, not worsened | The app has no actor/timestamp audit log anywhere yet (a known gap called out in the project roadmap). `AllocationTransfer` rows carry a timestamp as a natural byproduct, but this feature does not attempt to build general audit logging — out of scope, tracked separately. |
| VI. Test-First Development | ⚠ Required | Unallocated-balance and envelope-balance derivation, transfer validation (insufficient-balance rejection), and repeat-allocation logic MUST have Domain unit tests before/alongside implementation, per the precedent set by every prior feature. |
| VII. API Contract Stability | ✅ Pass | `budgetStatusResponse`/`budgetStatusEntry` gain additive fields only. Two new endpoints (`/budgets/transfer`, `/budgets/repeat`) are net-new paths, not versions of existing ones. |
| VIII. Hexagonal Architecture Compliance | ✅ Pass | New `AllocationTransfer` persistence is mediated through new SPI ports (`GetAllocationTransferPersistencePort`, `AddAllocationTransferPersistencePort`), implemented in Infrastructure. Balance derivation composes existing ports (`GetBudgetPersistencePort`, `GetBillPersistencePort`, `GetIncomePersistencePort`) plus the new one — no Spring/JPA leaks into Domain. |

**Gate decision**: PASS. Test-First (VI) is mandatory, matching every prior feature's precedent. Audit Trail (V) is flagged as a pre-existing, unresolved gap rather than something this feature must fix.

## Project Structure

### Documentation (this feature)

```text
specs/009-envelope-budgeting/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   ├── budget-model.yaml        # Updated OpenAPI schema snapshot (unallocated + envelopeBalance fields)
│   ├── budget-transfer-controller.yaml   # New — move money between category envelopes
│   └── budget-repeat-controller.yaml     # New — repeat a month's assignments into another month
└── tasks.md                     # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/budget/
├── budget-model.yaml                            # MODIFY — add `unallocated` to budgetStatusResponse,
│                                                 #          `envelopeBalance` to budgetStatusEntry;
│                                                 #          add transfer/repeat request+response schemas
├── budget-transfer-controller.yaml              # NEW — POST /budgets/transfer
└── budget-repeat-controller.yaml                # NEW — POST /budgets/repeat

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/budget/
│   ├── AllocationTransferDto.java               # NEW
│   └── BudgetStatusDto.java                     # MODIFY — add envelopeBalance field
├── api/
│   ├── MoveAllocationService.java                # NEW
│   └── RepeatAllocationsService.java             # NEW
├── spi/budget/
│   ├── GetBudgetPersistencePort.java             # MODIFY — add getAll()
│   ├── GetAllocationTransferPersistencePort.java # NEW
│   └── AddAllocationTransferPersistencePort.java # NEW
└── service/budget/
    ├── EnvelopeBalances.java                     # NEW — shared derivation helper (unallocated +
    │                                              #        per-category envelope balance), used by
    │                                              #        GetBudgetStatusServiceImpl and
    │                                              #        MoveAllocationServiceImpl
    ├── GetBudgetStatusServiceImpl.java            # MODIFY — return unallocated + envelopeBalance
    ├── SetBudgetServiceImpl.java                  # MODIFY — reject INCOME-only categories (FR-009)
    ├── MoveAllocationServiceImpl.java             # NEW
    └── RepeatAllocationsServiceImpl.java          # NEW

Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/
├── EnvelopeBalancesTest.java                      # NEW
├── GetBudgetStatusServiceImplTest.java             # MODIFY — cover new fields
├── MoveAllocationServiceImplTest.java              # NEW
└── RepeatAllocationsServiceImplTest.java           # NEW

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/AllocationTransferEntity.java           # NEW
├── repository/AllocationTransferRepository.java   # NEW
├── mapper/AllocationTransferMapper.java            # NEW
└── adapter/postgres/budget/
    ├── AllocationTransferPostgresAdapter.java      # NEW — implements both transfer ports
    └── GetBudgetPostgresAdapter.java                # MODIFY — implement getAll()

integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/
└── BudgetControllerIntegrationTest.java            # MODIFY — add transfer/repeat/derived-balance cases

frontend/src/
├── types/index.ts                                 # MODIFY — add Allocation, AllocationTransfer types;
│                                                    #          extend BudgetStatusEntry with envelopeBalance;
│                                                    #          extend budget status response with unallocated
├── api/client.ts                                  # MODIFY — add fetchBudgets, createOrUpdateBudget,
│                                                    #          moveAllocation, repeatAllocations
├── hooks/
│   └── useBudgetAllocations.ts                     # NEW — mirrors useAccounts.ts
├── components/
│   ├── BudgetingPage.tsx                           # NEW — unallocated balance + category envelope list
│   ├── AllocationForm.tsx                          # NEW — assign an amount to one category/month
│   ├── MoveAllocationDialog.tsx                    # NEW — move money between two categories
│   └── RepeatAllocationsDialog.tsx                 # NEW — preview + confirm repeating a prior month
└── App.tsx                                         # MODIFY — add 'budgeting' view state + nav button
```

**Structure Decision**: Web application (existing hexagonal-architecture backend + React SPA
frontend). No new modules or top-level directories. All backend changes stay within
`Application`/`Domain`/`Infrastructure`/`integration-tests`; all frontend changes stay within
`frontend/src/`, following the file layout every prior feature (especially 005 and 007, the two
other "list + create" frontend features) already established.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

| Addition | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|---------------------------------------|
| New `AllocationTransfer` entity/table/port pair, instead of writing a signed delta into the existing `Budget.limitAmount` row | FR-005/FR-006 need "move $X from category A to category B" to be a recorded, validated action with its own rejection case (insufficient balance) | Overloading `Budget` rows with negative amounts would break `SetBudgetServiceImpl`'s existing "amount must be > 0" invariant (relied on since feature 002) and would make a transfer indistinguishable from a plain reduced assignment in the data — the same reasoning 008 used to model corrections as new rows instead of mutating bills |
