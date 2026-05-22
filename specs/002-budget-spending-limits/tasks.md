---
description: "Task list for Budget / Spending Limits"
---

# Tasks: Budget / Spending Limits

**Input**: Design documents from `/specs/002-budget-spending-limits/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Included — Constitution Principle VI mandates unit tests for domain logic and
integration tests against a real database (no mocks).

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Domain API ports: `Domain/src/main/java/at/ymeri/my/finance/domain/api/`
- Domain SPI ports: `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/`
- Domain data: `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/`
- Domain services: `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`
- Domain tests: `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/`
- Infrastructure: `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/`
- Application mapper: `Application/src/main/java/at/ymeri/my/finance/application/mapper/`
- Application controller: `Application/src/main/java/at/ymeri/my/finance/controller/budget/`
- Swagger: `Application/src/main/resources/swagger/budget/`
- Integration tests: `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/`

---

## Phase 1: Setup

No new project structure or Maven modules required — feature wires into existing hexagonal layout.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure required by all three user stories. MUST be complete before
any user story work begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T001 Create domain data classes: `BudgetDto.java` (id, categoryId, year, month, limitAmount:BigDecimal), `BudgetStatus.java` (enum: UNDER_BUDGET, OVER_BUDGET), and `BudgetStatusDto.java` (categoryId, budgeted, actual, remaining: BigDecimal, status: BudgetStatus) in `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/`
- [ ] T002 [P] Create domain API interfaces: `SetBudgetService.java` (`setBudget(BudgetDto) → BudgetDto`), `GetBudgetService.java` (`getByYearAndMonth(int, int) → List<BudgetDto>`, `getById(UUID) → Optional<BudgetDto>`), `DeleteBudgetService.java` (`deleteBudget(UUID)`), `GetBudgetStatusService.java` (`getBudgetStatus(int year, int month) → List<BudgetStatusDto>`) in `Domain/src/main/java/at/ymeri/my/finance/domain/api/`
- [ ] T003 [P] Create SPI persistence port interfaces: `SetBudgetPersistencePort.java` (`upsert(BudgetDto) → BudgetDto`), `GetBudgetPersistencePort.java` (`findByYearAndMonth(int, int) → List<BudgetDto>`, `findByCategoryIdAndYearAndMonth(String, int, int) → Optional<BudgetDto>`, `existsById(UUID) → boolean`), `DeleteBudgetPersistencePort.java` (`deleteById(UUID)`) in `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/`
- [ ] T004 Create `BudgetEntity.java` annotated `@Entity @Table(name = "budget")` with fields: `UUID id` (UUID PK), `String categoryId`, `int year`, `int month`, `BigDecimal limitAmount` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/`
- [ ] T005 Create `BudgetRepository.java` extending `JpaRepository<BudgetEntity, UUID>` with derived queries `findByYearAndMonth(int year, int month)` and `findByCategoryIdAndYearAndMonth(String categoryId, int year, int month)` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/`
- [ ] T006 Create `BudgetMapper.java` (Infrastructure) as MapStruct `@Mapper` mapping `BudgetEntity ↔ BudgetDto` and `List<BudgetEntity> → List<BudgetDto>` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/mapper/`
- [ ] T007 Copy the four contract files from `specs/002-budget-spending-limits/contracts/` to `Application/src/main/resources/swagger/budget/`: `budget-model.yaml`, `budget-post-controller.yaml`, `budget-get-controller.yaml`, `budget-delete-controller.yaml`
- [ ] T008 Add three new `<execution>` blocks to `Application/pom.xml` for the OpenAPI generator: `budget-post` (apiPackage `${api-package}.budget`, inputSpec `swagger/budget/budget-post-controller.yaml`), `budget-get` (same package, `budget-get-controller.yaml`), `budget-delete` (same package, `budget-delete-controller.yaml`) — follow the exact pattern of the existing `account-post`, `account-get`, `account-delete` executions
- [ ] T009 Run `./mvnw -pl Application generate-sources` to generate `BudgetCreateApi`, `BudgetGetApi`, `BudgetDeleteApi` interfaces and model classes (`BudgetResponse`, `BudgetListResponse`, `BudgetStatusEntry`, `BudgetStatusResponse`, `CreateBudgetRequest`) into `Application/target/generated-sources/`
- [ ] T010 Create `BudgetMapper.java` (Application) as MapStruct `@Mapper` mapping `BudgetDto → BudgetResponse`, `List<BudgetDto> → List<BudgetResponse>`, and `BudgetStatusDto → BudgetStatusEntry` in `Application/src/main/java/at/ymeri/my/finance/application/mapper/`

