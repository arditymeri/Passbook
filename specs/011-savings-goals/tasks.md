---
description: "Task list for Savings Goals"
---

# Tasks: Savings Goals

**Input**: Design documents from `/specs/011-savings-goals/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Included — the constitution's Test-First principle (VI) is marked mandatory in this
feature's plan.md, matching every prior feature. Domain unit tests are written immediately before
the service they cover; integration tests extend a new `SavingsGoalControllerIntegrationTest`.

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3 → US4).
Unlike 010 (where the "view" story had to be built before the "producer" story), here US1
("Create") is the natural producer and US2 ("See Progress") the natural consumer, so priority
order already matches dependency order — no reordering needed. `SavingsGoalProgress`, the shared
pure helper every story's response depends on, is built once in Foundational (percent complete,
remaining amount, achieved) with `paceStatus` stubbed to always return `null`, then extended in
US3 to compute real pace values — the same "shared helper built incrementally" pattern
`RecurringMatching` used across 010's stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure:

- `Application/src/main/resources/swagger/goal/` — OpenAPI specs
- `Application/src/main/java/at/ymeri/my/finance/controller/goal/` — REST controllers
- `Application/src/main/java/at/ymeri/my/finance/application/mapper/` — Application-layer MapStruct mappers
- `Domain/src/main/java/at/ymeri/my/finance/domain/` — business logic, DTOs, ports
- `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/` — JPA entities, repositories, Postgres adapters
- `frontend/src/` — React components, hooks, API client, types
- `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/` — TestContainers tests

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wire the new OpenAPI contracts into the build so generated code exists for later phases

- [X] T001 [P] Create `Application/src/main/resources/swagger/goal/goal-model.yaml` (copy from `specs/011-savings-goals/contracts/goal-model.yaml`)
- [X] T002 [P] Create `Application/src/main/resources/swagger/goal/goal-get-controller.yaml` (copy from `specs/011-savings-goals/contracts/goal-get-controller.yaml`)
- [X] T003 [P] Create `Application/src/main/resources/swagger/goal/goal-add-controller.yaml` (copy from `specs/011-savings-goals/contracts/goal-add-controller.yaml`)
- [X] T004 [P] Create `Application/src/main/resources/swagger/goal/goal-update-controller.yaml` (copy from `specs/011-savings-goals/contracts/goal-update-controller.yaml`)
- [X] T005 [P] Create `Application/src/main/resources/swagger/goal/goal-delete-controller.yaml` (copy from `specs/011-savings-goals/contracts/goal-delete-controller.yaml`)
- [X] T006 Add `goal-get`, `goal-add`, `goal-update`, and `goal-delete` `<execution>` blocks to the OpenAPI generator plugin in `Application/pom.xml`, mirroring the existing `recurring-*` executions (depends on T001–T005)
- [X] T007 Run `./mvnw -pl Application generate-sources` to generate `GoalGetApi`, `GoalAddApi`, `GoalUpdateApi`, `GoalDeleteApi`, and the new model classes into `Application/target/generated-sources/` (depends on T006)

**Checkpoint**: Generated API surface exists; Application-layer controllers can now be written against it.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `SavingsGoal` entity, fully wired (read and write), plus the shared progress-derivation
helper every story's response depends on — every user story needs both from its very first task.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/goal/PaceStatus.java` (enum: `ON_PACE`, `BEHIND_PACE`, `OVERDUE`)
- [X] T009 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/goal/SavingsGoalDto.java` (fields: `id`, `name`, `targetAmount` (BigDecimal), `targetDate` (nullable OffsetDateTime), `accountId`, `createdAt`)
- [X] T010 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/goal/SavingsGoalStatusDto.java` (all `SavingsGoalDto` fields plus `savedAmount`, `percentComplete`, `remainingAmount` (all BigDecimal), `achieved` (boolean), `paceStatus` (nullable `PaceStatus`) — mirrors `BudgetStatusDto`'s split from `BudgetDto`) (depends on T008, T009)
- [X] T011 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/SavingsGoalEntity.java` (`@Entity @Table(name = "savings_goal")`; `targetAmount` as `BigDecimal`, `targetDate` nullable `OffsetDateTime` column, `accountId`/`name` as `String`, `createdAt` as `OffsetDateTime`)
- [X] T012 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/SavingsGoalRepository.java` (Spring Data `JpaRepository<SavingsGoalEntity, UUID>` with `Optional<SavingsGoalEntity> findByAccountId(String accountId)`)
- [X] T013 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/mapper/SavingsGoalMapper.java` (MapStruct, `SavingsGoalEntity` ↔ `SavingsGoalDto`, mirrors `RecurringSeriesMapper`'s (Infrastructure) simplicity)
- [X] T014 Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/goal/GetSavingsGoalPersistencePort.java` with `List<SavingsGoalDto> getAll()`, `Optional<SavingsGoalDto> findById(String id)`, `Optional<SavingsGoalDto> findByAccountId(String accountId)` (depends on T009)
- [X] T015 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/goal/AddSavingsGoalPersistencePort.java` with `SavingsGoalDto add(SavingsGoalDto goal)` (depends on T009)
- [X] T016 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/goal/UpdateSavingsGoalPersistencePort.java` with `SavingsGoalDto update(String id, SavingsGoalDto goal)` (depends on T009)
- [X] T017 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/goal/DeleteSavingsGoalPersistencePort.java` with `void delete(String id)`
- [X] T018 Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/goal/SavingsGoalPostgresAdapter.java` implementing `GetSavingsGoalPersistencePort`, `AddSavingsGoalPersistencePort`, `UpdateSavingsGoalPersistencePort`, and `DeleteSavingsGoalPersistencePort` (depends on T011, T012, T013, T014, T015, T016, T017)
- [X] T019 [P] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/SavingsGoalProgressTest.java`: `percentComplete`/`remainingAmount` for saved below, at, and above target (capped at 100% / floored at 0 respectively); `achieved` true once saved reaches or exceeds target, false below it; `paceStatus` is always `null` at this stage (no target-date logic yet — added in US3)
- [X] T020 Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/SavingsGoalProgress.java`: a static `SavingsGoalStatusDto of(SavingsGoalDto goal, BigDecimal savedAmount)` computing `percentComplete`, `remainingAmount`, `achieved`; `paceStatus` stubbed to always return `null` for now (depends on T010, T019 must fail first)

**Checkpoint**: `SavingsGoal` persistence and progress derivation exist end to end. User story implementation can now begin.

---

## Phase 3: User Story 1 - Create a Savings Goal (Priority: P1)

**Goal**: A user creates a goal with a name, target amount, optional target date, and a linked
account; creation is rejected if the account is already linked to another goal.

**Independent Test**: Create a goal with a name, target amount, and linked account via `POST
/savings-goals`; verify the response carries those exact details. Attempt a second goal on the
same account; verify it is rejected with 400.

### Tests for User Story 1

- [X] T021 [P] [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/AddSavingsGoalServiceImplTest.java`: creates a goal with valid name/targetAmount/accountId (with and without a targetDate), returning a `SavingsGoalStatusDto` with correct derived fields; rejects a `targetAmount` that is zero or negative; rejects an unknown `accountId` (`NoSuchElementException`); rejects an `accountId` already linked to another goal (`IllegalStateException`)

### Implementation for User Story 1

- [X] T022 [P] [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/AddSavingsGoalService.java` interface
- [X] T023 [US1] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/AddSavingsGoalServiceImpl.java`: validates `targetAmount > 0`, resolves the account via `GetAccountService.getAccountById` (propagates `NoSuchElementException` if absent), rejects via `IllegalStateException` if `GetSavingsGoalPersistencePort.findByAccountId` already finds a goal for that account, persists via `AddSavingsGoalPersistencePort.add`, and returns `SavingsGoalProgress.of(goal, account.getBalance())` (depends on T014, T015, T020, T022, T021 must fail first)
- [X] T024 [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/goal/SavingsGoalAddController.java` implementing the generated `GoalAddApi`, delegating to `AddSavingsGoalService`, mapping `NoSuchElementException`→404 and `IllegalStateException`→400 (depends on T023, T007)
- [X] T025 [US1] Create `Application/src/main/java/at/ymeri/my/finance/application/mapper/SavingsGoalMapper.java` (MapStruct, `createSavingsGoalRequest` → `SavingsGoalDto` fields, `SavingsGoalStatusDto` → `savingsGoalResponse`) (depends on T024)
- [X] T026 [P] [US1] Add `PaceStatusValue`, `SavingsGoalStatus` types and a `createSavingsGoal` function to `frontend/src/types/index.ts` and `frontend/src/api/client.ts`
- [X] T027 [US1] Create `frontend/src/components/SavingsGoalForm.tsx` — dialog with name, target amount, optional target date, and an account select (from the existing `Account[]` list); on submit calls `createSavingsGoal` or (when given an existing goal, for US4's reuse) `updateSavingsGoal` (depends on T026)
- [X] T028 [US1] Add cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/SavingsGoalControllerIntegrationTest.java` (new file): `POST /savings-goals` creates a goal and returns the correct name/targetAmount/accountId; a second goal on the same account is rejected with 400; an unknown `accountId` is rejected with 404; a zero/negative `targetAmount` is rejected with 400 (depends on T024, T025)

**Checkpoint**: Users can create a savings goal entirely through the API; the reusable form component exists (wired into a page in US2).

---

## Phase 4: User Story 2 - See Goal Progress at a Glance (Priority: P1)

**Goal**: Every goal's saved amount, percent complete, remaining amount, and achieved status are
derived from its linked account's current balance and shown in a goals list.

**Independent Test**: Create a goal linked to an account with a known balance below the target;
`GET /savings-goals` and verify the correct saved/percent/remaining values; record a new
transaction on the linked account and verify the numbers change on the next `GET` with no other
action; push the balance to or above the target and verify `achieved` becomes `true`.

### Tests for User Story 2

- [X] T029 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/GetSavingsGoalServiceImplTest.java`: `getAll()`/`getById()` return each goal's `savedAmount` matching its linked account's current balance, with correct `percentComplete`/`remainingAmount`/`achieved`; a negative account balance yields `percentComplete` floored at 0; no goals → empty list; unknown id → `NoSuchElementException`

### Implementation for User Story 2

- [X] T030 [P] [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetSavingsGoalService.java` interface (`List<SavingsGoalStatusDto> getAll()`, `SavingsGoalStatusDto getById(String id)`)
- [X] T031 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/GetSavingsGoalServiceImpl.java`: for each `SavingsGoalDto` from `GetSavingsGoalPersistencePort`, resolve its account via `GetAccountService.getAccountById` and return `SavingsGoalProgress.of(goal, account.getBalance())` (depends on T014, T020, T030, T029 must fail first)
- [X] T032 [US2] Create `Application/src/main/java/at/ymeri/my/finance/controller/goal/SavingsGoalGetController.java` implementing the generated `GoalGetApi` (`listSavingsGoals`, `getSavingsGoal`), delegating to `GetSavingsGoalService`, mapping `NoSuchElementException`→404 for the single-goal lookup (depends on T031, T007)
- [X] T033 [US2] Extend `Application/src/main/java/at/ymeri/my/finance/application/mapper/SavingsGoalMapper.java` with `List<SavingsGoalStatusDto>` → `savingsGoalListResponse` (depends on T032)
- [X] T034 [P] [US2] Add `fetchSavingsGoals` function to `frontend/src/api/client.ts`
- [X] T035 [US2] Create `frontend/src/hooks/useSavingsGoals.ts` fetching the goal list, mirrors `useRecurringSeries.ts` (depends on T034)
- [X] T036 [US2] Create `frontend/src/components/SavingsGoalsPage.tsx` — goal cards showing name, saved amount, a progress bar, percent complete, remaining amount, and an achieved badge; a "+ New Goal" button opens `SavingsGoalForm` (depends on T035, T027)
- [X] T037 [US2] Add a "Goals" nav button and `'goals'` view to `frontend/src/App.tsx`, mirroring how `AccountsPage`/`BudgetingPage` are opened (depends on T036)
- [X] T038 [US2] Add cases to `SavingsGoalControllerIntegrationTest.java`: `GET /savings-goals` reflects the linked account's current balance in `savedAmount`/`percentComplete`/`remainingAmount`; a new transaction on the linked account changes the values on the next call; a balance at or above target sets `achieved` true; `GET /savings-goals/{id}` returns 404 for an unknown id (depends on T032, T033)

**Checkpoint**: Users can create a goal and see its live progress — the core MVP loop works end to end.

---

## Phase 5: User Story 3 - Get Warned About Pace (Priority: P2)

**Goal**: A goal with a target date shows whether it's on pace, behind pace, or overdue, based on
straight-line interpolation between its creation date and target date.

**Independent Test**: Create goals with a target date and varying progress (ahead, on-track,
behind, and a target date already in the past with the target unmet); verify each shows the
correct pace status. Verify a goal with no target date never shows a pace status.

### Tests for User Story 3

- [ ] T039 [P] [US3] Extend `SavingsGoalProgressTest.java`: no `targetDate` → `paceStatus` is `null`; `actualFraction >= expectedFraction` (elapsed time vs. progress) → `ON_PACE`; `actualFraction < expectedFraction` → `BEHIND_PACE`; `targetDate` already passed and not achieved → `OVERDUE`; `targetDate` already passed but `achieved` is true → `paceStatus` is `null` (achieved takes precedence, never `OVERDUE`)

### Implementation for User Story 3

- [ ] T040 [US3] Extend `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/SavingsGoalProgress.java`'s `paceStatus` computation per `data-model.md`'s pseudocode: `null` when no `targetDate` or when `achieved`; `OVERDUE` when `now` is after `targetDate`; otherwise `ON_PACE`/`BEHIND_PACE` from comparing `actualFraction` to the straight-line `expectedFraction` between `createdAt` and `targetDate` (depends on T039 must fail first)
- [ ] T041 [US3] Add cases to `SavingsGoalControllerIntegrationTest.java`: a goal with a future target date and progress at/ahead of the straight-line pace shows `ON_PACE`; one behind pace shows `BEHIND_PACE`; one with a past target date and unmet target shows `OVERDUE`; one with no target date never includes a `paceStatus` (depends on T040)

**Checkpoint**: All three P1/P2 stories are independently functional — create, view progress, and pace warnings all work end to end.

---

## Phase 6: User Story 4 - Manage a Goal (Priority: P3)

**Goal**: A user can edit an existing goal's name, target amount, or target date, and can delete a
goal without affecting its linked account.

**Independent Test**: Edit an existing goal's target amount; verify the change is reflected
immediately. Delete a goal; verify it disappears from the list while its linked account and
transaction history are untouched.

### Tests for User Story 4

- [ ] T042 [P] [US4] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/UpdateSavingsGoalServiceImplTest.java`: updates `name`/`targetAmount`/`targetDate` and returns the refreshed `SavingsGoalStatusDto`; rejects a zero/negative `targetAmount`; rejects an unknown id (`NoSuchElementException`)
- [ ] T043 [P] [US4] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/goal/DeleteSavingsGoalServiceImplTest.java`: deletes an existing goal; rejects an unknown id (`NoSuchElementException`)

### Implementation for User Story 4

- [ ] T044 [P] [US4] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/UpdateSavingsGoalService.java` and `DeleteSavingsGoalService.java` interfaces
- [ ] T045 [US4] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/UpdateSavingsGoalServiceImpl.java`: loads by id (`NoSuchElementException` if missing), validates `targetAmount > 0`, persists via `UpdateSavingsGoalPersistencePort.update` (accountId unchanged, per research.md), returns `SavingsGoalProgress.of(updated, account.getBalance())` (depends on T014, T016, T020, T044, T042 must fail first)
- [ ] T046 [US4] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/goal/DeleteSavingsGoalServiceImpl.java`: loads by id (`NoSuchElementException` if missing), then `DeleteSavingsGoalPersistencePort.delete` (depends on T014, T017, T044, T043 must fail first)
- [ ] T047 [US4] Create `Application/src/main/java/at/ymeri/my/finance/controller/goal/SavingsGoalUpdateController.java` and `SavingsGoalDeleteController.java` implementing the generated `GoalUpdateApi`/`GoalDeleteApi`, delegating to `UpdateSavingsGoalService`/`DeleteSavingsGoalService`, mapping `NoSuchElementException`→404 (depends on T045, T046, T007)
- [ ] T048 [US4] Extend `Application/src/main/java/at/ymeri/my/finance/application/mapper/SavingsGoalMapper.java` with `updateSavingsGoalRequest` → update fields (depends on T047)
- [ ] T049 [P] [US4] Add `updateSavingsGoal` and `deleteSavingsGoal` functions to `frontend/src/api/client.ts`
- [ ] T050 [US4] Add edit (reusing `SavingsGoalForm` pre-filled with the selected goal) and delete (with a confirm step, mirroring `RemoveConfirmDialog`'s pattern) actions to `frontend/src/components/SavingsGoalsPage.tsx` (depends on T049, T036)
- [ ] T051 [US4] Add cases to `SavingsGoalControllerIntegrationTest.java`: `PUT /savings-goals/{id}` updates name/targetAmount/targetDate and the change is reflected on the next `GET`; `PUT`/`DELETE` on an unknown id return 404; `DELETE /savings-goals/{id}` removes the goal while its linked account's balance and transactions are unaffected (depends on T047, T048)

**Checkpoint**: Full CRUD lifecycle works end to end — all four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full-stack verification once all stories are implemented

- [ ] T052 Run `./mvnw clean package` and verify the full build passes with all new/updated tests green
- [ ] T053 [P] Run `./mvnw -pl Domain test` and confirm `SavingsGoalProgressTest`, `AddSavingsGoalServiceImplTest`, `GetSavingsGoalServiceImplTest`, `UpdateSavingsGoalServiceImplTest`, and `DeleteSavingsGoalServiceImplTest` all pass
- [ ] T054 [P] Run `./mvnw -pl integration-tests test` and confirm all `SavingsGoalControllerIntegrationTest` cases pass
- [ ] T055 [P] Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new components/hook/types
- [ ] T056 Execute `specs/011-savings-goals/quickstart.md` end to end in the browser
- [ ] T057 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows `goalGet`, `goalAdd`, `goalUpdate`, and `goalDelete` tags matching `contracts/goal-model.yaml`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup only where it touches Application code (none here — T008–T020 have no dependency on Phase 1 and can start in parallel with it) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational (T014, T015, T020) and the existing `GetAccountService` (007) — the natural producer story, built first
- **User Story 2 (Phase 4)**: Depends on Foundational (T014, T020) and reuses `SavingsGoalForm.tsx` from US1 (T027) for its page's create action — the natural consumer of US1's data
- **User Story 3 (Phase 5)**: Depends on US2's `SavingsGoalProgress` (T020) already existing — it extends the same method rather than creating a new one, and its effect surfaces automatically through both US1's and US2's existing responses
- **User Story 4 (Phase 6)**: Depends on Foundational (T014, T016, T017, T020) and US2's `SavingsGoalsPage.tsx` (T036) to attach edit/delete actions to
- **Polish (Phase 7)**: Depends on all four user stories being complete

### User Story Dependencies

Unlike 010, this feature's stories track the spec's own priority order directly: US1 (create) →
US2 (view) → US3 (pace, a refinement of US2's view) → US4 (manage). Each is still independently
*testable* once its dependencies are met — see each phase's Independent Test.

### Parallel Opportunities

- Setup: T001–T005 in parallel; T006–T007 sequential after them
- Foundational: T008–T013 in parallel (different new files); T015–T017 in parallel with each other (all depend only on T009)
- Within each user story, `[P]`-marked test-writing and frontend-type/client tasks can run in parallel with each other, but implementation tasks touching the same file (e.g. `SavingsGoalMapper.java` (Application) across T025/T033/T048, or `SavingsGoalProgress.java` across T020/T040) are sequential

---

## Parallel Example: Foundational Phase

```text
# Launch together (different files, no dependency on each other):
T008: Create PaceStatus.java
T009: Create SavingsGoalDto.java
T011: Create SavingsGoalEntity.java
T012: Create SavingsGoalRepository.java
T013: Create SavingsGoalMapper.java (Infrastructure)

# Then, in parallel:
T010: Create SavingsGoalStatusDto.java             (needs T008, T009)
T015: Create AddSavingsGoalPersistencePort.java     (needs T009)
T016: Create UpdateSavingsGoalPersistencePort.java  (needs T009)
T017: Create DeleteSavingsGoalPersistencePort.java

# Then sequentially:
T014: Create GetSavingsGoalPersistencePort.java     (needs T009)
T018: Create SavingsGoalPostgresAdapter.java         (needs T011, T012, T013, T014, T015, T016, T017)
T019: Write SavingsGoalProgressTest.java             (needs T010)
T020: Create SavingsGoalProgress.java                (needs T010, T019 must fail first)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3 (US1): create a goal, reusable form component
4. Complete Phase 4 (US2): goals list with live progress, nav entry
5. **STOP and VALIDATE**: create a goal, record transactions against its linked account, confirm
   saved/percent/remaining update correctly and achieved flips on at target
6. Add Phase 5 (US3) — pace warnings — a refinement on top of US2's view, not a prerequisite
7. Add Phase 6 (US4) — edit/delete — lifecycle housekeeping, not required for daily use

### Incremental Delivery

1. Setup + Foundational → shared plumbing and progress math (minus pace) ready
2. US1 → goal creation works end to end (no list UI yet)
3. US2 → the goals list with live progress; MVP complete
4. US3 → pace warnings surface automatically on the same responses
5. US4 → edit/delete lifecycle
6. Polish → full build, integration, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- `SavingsGoalProgress` is built once in Foundational (T020, percent/remaining/achieved) and
  extended once in US3 (T040, pace status) — the same "shared helper built incrementally" pattern
  `RecurringMatching` used across 010's stories; because `AddSavingsGoalServiceImpl` and
  `GetSavingsGoalServiceImpl` both call this one method, US3's change surfaces through every
  existing endpoint automatically, with no controller or mapper changes needed
- Creating, updating, or deleting a goal never writes to `account`, `bill`, or `income` rows —
  consistent with this feature's Constitution Check (Principle I)
- `SavingsGoalForm.tsx` is built once in US1 and reused for both create (US1) and edit (US4) via a
  pre-filled-vs-empty initial value, the same reuse `CorrectBillForm`/`AddBillForm` already
  demonstrate elsewhere in this app
- Submit actions in `SavingsGoalForm.tsx` and the delete confirm step must disable their buttons
  during their in-flight request, the same edge case already handled by every prior form/dialog in
  this app
