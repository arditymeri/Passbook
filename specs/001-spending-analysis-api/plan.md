# Implementation Plan: Spending Analysis API

**Branch**: `001-spending-analysis-api` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-spending-analysis-api/spec.md`

## Summary

Expose the already-implemented `GetSpendingAnalysisService` domain interface via two REST endpoints:
`GET /api/v1/analysis/monthly` (single month) and `GET /api/v1/analysis/period` (date range).
The domain logic is complete. Work is limited to the Application layer (OpenAPI YAML + controller +
mapper) and the Infrastructure layer (persistence adapter + repository queries).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern)

**Storage**: PostgreSQL — existing `bill` and `income` tables queried by date range

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration)

**Target Platform**: Linux server (Docker Compose stack)

**Project Type**: Web service (hexagonal architecture, Maven multi-module)

**Performance Goals**: Sub-second response for ranges up to 12 months on a personal-scale dataset
(hundreds to low thousands of transactions)

**Constraints**: BigDecimal throughout domain and infrastructure; no floating-point money in domain
or DB layer (Principle IV). Domain module must remain framework-free (Principle VIII).

**Scale/Scope**: Single user, personal finance data — no multi-tenancy in scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Read-only feature — no writes, no risk of silent mutation |
| II. Double-Entry Accounting | ✅ N/A | Aggregation read, not a transaction recording feature |
| III. Account Integrity & Balance Derivation | ✅ Pass | Totals derived by summing transaction lines from bill/income tables |
| IV. Currency Precision | ✅ Pass | Domain impl uses `BigDecimal`; OpenAPI model uses `number` (no `format: double`); mapper converts |
| V. Audit Trail | ✅ N/A | Read-only; no state changes to log |
| VI. Test-First | ⚠ Required | Domain service impl exists but has no unit test yet; integration test required |
| VII. API Contract Stability | ✅ Pass | OpenAPI YAML written first (Phase 1) before any controller code |
| VIII. Hexagonal Architecture | ✅ Pass | Domain service → SPI port → Infrastructure adapter; no framework deps in Domain |

**Gate decision**: PASS. Test-First (VI) is flagged — domain service unit test and integration test
are mandatory tasks, not optional.

## Project Structure

### Documentation (this feature)

```text
specs/001-spending-analysis-api/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── analysis-model.yaml
│   └── analysis-get-controller.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/analysis/
├── analysis-model.yaml                          # new — OpenAPI schema definitions
└── analysis-get-controller.yaml                 # new — endpoint definitions

Application/src/main/java/at/ymeri/my/finance/
├── application/mapper/
│   └── AnalysisMapper.java                      # new — MonthlySummaryDto ↔ API model
└── controller/analysis/
    └── AnalysisGetController.java               # new — implements generated AnalysisGetApiDelegate

Infrastructure/src/main/java/at/ymeri/my/finance/
└── infrastructure/
    ├── adapter/postgres/analysis/
    │   └── GetSpendingAnalysisPostgresAdapter.java  # new — implements GetSpendingAnalysisPersistencePort
    └── repository/
        ├── BillRepository.java                  # modified — add findByTimeBetween query
        └── IncomeRepository.java                # modified — add findByTimeBetween query

Domain/src/test/java/at/ymeri/my/finance/
└── domain/service/analysis/
    └── GetSpendingAnalysisServiceImplTest.java  # new — unit tests (Principle VI)

integration-tests/src/test/java/at/ymeri/my/finance/
└── AnalysisGetControllerIntegrationTest.java    # new — TestContainers acceptance test
```

**Structure Decision**: Follows the established hexagonal module layout. No new modules required.
All additions are parallel to existing bill/income/account/category features.