**Checkpoint**: Foundation ready — US1, US2, and US3 can now proceed (US3 depends on US1 for the upsert adapter).

---

## Phase 3: User Story 1 — Set Monthly Budget (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/budgets` creates or updates a spending limit for a category+month.
Validates: `limitAmount > 0`, `month in [1,12]`, category must exist. Upserts on duplicate.

**Independent test**: POST a budget for an existing category and month, verify 200 with correct
fields; POST again with same category+month and a new amount, verify the limit is updated not
duplicated; POST with invalid limit → 400; POST with unknown categoryId → 404.

- [ ] T011 [US1] Write `SetBudgetServiceImplTest.java` in `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/` — unit tests: (a) valid budget is persisted and returned, (b) limitAmount ≤ 0 throws IllegalArgumentException, (c) unknown categoryId throws NoSuchElementException, (d) duplicate category+month calls upsert (findByCategoryIdAndYearAndMonth returns existing → updates limitAmount)
- [ ] T012 [US1] Implement `SetBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`: validate `limitAmount > 0` (throw IllegalArgumentException), validate `month in [1,12]`, call `GetCategoryPersistencePort.getCategoryById(categoryId)` (throw NoSuchElementException if absent), call `GetBudgetPersistencePort.findByCategoryIdAndYearAndMonth()` — if found update limitAmount on the existing dto and upsert, else upsert new; return result from `SetBudgetPersistencePort.upsert()`
- [ ] T013 [US1] Create `SetBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/` implementing `SetBudgetPersistencePort` — map dto to entity, call `budgetRepository.save()`, map result back to dto using `BudgetMapper.INSTANCE`
- [ ] T014 [US1] Create `GetBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/` implementing `GetBudgetPersistencePort` — implement `findByCategoryIdAndYearAndMonth`, `findByYearAndMonth`, and `existsById` using `BudgetRepository` and `BudgetMapper.INSTANCE`
- [ ] T015 [US1] Create `BudgetCreateController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/` implementing generated `BudgetCreateApi` — implement `createOrUpdateBudget(CreateBudgetRequest)`: map request to `BudgetDto`, call `SetBudgetService.setBudget()`, map result to `BudgetResponse` via `BudgetMapper.INSTANCE`, return `ResponseEntity.ok(response)`; catch `IllegalArgumentException` → 400, `NoSuchElementException` → 404
- [ ] T016 [US1] Write integration test class `BudgetControllerIntegrationTest.java` in `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/` (use `@SpringBootTest` + `@EmbeddedKafka` + `TestConfig` + `TestDataSourceConfig` pattern); add tests: (a) POST valid budget returns 200 with id; (b) POST same category+month updates limit; (c) POST limitAmount=0 returns 400; (d) POST unknown categoryId returns 404

---

## Phase 4: User Story 2 — Budget vs. Actual Status (Priority: P2)

**Goal**: `GET /api/v1/budgets/status?year={year}&month={month}` returns per-category budgeted,
actual, remaining, and OVER/UNDER status by merging budget records with bill aggregation.

**Independent test**: Seed a budget for Groceries (€500), seed bills totalling €420 for Groceries
in the same month, call GET /budgets/status — verify budgeted=500, actual=420, remaining=80,
status=UNDER_BUDGET. Also verify a category with spend but no budget shows OVER_BUDGET.

- [ ] T017 [US2] Write `GetBudgetStatusServiceImplTest.java` in `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/` — unit tests with mocked ports: (a) category under budget shows UNDER_BUDGET, correct remaining; (b) category over budget shows OVER_BUDGET, negative remaining; (c) category with spend but no budget shows budgeted=0, OVER_BUDGET; (d) category with budget but zero spend shows actual=0, UNDER_BUDGET
- [ ] T018 [US2] Implement `GetBudgetStatusServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`: inject `GetBudgetPersistencePort` and `GetSpendingAnalysisPersistencePort`; compute `LocalDate from/to` for the month; call `getBillsByPeriod(from, to)` and aggregate actual spend per categoryId; call `findByYearAndMonth(year, month)` for budgets; merge into `Map<String, BudgetStatusDto>` — compute `remaining = budgeted - actual`; set status to `OVER_BUDGET` if `actual > budgeted` (treat missing budget as budgeted=0); return as list
- [ ] T019 [US2] Create `BudgetGetController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/` implementing generated `BudgetGetApi`; implement `getBudgetStatus(Integer year, Integer month)`: validate month in [1,12] (400 if not), call `GetBudgetStatusService.getBudgetStatus()`, map result list via `BudgetMapper.INSTANCE.mapStatusList()`, return `ResponseEntity.ok(new BudgetStatusResponse().year(year).month(month).entries(entries))`
- [ ] T020 [US2] Add status endpoint integration tests to `BudgetControllerIntegrationTest.java`: (a) seed budget + bills, verify GET /budgets/status returns correct budgeted/actual/remaining/status; (b) category with spend but no budget appears as OVER_BUDGET; (c) empty month returns empty entries list; (d) invalid month returns 400

