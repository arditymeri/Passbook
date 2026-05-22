---
description: "Task list for Spending Analysis API"
---

# Tasks: Spending Analysis API

**Input**: Design documents from `/specs/001-spending-analysis-api/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Included — Constitution Principle VI mandates unit tests for domain logic and
integration tests against a real database (no mocks).

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- Domain: `Domain/src/main/java/at/ymeri/my/finance/`
- Application: `Application/src/main/java/at/ymeri/my/finance/`
- Infrastructure: `Infrastructure/src/main/java/at/ymeri/my/finance/`
- Swagger: `Application/src/main/resources/swagger/analysis/`
- Domain tests: `Domain/src/test/java/at/ymeri/my/finance/`
- Integration tests: `integration-tests/src/test/java/at/ymeri/my/finance/`

---

## Phase 1: Setup

No new project structure or module setup required — this feature wires into the existing
hexagonal module layout.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure required by both user stories. MUST be complete before
any user story work begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T001 Add `findByTimeBetween(OffsetDateTime start, OffsetDateTime end)` derived query to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/BillRepository.java`
- [ ] T002 [P] Add `findByTimeBetween(OffsetDateTime start, OffsetDateTime end)` derived query to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/IncomeRepository.java`
- [ ] T003 Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/analysis/GetSpendingAnalysisPostgresAdapter.java` implementing `GetSpendingAnalysisPersistencePort` — use `BillRepository.findByTimeBetween` and `IncomeRepository.findByTimeBetween`, convert `LocalDate` to `OffsetDateTime` at UTC midnight / end-of-day, map entities to DTOs using existing `BillMapper` and `IncomeMapper`
- [ ] T004 Copy `specs/001-spending-analysis-api/contracts/analysis-model.yaml` to `Application/src/main/resources/swagger/analysis/analysis-model.yaml`
- [ ] T005 [P] Copy `specs/001-spending-analysis-api/contracts/analysis-get-controller.yaml` to `Application/src/main/resources/swagger/analysis/analysis-get-controller.yaml`
- [ ] T006 Run `./mvnw -pl Application generate-sources` to generate `AnalysisGetApi`, `AnalysisGetApiDelegate`, `MonthlySummary`, `MonthlySummaryResponse`, and `MonthlySummaryListResponse` into `Application/target/generated-sources/`
- [ ] T007 Create `Application/src/main/java/at/ymeri/my/finance/application/mapper/AnalysisMapper.java` — MapStruct `@Mapper` mapping `MonthlySummaryDto` ↔ generated `MonthlySummary` API model; handle `Map<String, BigDecimal>` → `Map<String, BigDecimal>` pass-through for `spendingByCategory`

**Checkpoint**: Foundation ready — US1 and US2 can now proceed.

---

## Phase 3: User Story 1 — Monthly Financial Summary (Priority: P1) 🎯 MVP

**Goal**: `GET /api/v1/analysis/monthly?year={year}&month={month}` returns a complete monthly
summary (totalIncome, totalExpenses, netBalance, spendingByCategory).

**Independent test**: Start app, `POST` a bill and an income with dates in May 2026,
call `GET /api/v1/analysis/monthly?year=2026&month=5`, verify correct totals and category map.

- [ ] T008 [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/analysis/GetSpendingAnalysisServiceImplTest.java` — unit tests using mock `GetSpendingAnalysisPersistencePort`: (a) correct BigDecimal aggregation, (b) netBalance = totalIncome − totalExpenses, (c) uncategorised bills excluded from spendingByCategory map but counted in totalExpenses, (d) empty month returns all-zero summary
- [ ] T009 [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/analysis/AnalysisGetController.java` implementing generated `AnalysisGetApiDelegate` — implement `getMonthlySummary(Integer year, Integer month)`: validate month in [1,12] (return 400 if not), delegate to `GetSpendingAnalysisService.getMonthlySummary()`, map result via `AnalysisMapper`, return `ResponseEntity<MonthlySummaryResponse>` with `200 OK`
- [ ] T010 [US1] Write integration test `integration-tests/src/test/java/at/ymeri/my/finance/AnalysisGetControllerIntegrationTest.java` using TestContainers: (a) seed bill + income for a month, verify `GET /analysis/monthly` returns correct totals; (b) empty month returns zeros; (c) month=13 returns 400

---

## Phase 4: User Story 2 — Period Financial Summary (Priority: P2)

**Goal**: `GET /api/v1/analysis/period?from={date}&to={date}` returns a list of monthly summaries,
one per calendar month in the range.

**Independent test**: Call `GET /api/v1/analysis/period?from=2026-01-01&to=2026-03-31` with data
seeded across three months — verify three entries returned in order with correct per-month totals.

- [ ] T011 [US2] Implement `getPeriodSummary(LocalDate from, LocalDate to)` in `Application/src/main/java/at/ymeri/my/finance/controller/analysis/AnalysisGetController.java` — validate `from` is not after `to` (return 400 if so), delegate to `GetSpendingAnalysisService.getSummaryForPeriod()`, map list via `AnalysisMapper`, return `ResponseEntity<MonthlySummaryListResponse>` with `200 OK`
- [ ] T012 [P] [US2] Add period endpoint tests to `integration-tests/src/test/java/at/ymeri/my/finance/AnalysisGetControllerIntegrationTest.java`: (a) multi-month range returns correct entry count and per-month totals; (b) from after to returns 400; (c) range with months having no data returns zero-value entries

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T013 Run `./mvnw clean package` and verify full build passes with all new tests green
- [ ] T014 [P] Run `./mvnw -pl Domain test` and confirm `GetSpendingAnalysisServiceImplTest` passes
- [ ] T015 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows the `analysisGet` tag with both endpoints documented

---

## Dependencies

```
T001, T002 (parallel) → T003
T004, T005 (parallel) → T006 → T007
T003 + T007 → T008, T009 (US1 phase)
T009 → T010 (integration test needs controller)
T010 → T011 (US2 builds on US1 controller)
T011 → T012
T012 → T013, T014, T015
```

## Parallel Execution Opportunities

**Phase 2**: T001 and T002 can run in parallel (different repository files).
T004 and T005 can run in parallel (different YAML files).

**Phase 5**: T014 and T015 can run in parallel after T013.

## Implementation Strategy

| Phase | Deliverable | Value |
|-------|-------------|-------|
| Phase 2 (T001–T007) | Wiring complete, no endpoint yet | Infrastructure ready |
| Phase 3 (T008–T010) | Monthly endpoint live, tested | MVP — demonstrable feature |
| Phase 4 (T011–T012) | Period endpoint live, tested | Full feature complete |
| Phase 5 (T013–T015) | Build + smoke verified | Ship-ready |

**MVP scope**: Complete Phases 2 and 3 (T001–T010). The monthly summary endpoint alone delivers
the primary user value and satisfies US1 acceptance criteria independently.
