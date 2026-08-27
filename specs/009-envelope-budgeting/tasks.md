---
description: "Task list for Envelope Budgeting"
---

# Tasks: Envelope Budgeting

**Input**: Design documents from `/specs/009-envelope-budgeting/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Included — the constitution's Test-First principle (VI) is marked mandatory in this
feature's plan.md, matching every prior feature. Domain unit tests are written immediately
alongside (before, where noted) the service they cover; integration tests extend the existing
`BudgetControllerIntegrationTest`.

**Organization**: Tasks are grouped by user story so each of the four stories can be built,
tested, and demoed independently, in priority order.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure:

- `Application/src/main/resources/swagger/budget/` — OpenAPI specs
- `Application/src/main/java/at/ymeri/my/finance/controller/budget/` — REST controllers
- `Application/src/main/java/at/ymeri/my/finance/application/mapper/` — Application-layer MapStruct mappers
- `Domain/src/main/java/at/ymeri/my/finance/domain/` — business logic, DTOs, ports
- `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/` — JPA entities, repositories, Postgres adapters
- `frontend/src/` — React components, hooks, API client, types
- `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/` — TestContainers tests

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wire the new OpenAPI contracts into the build so generated code exists for later phases

- [X] T001 [P] Add `unallocated` to `budgetStatusResponse` and `envelopeBalance` to `budgetStatusEntry`, and add the `transferAllocationRequest`/`transferAllocationResponse`/`repeatAllocationsRequest`/`repeatAllocationsResponse` schemas, in `Application/src/main/resources/swagger/budget/budget-model.yaml` (copy field definitions from `specs/009-envelope-budgeting/contracts/budget-model.yaml`)
- [X] T002 [P] Create `Application/src/main/resources/swagger/budget/budget-transfer-controller.yaml` (copy from `specs/009-envelope-budgeting/contracts/budget-transfer-controller.yaml`)
- [X] T003 [P] Create `Application/src/main/resources/swagger/budget/budget-repeat-controller.yaml` (copy from `specs/009-envelope-budgeting/contracts/budget-repeat-controller.yaml`)
- [X] T004 Add `budget-transfer` and `budget-repeat` `<execution>` blocks to the OpenAPI generator plugin in `Application/pom.xml`, mirroring the existing `budget-post`/`budget-get`/`budget-delete` executions (depends on T002, T003)
- [X] T005 Run `./mvnw -pl Application generate-sources` to generate `BudgetTransferApi`, `BudgetRepeatApi`, and the new/updated model classes (`BudgetStatusResponse`, `BudgetStatusEntry`, `TransferAllocationRequest/Response`, `RepeatAllocationsRequest/Response`) into `Application/target/generated-sources/` (depends on T001, T004)

**Checkpoint**: Generated API surface exists; Application-layer controllers can now be written against it.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The reusable pieces every user story needs — the reused `Budget` full-history read,
the new `AllocationTransfer` read-side entity/port/adapter, and the shared balance-derivation
helper. Only the read side of `AllocationTransfer` is built here; the write side belongs to US3
(no story before US3 needs to create a transfer).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T006 Add `getAll()` to `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/GetBudgetPersistencePort.java`
- [X] T007 Implement `getAll()` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/GetBudgetPostgresAdapter.java` (depends on T006)
- [X] T008 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/AllocationTransferDto.java` (fields: `id`, `fromCategoryId`, `toCategoryId`, `year`, `month`, `amount`, `createdAt`)
- [X] T009 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/AllocationTransferEntity.java` (`@Entity`, same field shape as T008, `amount` as `BigDecimal`, `createdAt` as `OffsetDateTime`)
- [X] T010 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/AllocationTransferRepository.java` (Spring Data `JpaRepository<AllocationTransferEntity, UUID>`)
- [X] T011 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/mapper/AllocationTransferMapper.java` (MapStruct, `AllocationTransferEntity` ↔ `AllocationTransferDto`, mirrors the existing `BudgetMapper`)
- [X] T012 Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/GetAllocationTransferPersistencePort.java` with `List<AllocationTransferDto> getAll()` (depends on T008)
- [X] T013 Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/AllocationTransferPostgresAdapter.java` implementing `GetAllocationTransferPersistencePort.getAll()` (depends on T009, T010, T011, T012)
- [X] T014 [P] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/EnvelopeBalancesTest.java` covering `unallocatedAsOf(year, month)`: zero allocations, some allocations in one month, allocations spanning multiple months summed cumulatively, income and allocations in different months (needs T015 to exist to compile, but MUST fail before T015's logic is implemented)
- [X] T015 Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/EnvelopeBalances.java` with `unallocatedAsOf(int year, int month)`, composed from `GetBudgetPersistencePort.getAll()` and `GetIncomePersistencePort.getAll()` per the formula in `data-model.md` (depends on T006, T014)