---

## Phase 5: User Story 3 — List and Delete Budgets (Priority: P3)

**Goal**: `GET /api/v1/budgets?year={year}&month={month}` lists all raw budget entries;
`DELETE /api/v1/budgets/{id}` removes a budget by ID.

**Independent test**: Create 3 budgets for different categories in the same month; GET /budgets
returns all 3; DELETE one by id → 204; GET /budgets returns 2 remaining; DELETE non-existent
id → 404.

- [ ] T021 [US3] Implement `GetBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/` — `getByYearAndMonth(int year, int month)` delegates to `GetBudgetPersistencePort.findByYearAndMonth()`; `getById(UUID id)` delegates to port, throws `NoSuchElementException` if absent
- [ ] T022 [P] [US3] Implement `DeleteBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/` — `deleteBudget(UUID id)`: call `GetBudgetPersistencePort.existsById(id)`, throw `NoSuchElementException` if not found, then call `DeleteBudgetPersistencePort.deleteById(id)`
- [ ] T023 [P] [US3] Create `DeleteBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/` implementing `DeleteBudgetPersistencePort` — call `budgetRepository.deleteById(id)`
- [ ] T024 [US3] Add `listBudgets(Integer year, Integer month)` to `BudgetGetController.java` — validate month, call `GetBudgetService.getByYearAndMonth()`, map to `BudgetListResponse` via `BudgetMapper.INSTANCE.mapList()`; return `ResponseEntity.ok(...)`
- [ ] T025 [US3] Create `BudgetDeleteController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/` implementing generated `BudgetDeleteApi` — implement `deleteBudget(String id)`: parse UUID, call `DeleteBudgetService.deleteBudget()`, return `ResponseEntity.noContent().build()`; catch `NoSuchElementException` → 404
- [ ] T026 [US3] Add list and delete integration tests to `BudgetControllerIntegrationTest.java`: (a) GET /budgets returns all budgets for a month; (b) DELETE /budgets/{id} returns 204 and budget no longer appears in list; (c) DELETE non-existent id returns 404

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T027 Run `./mvnw clean package` and verify full build passes with all new tests green — NOTE: requires Java 21 (currently missing from jenv; install with `! jenv add <path-to-jdk-21>` to unblock)
- [ ] T028 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows `budgetCreate`, `budgetGet`, and `budgetDelete` tags with all four endpoints documented

---

## Dependencies

```
T001, T002, T003 (parallel) → T004, T005, T006 (parallel)
T007, T008 → T009 → T010
All of T001–T010 → US phases

US1: T011 → T012 → T013, T014 (parallel) → T015 → T016
US2: T016 complete → T017 → T018 → T019 → T020
US3: T014 complete (GetBudgetPostgresAdapter) → T021, T022, T023 (parallel) → T024, T025 → T026

T026 → T027, T028
```

## Parallel Execution Opportunities

**Phase 2**: T001, T002, T003 can run in parallel (different packages/files).
T005, T006 can run in parallel after T004.

**US1**: T013 and T014 can run in parallel (different adapters).

**US3**: T022 and T023 can run in parallel (different service/adapter files).
T024 and T025 can run in parallel (different controllers).

## Implementation Strategy

| Phase | Deliverable | Value |
|-------|-------------|-------|
| Phase 2 (T001–T010) | All wiring in place | Foundation for all stories |
| Phase 3 (T011–T016) | POST /budgets live and tested | MVP — users can set spending limits |
| Phase 4 (T017–T020) | GET /budgets/status live | Core value: budget vs. actual visible |
| Phase 5 (T021–T026) | GET /budgets + DELETE live | Full lifecycle management |
| Phase 6 (T027–T028) | Build + smoke verified | Ship-ready |

**MVP scope**: Complete Phases 2 and 3 (T001–T016). Creating budgets alone delivers immediate
value and satisfies US1 acceptance criteria independently.
