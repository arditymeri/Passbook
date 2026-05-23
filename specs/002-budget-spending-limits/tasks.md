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

- [x] T001 Create domain data classes: `BudgetDto.java`, `BudgetStatus.java` (enum), `BudgetStatusDto.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/`
- [x] T002 [P] Create domain API interfaces: `SetBudgetService.java`, `GetBudgetService.java`, `DeleteBudgetService.java`, `GetBudgetStatusService.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/api/`
- [x] T003 [P] Create SPI persistence port interfaces: `SetBudgetPersistencePort.java`, `GetBudgetPersistencePort.java`, `DeleteBudgetPersistencePort.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/`
- [x] T004 Create `BudgetEntity.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/`
- [x] T005 Create `BudgetRepository.java` with derived queries in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/`
- [x] T006 Create `BudgetMapper.java` (Infrastructure) in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/mapper/`
- [x] T007 Copy four contract YAML files to `Application/src/main/resources/swagger/budget/`
- [x] T008 Add three `<execution>` blocks (`budget-post`, `budget-get`, `budget-delete`) to `Application/pom.xml`
- [x] T009 Run `./mvnw -pl Application generate-sources` — generated `BudgetCreateApi`, `BudgetGetApi`, `BudgetDeleteApi`, and model classes
- [x] T010 Create `BudgetMapper.java` (Application) in `Application/src/main/java/at/ymeri/my/finance/application/mapper/`

**Checkpoint**: Foundation ready — US1, US2, and US3 can now proceed.

---

## Phase 3: User Story 1 — Set Monthly Budget (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/budgets` creates or updates a spending limit for a category+month.

- [x] T011 [US1] Write `SetBudgetServiceImplTest.java` in `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T012 [US1] Implement `SetBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T013 [US1] Create `SetBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/`
- [x] T014 [US1] Create `GetBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/`
- [x] T015 [US1] Create `BudgetCreateController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/`
- [x] T016 [US1] Write `BudgetControllerIntegrationTest.java` in `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/`

---

## Phase 4: User Story 2 — Budget vs. Actual Status (Priority: P2)

**Goal**: `GET /api/v1/budgets/status?year={year}&month={month}` returns per-category status.

- [x] T017 [US2] Write `GetBudgetStatusServiceImplTest.java` in `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T018 [US2] Implement `GetBudgetStatusServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T019 [US2] Create `BudgetGetController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/`
- [x] T020 [US2] Add status endpoint integration tests to `BudgetControllerIntegrationTest.java`

---

## Phase 5: User Story 3 — List and Delete Budgets (Priority: P3)

**Goal**: `GET /api/v1/budgets` lists budgets; `DELETE /api/v1/budgets/{id}` removes one.

- [x] T021 [US3] Implement `GetBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T022 [P] [US3] Implement `DeleteBudgetServiceImpl.java` in `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/`
- [x] T023 [P] [US3] Create `DeleteBudgetPostgresAdapter.java` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/`
- [x] T024 [US3] Add `listBudgets()` to `BudgetGetController.java`
- [x] T025 [US3] Create `BudgetDeleteController.java` in `Application/src/main/java/at/ymeri/my/finance/controller/budget/`
- [x] T026 [US3] Add list and delete integration tests to `BudgetControllerIntegrationTest.java`

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T027 Run `./mvnw clean package` and verify full build passes — BLOCKED: Java 21 missing from jenv (run `! jenv add <path-to-jdk-21>` to unblock)
- [ ] T028 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows `budgetCreate`, `budgetGet`, `budgetDelete` tags — BLOCKED: app cannot start without Java 21

---

## Dependencies

```
T001, T002, T003 (parallel) → T004, T005, T006 (parallel)
T007, T008 → T009 → T010
All of T001–T010 → US phases

US1: T011 → T012 → T013, T014 (parallel) → T015 → T016
US2: T016 complete → T017 → T018 → T019 → T020
US3: T014 complete → T021, T022, T023 (parallel) → T024, T025 → T026

T026 → T027, T028
```

## Implementation Strategy

| Phase | Deliverable | Value |
|-------|-------------|-------|
| Phase 2 (T001–T010) | All wiring in place | Foundation for all stories |
| Phase 3 (T011–T016) | POST /budgets live and tested | MVP — users can set spending limits |
| Phase 4 (T017–T020) | GET /budgets/status live | Core value: budget vs. actual visible |
| Phase 5 (T021–T026) | GET /budgets + DELETE live | Full lifecycle management |
| Phase 6 (T027–T028) | Build + smoke verified | Ship-ready |
