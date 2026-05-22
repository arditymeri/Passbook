# Implementation Plan: Budget / Spending Limits

**Branch**: `002-budget-spending-limits` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-budget-spending-limits/spec.md`

## Summary

Introduce a Budget concept: users set a monthly spending limit per category, then query a
budget-vs-actual status view. The feature requires new domain services, a new `budget` table,
OpenAPI contracts, and four REST endpoints. Actual spend is sourced by reusing the existing
`GetSpendingAnalysisPersistencePort.getBillsByPeriod()` port from the Spending Analysis feature.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern)

**Storage**: PostgreSQL — new `budget` table; reads from existing `bill` table for actual spend

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration)

**Target Platform**: Linux server (Docker Compose stack)

**Project Type**: Web service (hexagonal architecture, Maven multi-module)

**Performance Goals**: Sub-second response for all budget endpoints on personal-scale data

**Constraints**: BigDecimal for all monetary values (Principle IV). Domain module framework-free
(Principle VIII). Category must exist before a budget can reference it (referential integrity
enforced in domain, not DB).

**Scale/Scope**: Single user, personal finance — no multi-tenancy in scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Budgets are planning data, not financial ledger entries. Update/delete of a budget limit does not mutate any transaction record. |
| II. Double-Entry Accounting | ✅ N/A | No journal entries created or modified by this feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass | Budget status derives actual spend by aggregating bill records — no mutable cached total. |
| IV. Currency Precision | ✅ Pass | All monetary fields use `BigDecimal`; no `double`/`float` in domain or OpenAPI model. |
| V. Audit Trail | ✅ N/A | Budgets are planning data; financial audit trail not required. |
| VI. Test-First | ⚠ Required | Domain service unit tests and integration tests are mandatory. |
| VII. API Contract Stability | ✅ Pass | OpenAPI YAML written first (Phase 1) before controller code. |
| VIII. Hexagonal Architecture | ✅ Pass | All domain services depend only on port interfaces; Spring annotations stay in Application/Infrastructure. |

**Gate decision**: PASS. Test-First (VI) flagged — mandatory, not optional.

## Project Structure

### Documentation (this feature)

```text
specs/002-budget-spending-limits/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── budget-model.yaml
│   ├── budget-post-controller.yaml
│   ├── budget-get-controller.yaml
│   └── budget-delete-controller.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
Domain/src/main/java/at/ymeri/my/finance/
├── domain/api/
│   ├── SetBudgetService.java                    # new — upsert budget
│   ├── GetBudgetService.java                    # new — list by month, get by id
│   ├── DeleteBudgetService.java                 # new — delete by id
│   └── GetBudgetStatusService.java              # new — budget vs actual view
├── domain/data/budget/
│   ├── BudgetDto.java                           # new
│   ├── BudgetStatusDto.java                     # new
│   └── BudgetStatus.java                        # new enum: UNDER_BUDGET, OVER_BUDGET
├── domain/service/budget/
│   ├── SetBudgetServiceImpl.java                # new
│   ├── GetBudgetServiceImpl.java                # new
│   ├── DeleteBudgetServiceImpl.java             # new
│   └── GetBudgetStatusServiceImpl.java          # new — merges budgets + bill aggregation
└── domain/spi/budget/
    ├── SetBudgetPersistencePort.java            # new
    ├── GetBudgetPersistencePort.java            # new
    └── DeleteBudgetPersistencePort.java         # new

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/BudgetEntity.java                     # new — @Table("budget")
├── repository/BudgetRepository.java             # new — derived queries
├── mapper/BudgetMapper.java                     # new — MapStruct
└── adapter/postgres/budget/
    ├── SetBudgetPostgresAdapter.java            # new
    ├── GetBudgetPostgresAdapter.java            # new
    └── DeleteBudgetPostgresAdapter.java         # new

Application/src/main/resources/swagger/budget/
├── budget-model.yaml                            # new
├── budget-post-controller.yaml                  # new
├── budget-get-controller.yaml                   # new
└── budget-delete-controller.yaml               # new

Application/src/main/java/at/ymeri/my/finance/
├── application/mapper/BudgetMapper.java         # new
└── controller/budget/
    ├── BudgetCreateController.java              # new — POST /budgets
    ├── BudgetGetController.java                 # new — GET /budgets, GET /budgets/status
    └── BudgetDeleteController.java              # new — DELETE /budgets/{id}

Domain/src/test/java/.../domain/service/budget/
├── SetBudgetServiceImplTest.java                # new
└── GetBudgetStatusServiceImplTest.java          # new

integration-tests/src/test/java/.../integration/tests/
└── BudgetControllerIntegrationTest.java         # new
```

**Structure Decision**: Follows the established hexagonal module layout. No new Maven modules.
All additions parallel the existing bill/income/account/category/analysis patterns.