**Checkpoint**: `AllocationTransfer` read-side and the cumulative-balance helper exist and are unit-tested. User story implementation can now begin.

---

## Phase 3: User Story 1 - See What's Left to Assign (Priority: P1) 🎯 MVP

**Goal**: Opening the budgeting view shows total income to date, total allocated to date, and the
resulting unallocated balance — including whatever carried over from prior months — with an
over-allocated month visually distinguished from one with money still unassigned.

**Independent Test**: With one or more allocations already created via `POST /budgets` (as every
budget has been created until now — this story doesn't yet need a creation UI), open the
budgeting view and verify the displayed unallocated figure equals cumulative income minus
cumulative allocations to date.

### Tests for User Story 1

- [X] T016 [P] [US1] Add cases to `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/GetBudgetStatusServiceImplTest.java` asserting the response's `unallocated` field: no allocations (equals total income), allocations in the current month only, allocations carried in from a prior month (must still be included)

### Implementation for User Story 1

- [X] T017 [US1] Add `unallocated` field to `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/BudgetStatusDto.java`'s containing response shape — add a sibling `getUnallocated()` result alongside the existing `List<BudgetStatusDto>` in `GetBudgetStatusServiceImpl` (introduce a small wrapper return type, e.g. `BudgetStatusResult { List<BudgetStatusDto> entries; BigDecimal unallocated; }`, in the same file or package)
- [X] T018 [US1] Wire `EnvelopeBalances.unallocatedAsOf(year, month)` into `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/GetBudgetStatusServiceImpl.java` and return it via the new wrapper from T017 (depends on T015, T017, T016 must fail first)
- [X] T019 [US1] Update `Application/src/main/java/at/ymeri/my/finance/controller/budget/BudgetGetController.java` to map the new `unallocated` field onto `BudgetStatusResponse` (depends on T018, T005)
- [X] T020 [P] [US1] Add `unallocated: number` to `BudgetStatusResponse`-equivalent type and extend `BudgetStatusEntry` in `frontend/src/types/index.ts`
- [X] T021 [P] [US1] Add `fetchBudgets(year, month)` to `frontend/src/api/client.ts` (`GET /api/v1/budgets`, mirrors the existing `fetchBudgetStatus`)
- [X] T022 [US1] Create `frontend/src/hooks/useBudgetAllocations.ts` fetching budgets + budget status for the selected month (mirrors `useAccounts.ts`) (depends on T020, T021)
- [X] T023 [US1] Create `frontend/src/components/BudgetingPage.tsx` — header showing total income, total allocated, and unallocated, colour-coding unallocated red when negative (mirrors the negative-balance treatment in `AccountList.tsx` from feature 007) (depends on T022)
- [X] T024 [US1] Add a `'budgeting'` view state and nav button to `frontend/src/App.tsx`, rendering `BudgetingPage` (depends on T023)
- [X] T025 [US1] Add an integration test case to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BudgetControllerIntegrationTest.java`: seed income and allocations across two consecutive months, verify `GET /budgets/status` for the second month returns `unallocated` reflecting both months' cumulative totals (depends on T019)

**Checkpoint**: User Story 1 is fully functional — the budgeting view accurately shows the running unallocated balance for any month, using allocations created via the API as before.

---

## Phase 4: User Story 2 - Assign Income to a Category (Priority: P1)

**Goal**: A user can assign an amount to a category for the current month directly from the app
(the first allocation-creation UI this app has ever had), see the category's envelope balance and
the header's unallocated figure update, and INCOME-type categories are excluded as targets both in
the picker and by the API.

**Independent Test**: Assign an amount to a category with no prior allocation, verify its envelope
balance and the month's unallocated figure both update by that amount; change the amount and
verify it updates in place rather than duplicating; attempt to target an INCOME category and
verify it is rejected.

### Tests for User Story 2

- [X] T026 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/EnvelopeBalancesTest.java` cases for `envelopeBalanceAsOf(categoryId, year, month)`: zero allocations/spend, allocated with no spend, allocated with some spend (including a reversed/corrected bill netting to its post-correction value per feature 008), allocations carried in from a prior month
- [X] T027 [P] [US2] Add a case to `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/SetBudgetServiceImplTest.java` asserting an `IllegalArgumentException` when the target category's type is `INCOME`

### Implementation for User Story 2

- [X] T028 [US2] Add `envelopeBalanceAsOf(String categoryId, int year, int month)` to `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/EnvelopeBalances.java`, composed from `GetBudgetPersistencePort.getAll()`, `GetAllocationTransferPersistencePort.getAll()`, and `GetBillPersistencePort.getAll()` per the formula in `data-model.md` (depends on T012, T015, T026 must fail first)
- [X] T029 [US2] Add `envelopeBalance` to `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/BudgetStatusDto.java` and wire `EnvelopeBalances.envelopeBalanceAsOf` into `GetBudgetStatusServiceImpl.java` per category (depends on T028)
- [X] T030 [US2] Add the category-type check (`EXPENSE` or `BOTH` only) to `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/SetBudgetServiceImpl.java` (depends on T027 must fail first)
- [X] T031 [US2] Update `Application/src/main/java/at/ymeri/my/finance/controller/budget/BudgetGetController.java` to map `envelopeBalance` onto `BudgetStatusEntry` (depends on T029, T019)
- [X] T032 [P] [US2] Add `createOrUpdateBudget(request)` to `frontend/src/api/client.ts` (`POST /api/v1/budgets`)
- [X] T033 [US2] Create `frontend/src/components/AllocationForm.tsx` — category picker filtered to `EXPENSE`/`BOTH` types, amount field (`type="text"`, string state, per the project's money-input convention), submit calls `createOrUpdateBudget` (depends on T032)
- [X] T034 [US2] Wire `AllocationForm` into `BudgetingPage.tsx`, showing each category's current-month assigned amount and envelope balance, refreshing `useBudgetAllocations` on submit (depends on T033, T023)
- [X] T035 [US2] Add integration test cases to `BudgetControllerIntegrationTest.java`: assign to a category and verify `envelopeBalance` in `GET /budgets/status`; assign, then reassign, and verify upsert (not duplication); attempt `POST /budgets` targeting an INCOME category and verify 400 (depends on T030, T031)

**Checkpoint**: Users can assign money to categories entirely from the app. Combined with US1, this is the core give-every-dollar-a-job loop.

---

## Phase 5: User Story 3 - Move Money Between Categories (Priority: P2)

**Goal**: A user can move an amount from one category's envelope balance to another's, rejected if
it exceeds the source's currently available balance, with the overall unallocated total unchanged.

**Independent Test**: With two categories holding positive envelope balances, move an amount from
one to the other and verify the source decreases, the destination increases, and unallocated is
unchanged; attempt to move more than the source's available balance and verify rejection.

### Tests for User Story 3

- [X] T036 [P] [US3] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/MoveAllocationServiceImplTest.java`: successful move updates both categories' envelope balances and leaves unallocated unchanged; move exceeding the source's available balance throws; move with `fromCategoryId == toCategoryId` throws; move to/from a nonexistent category throws

### Implementation for User Story 3

- [X] T037 [P] [US3] Add `AddAllocationTransferPersistencePort.java` to `Domain/src/main/java/at/ymeri/my/finance/domain/spi/budget/` with `AllocationTransferDto add(AllocationTransferDto transfer)`
- [X] T038 [US3] Implement `add()` in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/budget/AllocationTransferPostgresAdapter.java` (now implements both `GetAllocationTransferPersistencePort` and `AddAllocationTransferPersistencePort`) (depends on T037, T013)
- [X] T039 [P] [US3] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/MoveAllocationService.java` (interface: `AllocationTransferDto moveAllocation(String fromCategoryId, String toCategoryId, int year, int month, BigDecimal amount)`)
- [X] T040 [US3] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/MoveAllocationServiceImpl.java`: validate amount > 0, categories differ and exist, amount ≤ `EnvelopeBalances.envelopeBalanceAsOf(fromCategoryId, ...)`, then persist via `AddAllocationTransferPersistencePort` (depends on T028, T037, T038, T039, T036 must fail first)
- [X] T041 [US3] Create `Application/src/main/java/at/ymeri/my/finance/controller/budget/BudgetTransferController.java` implementing the generated `BudgetTransferApi`, delegating to `MoveAllocationService` (depends on T040, T005)
- [X] T042 [US3] Extend `Application/src/main/java/at/ymeri/my/finance/application/mapper/BudgetMapper.java` with `TransferAllocationRequest` → `MoveAllocationService` call params and `AllocationTransferDto` → `TransferAllocationResponse` mappings (including post-move `fromEnvelopeBalance`/`toEnvelopeBalance`, sourced from `GetBudgetStatusService`) (depends on T041)
- [X] T043 [P] [US3] Add `moveAllocation(request)` to `frontend/src/api/client.ts` (`POST /api/v1/budgets/transfer`)
- [X] T044 [US3] Create `frontend/src/components/MoveAllocationDialog.tsx` — source/destination category pickers, amount field, inline error on rejection (depends on T043)
- [X] T045 [US3] Wire a "Move money" action into `BudgetingPage.tsx` opening `MoveAllocationDialog`, refreshing on success (depends on T044, T034)
- [X] T046 [US3] Add integration test cases to `BudgetControllerIntegrationTest.java`: successful transfer via `POST /budgets/transfer` updates both categories' status; over-the-balance transfer returns 400 (depends on T042)

**Checkpoint**: Money can be reassigned between envelopes without a detour through "unallocated."

---

## Phase 6: User Story 4 - Repeat Last Month's Assignments (Priority: P3)

**Goal**: A user can top up a month's category allocations with a prior month's assignment
amounts in one action, previewed client-side before confirming, with a clear message when the
source month has nothing to repeat.

**Independent Test**: With a source month holding several category allocations and a target month
with none, repeat the source into the target and verify every category received a new allocation
entry equal to the source amount, added on top of any existing/carried balance.

### Tests for User Story 4

- [X] T047 [P] [US4] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/budget/RepeatAllocationsServiceImplTest.java`: repeating into a month with no existing allocations tops each category up from zero; repeating into a month with existing allocations adds on top (not overwrite); repeating a source month with zero allocations throws

### Implementation for User Story 4

- [X] T048 [P] [US4] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/RepeatAllocationsService.java` (interface: `List<AllocationTopUp> repeatAllocations(int fromYear, int fromMonth, int toYear, int toMonth)`, with a small `AllocationTopUp{categoryId, amountAdded, newMonthlyAmount}` record/DTO)
- [X] T049 [US4] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/RepeatAllocationsServiceImpl.java`: read source month's allocations via `GetBudgetPersistencePort`, throw if empty, else for each upsert target month = existing target amount (0 if none) + source amount via `SetBudgetPersistencePort` (depends on T047 must fail first)
- [X] T050 [US4] Create `Application/src/main/java/at/ymeri/my/finance/controller/budget/BudgetRepeatController.java` implementing the generated `BudgetRepeatApi`, delegating to `RepeatAllocationsService` (depends on T049, T005)
- [X] T051 [US4] Extend `Application/src/main/java/at/ymeri/my/finance/application/mapper/BudgetMapper.java` with `RepeatAllocationsRequest`/`RepeatAllocationsResponse` mappings (depends on T050)
- [X] T052 [P] [US4] Add `repeatAllocations(request)` to `frontend/src/api/client.ts` (`POST /api/v1/budgets/repeat`)
- [X] T053 [US4] Create `frontend/src/components/RepeatAllocationsDialog.tsx` — computes the client-side preview (which categories will receive a top-up and by how much) from the already-fetched source/target month allocation lists, shows it, then calls `repeatAllocations` on confirm; shows "nothing to repeat" when the source month is empty (depends on T052)
- [X] T054 [US4] Wire a "Repeat last month" action into `BudgetingPage.tsx` opening `RepeatAllocationsDialog`, refreshing on success (depends on T053, T034)
- [X] T055 [US4] Add integration test cases to `BudgetControllerIntegrationTest.java`: repeat into an empty target month; repeat into a month with existing allocations (additive, not overwritten); repeat an empty source month returns 400 (depends on T051)

**Checkpoint**: All four user stories are independently functional — the full envelope-budgeting loop works end to end.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full-stack verification once all stories are implemented

- [ ] T056 Run `./mvnw clean package` and verify the full build passes with all new/updated tests green — BLOCKED (partial): `./mvnw clean package -pl '!integration-tests'` passes clean (Application, Domain, Infrastructure, Launcher, Events all build and test green); the full reactor build fails at `integration-tests` only because no Docker daemon is available in this environment (`docker ps` reports no socket) — same class of environment gap 001/002 hit with a missing Java 21, not a code defect. Re-run in an environment with Docker to close this out.
- [X] T057 [P] Run `./mvnw -pl Domain test` and confirm `EnvelopeBalancesTest`, `GetBudgetStatusServiceImplTest`, `SetBudgetServiceImplTest`, `MoveAllocationServiceImplTest`, and `RepeatAllocationsServiceImplTest` all pass
- [ ] T058 [P] Run `./mvnw -pl integration-tests test` and confirm all `BudgetControllerIntegrationTest` cases pass — BLOCKED: requires a Docker daemon (TestContainers) not available in this environment. All new cases (009 US1-US4 sections) compile cleanly against the real generated API models; not executed.
- [X] T059 [P] Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new components/hook/types
- [ ] T060 Execute `specs/009-envelope-budgeting/quickstart.md` end to end in the browser — BLOCKED: requires the full Docker Compose stack (Postgres) not available in this environment.
- [ ] T061 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows `budgetTransfer` and `budgetRepeat` tags, and that `budgetGet`'s status schema includes `unallocated`/`envelopeBalance` — BLOCKED: requires a running app instance (same Docker gap as T058/T060). The underlying OpenAPI specs (`Application/src/main/resources/swagger/budget/*.yaml`) were verified directly and generate the expected `BudgetTransferApi`/`BudgetRepeatApi` and schema fields (confirmed via `generate-sources` output).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup (needs generated sources from T005 only where it touches Application code; the Domain/Infrastructure tasks T006–T015 have no dependency on Phase 1 and can start in parallel with it) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational (T006, T015)
- **User Story 2 (Phase 4)**: Depends on Foundational (T012, T015) and reuses US1's `GetBudgetStatusServiceImpl`/`BudgetingPage.tsx` (T018, T023) — built after US1 but independently testable via the API on its own
- **User Story 3 (Phase 5)**: Depends on Foundational (T013) and US2's `EnvelopeBalances.envelopeBalanceAsOf` (T028) and `BudgetingPage.tsx` (T034)
- **User Story 4 (Phase 6)**: Depends on Foundational (T006) and US2's `BudgetingPage.tsx` (T034); independent of US3
- **Polish (Phase 7)**: Depends on all four user stories being complete

### User Story Dependencies

Per spec.md, US2 depends on US1 existing (nothing to display otherwise), US3 depends on US2 (moves
an existing allocation), and US4 depends on US1/US2 (repeats existing allocations) — so unlike a
feature where every story is independent, this feature's stories are intentionally sequential in
priority order. Each is still independently *testable* once its dependencies are met (see each
phase's Independent Test).

### Parallel Opportunities

- Setup: T001, T002, T003 in parallel; T004 and T005 are sequential after them
- Foundational: T008–T011 in parallel (different new files); T014 in parallel with T008–T013
- Within each user story, the `[P]`-marked test-writing and frontend-type/client tasks can run in parallel with each other, but implementation tasks that touch the same file (e.g. `GetBudgetStatusServiceImpl.java` across T018/T029, or `BudgetingPage.tsx` across T023/T034/T045/T054) are sequential

---

## Parallel Example: Foundational Phase

```text
# Launch together (different files, no dependency on each other):
T008: Create AllocationTransferDto.java
T009: Create AllocationTransferEntity.java
T010: Create AllocationTransferRepository.java
T011: Create AllocationTransferMapper.java
T014: Write EnvelopeBalancesTest.java (unallocatedAsOf cases)

# Then sequentially:
T012: Create GetAllocationTransferPersistencePort.java   (needs T008)
T013: Create AllocationTransferPostgresAdapter.java       (needs T009, T010, T011, T012)
T015: Implement EnvelopeBalances.unallocatedAsOf          (needs T006, T014 failing first)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3 (US1): running unallocated balance, read-only budgeting view
4. Complete Phase 4 (US2): allocation-creation UI, envelope balances, category-type enforcement
5. **STOP and VALIDATE**: give-every-dollar-a-job loop works end to end — assign money, watch unallocated and envelope balances update correctly, including across a month boundary (carryover)
6. Add Phase 5 (US3) and Phase 6 (US4) when ready — both are refinements, not prerequisites for daily use

### Incremental Delivery

1. Setup + Foundational → shared plumbing ready
2. US1 → unallocated balance visible (allocations still created via Swagger, as today)
3. US2 → the app's first allocation-creation UI; MVP complete
4. US3 → move money between envelopes without a detour through unallocated
5. US4 → stop re-typing the same amounts every month
6. Polish → full build, integration, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- `EnvelopeBalances` is deliberately built in two passes: `unallocatedAsOf` in Foundational (T015,
  needed by US1), `envelopeBalanceAsOf` in US2 (T028) — both live in the same class, added
  incrementally rather than speculatively building the whole helper before any story needs it
- Amount fields in `AllocationForm`, `MoveAllocationDialog` MUST use `type="text"` with string
  state, never `type="number"` (Constitution Principle IV, same rule as every existing money field)
- `MoveAllocationServiceImpl` and `RepeatAllocationsServiceImpl` never mutate an existing row —
  transfers are always new `AllocationTransfer` rows (T040), and repeats always upsert an
  *additive* target amount (T049), consistent with this feature's carryover model and with how
  feature 008 models corrections
- `RepeatAllocationsDialog`'s preview is computed entirely client-side from already-fetched data —
  there is no dry-run/preview endpoint (see `research.md`)
- Submit buttons in all three new forms/dialogs must be disabled during their `submitting` state,
  the same edge case already handled by `AddCategoryForm`/`AddAccountForm`
