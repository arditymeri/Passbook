---
description: "Task list for Recurring Transaction Detection"
---

# Tasks: Recurring Transaction Detection

**Input**: Design documents from `/specs/010-recurring-transaction-detection/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Included — the constitution's Test-First principle (VI) is marked mandatory in this
feature's plan.md, matching every prior feature. Domain unit tests are written immediately before
the service they cover; integration tests extend a new `RecurringSeriesControllerIntegrationTest`.

**Organization**: Tasks are grouped by user story. **Phase order does not match the spec's
priority numbering** — US2 ("Recognize a Recurring Series") is built before US1 ("See What's
Coming Up") because US1 has nothing to display without a detection mechanism to produce confirmed
series in the first place (US2's own "Why this priority" says exactly this). Both are P1; this is
a dependency ordering, not a priority change. See Dependencies below.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US3)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure:

- `Application/src/main/resources/swagger/recurring/` — OpenAPI specs
- `Application/src/main/java/at/ymeri/my/finance/controller/recurring/` — REST controllers
- `Application/src/main/java/at/ymeri/my/finance/application/mapper/` — Application-layer MapStruct mappers
- `Domain/src/main/java/at/ymeri/my/finance/domain/` — business logic, DTOs, ports
- `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/` — JPA entities, repositories, Postgres adapters
- `frontend/src/` — React components, hooks, API client, types
- `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/` — TestContainers tests

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wire the new OpenAPI contracts into the build so generated code exists for later phases

- [X] T001 [P] Create `Application/src/main/resources/swagger/recurring/recurring-model.yaml` (copy from `specs/010-recurring-transaction-detection/contracts/recurring-model.yaml`)
- [X] T002 [P] Create `Application/src/main/resources/swagger/recurring/recurring-get-controller.yaml` (copy from `specs/010-recurring-transaction-detection/contracts/recurring-get-controller.yaml`)
- [X] T003 [P] Create `Application/src/main/resources/swagger/recurring/recurring-detect-controller.yaml` (copy from `specs/010-recurring-transaction-detection/contracts/recurring-detect-controller.yaml`)
- [X] T004 [P] Create `Application/src/main/resources/swagger/recurring/recurring-confirm-controller.yaml` (copy from `specs/010-recurring-transaction-detection/contracts/recurring-confirm-controller.yaml`)
- [X] T005 [P] Create `Application/src/main/resources/swagger/recurring/recurring-dismiss-controller.yaml` (copy from `specs/010-recurring-transaction-detection/contracts/recurring-dismiss-controller.yaml`)
- [X] T006 Add `recurring-get`, `recurring-detect`, `recurring-confirm`, and `recurring-dismiss` `<execution>` blocks to the OpenAPI generator plugin in `Application/pom.xml`, mirroring the existing `budget-*` executions (depends on T001–T005)
- [X] T007 Run `./mvnw -pl Application generate-sources` to generate `RecurringGetApi`, `RecurringDetectApi`, `RecurringConfirmApi`, `RecurringDismissApi`, and the new model classes into `Application/target/generated-sources/` (depends on T006)

**Checkpoint**: Generated API surface exists; Application-layer controllers can now be written against it.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `RecurringSeries` entity, fully wired (read and write) — every user story needs
it from its very first task, unlike 009's `AllocationTransfer` where only later stories needed the
write side.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/TransactionType.java` (enum: `BILL`, `INCOME`)
- [X] T009 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/RecurringSeriesStatus.java` (enum: `PROPOSED`, `CONFIRMED`, `DISMISSED`)
- [X] T010 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/RecurringSeriesDto.java` (fields: `id`, `transactionType`, `groupKey`, `description`, `frequency` (existing `RecurringFrequency`), `status`, `createdAt`)
- [X] T011 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/RecurringSeriesEntity.java` (`@Entity @Table(name = "recurring_series")`; `transactionType`, `status`, and `frequency` stored as plain `String` columns, matching `BillEntity.recurringFrequency`'s convention — no `@Enumerated` needed, MapStruct converts automatically)
- [X] T012 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/RecurringSeriesRepository.java` (Spring Data `JpaRepository<RecurringSeriesEntity, UUID>` with `Optional<RecurringSeriesEntity> findByTransactionTypeAndCategoryIdAndDescription(String, String, String)`)
- [X] T013 [P] Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/mapper/RecurringSeriesMapper.java` (MapStruct, `RecurringSeriesEntity` ↔ `RecurringSeriesDto`, mirrors the existing `BillMapper`'s simplicity — implicit enum↔String conversion, no `@Named` needed)
- [X] T014 Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/recurring/GetRecurringSeriesPersistencePort.java` with `List<RecurringSeriesDto> getAll()`, `Optional<RecurringSeriesDto> findById(String id)`, `Optional<RecurringSeriesDto> findByKey(TransactionType type, String groupKey, String description)` (depends on T008, T009, T010)
- [X] T015 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/recurring/AddRecurringSeriesPersistencePort.java` with `RecurringSeriesDto add(RecurringSeriesDto series)` (depends on T010)
- [X] T016 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/recurring/UpdateRecurringSeriesStatusPersistencePort.java` with `RecurringSeriesDto updateStatus(String id, RecurringSeriesStatus status)` (depends on T009, T010)
- [X] T017 Create `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/recurring/RecurringSeriesPostgresAdapter.java` implementing `GetRecurringSeriesPersistencePort`, `AddRecurringSeriesPersistencePort`, and `UpdateRecurringSeriesStatusPersistencePort` (depends on T011, T012, T013, T014, T015, T016)

**Checkpoint**: `RecurringSeries` persistence exists end to end. User story implementation can now begin.

---

## Phase 3: User Story 2 - Recognize a Recurring Series (Priority: P1)

**Goal**: The system detects candidate recurring series from bill/income history (three matching
occurrences, or two if one is already flagged `recurring`), the user confirms or dismisses each
proposal, and a confirmed series can later be dismissed to stop tracking it.

**Independent Test**: Record three bills sharing a category, description, and similar amount one
month apart; trigger detection; verify a proposal appears; confirm it; verify dismissing a
different, incorrectly-proposed series removes it and it is never re-proposed.

### Tests for User Story 2

- [X] T018 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/RecurringMatchingTest.java`: description normalization (trim + lowercase); per-cadence date-gap tolerance (DAILY/WEEKLY/MONTHLY/YEARLY, both within and outside tolerance); amount tolerance (within/outside the larger-of-5%-or-€2.00 rule); `predictNextDate` for each cadence
- [X] T019 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/DetectRecurringSeriesServiceImplTest.java`: three matching bills propose a series; two matching bills where one has the existing `recurring` flag set also propose a series; two matching bills with neither flagged do not propose one; a category/description mismatch is never grouped; an amount outside tolerance breaks the run; a key that already has a `RecurringSeries` row (any status) is never re-proposed; income and bill candidates are never merged into the same series
- [X] T020 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/ConfirmRecurringSeriesServiceImplTest.java`: confirms a `PROPOSED` series; rejects confirming a `CONFIRMED` or `DISMISSED` series; rejects confirming an unknown id
- [X] T021 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/DismissRecurringSeriesServiceImplTest.java`: dismisses a `PROPOSED` series; dismisses a `CONFIRMED` series; rejects dismissing an already-`DISMISSED` series; rejects dismissing an unknown id

### Implementation for User Story 2

- [X] T022 [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/RecurringMatching.java`: `normalizeDescription(String)`, `isWithinCadenceTolerance(RecurringFrequency, Duration gap)`, `isWithinAmountTolerance(BigDecimal prior, BigDecimal candidate)`, `predictNextDate(OffsetDateTime last, RecurringFrequency)` (depends on T018 must fail first)
- [X] T023 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/DetectRecurringSeriesServiceImpl.java`: read `GetBillService.getAll()` and `GetIncomeService.getAll()`, group by `(transactionType, groupKey, RecurringMatching.normalizeDescription(description))` — `groupKey` is a bill's `categoryId` or an income's `source.name()` (income has no category; see research.md) skipping blank descriptions, find the most recent run of 3 (or 2, if any member has `recurring=true`) consecutive occurrences satisfying `RecurringMatching`'s cadence and amount tolerance, skip any key already covered by `GetRecurringSeriesPersistencePort.findByKey`, persist new `PROPOSED` rows via `AddRecurringSeriesPersistencePort`, return `GetRecurringSeriesPersistencePort.getAll()` (depends on T014, T015, T022, T019 must fail first)
- [X] T024 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/ConfirmRecurringSeriesServiceImpl.java`: load by id (`NoSuchElementException` if missing), reject via `IllegalStateException` unless status is `PROPOSED`, else `UpdateRecurringSeriesStatusPersistencePort.updateStatus(id, CONFIRMED)` (depends on T014, T016, T020 must fail first)
- [X] T025 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/DismissRecurringSeriesServiceImpl.java`: load by id (`NoSuchElementException` if missing), reject via `IllegalStateException` if already `DISMISSED`, else `UpdateRecurringSeriesStatusPersistencePort.updateStatus(id, DISMISSED)` (depends on T014, T016, T021 must fail first)
- [X] T026 [P] [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/DetectRecurringSeriesService.java`, `ConfirmRecurringSeriesService.java`, `DismissRecurringSeriesService.java`, and `GetRecurringSeriesService.java` interfaces
- [X] T027 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/GetRecurringSeriesServiceImpl.java` (thin wrapper over `GetRecurringSeriesPersistencePort.getAll()`) (depends on T014, T026)
- [X] T028 [US2] Create `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringDetectController.java` implementing the generated `RecurringDetectApi`, delegating to `DetectRecurringSeriesService` (depends on T023, T007)
- [X] T029 [US2] Create `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringConfirmController.java` implementing the generated `RecurringConfirmApi`, delegating to `ConfirmRecurringSeriesService`, mapping `NoSuchElementException`→404 and `IllegalStateException`→400 (depends on T024, T007)
- [X] T030 [US2] Create `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringDismissController.java` implementing the generated `RecurringDismissApi`, delegating to `DismissRecurringSeriesService`, mapping `NoSuchElementException`→404 and `IllegalStateException`→400 (depends on T025, T007)
- [X] T031 [US2] Create `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringGetController.java` implementing the `listRecurringSeries` operation of the generated `RecurringGetApi`, delegating to `GetRecurringSeriesService` (`getRecurringDashboard` is left to the interface's default 501 response until US1 overrides it) (depends on T027, T007)
- [X] T032 [US2] Create `Application/src/main/java/at/ymeri/my/finance/application/mapper/RecurringSeriesMapper.java` (MapStruct, Domain `RecurringSeriesDto` ↔ generated `recurringSeriesResponse`) (depends on T028, T029, T030, T031)
- [X] T033 [P] [US2] Add `RecurringSeries`, `TransactionType`, `RecurringSeriesStatus` types and `fetchRecurringSeries`, `detectRecurringSeries`, `confirmRecurringSeries`, `dismissRecurringSeries` functions to `frontend/src/types/index.ts` and `frontend/src/api/client.ts`
- [X] T034 [US2] Create `frontend/src/hooks/useRecurringSeries.ts` fetching the series list, mirrors `useBudgetAllocations.ts` (depends on T033)
- [X] T035 [US2] Create `frontend/src/components/RecurringSeriesProposals.tsx` — on open, calls `detectRecurringSeries`, then lists `PROPOSED` series with confirm/dismiss actions and `CONFIRMED` series with a "stop tracking" (dismiss) action (depends on T034)
- [X] T036 [US2] Add a way to open `RecurringSeriesProposals` from `frontend/src/App.tsx` (a nav button, mirroring how `AccountsPage`/`BudgetingPage` are opened) (depends on T035)
- [X] T037 [US2] Add cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/RecurringSeriesControllerIntegrationTest.java` (new file): `POST /recurring-series/detect` proposes a series from three matching bills; a second `detect` call does not duplicate it; `POST /recurring-series/{id}/confirm` transitions `PROPOSED`→`CONFIRMED` and rejects a non-`PROPOSED` id with 400; `POST /recurring-series/{id}/dismiss` transitions from both `PROPOSED` and `CONFIRMED` to `DISMISSED`, and rejects an already-`DISMISSED` id with 400 (depends on T028, T029, T030, T031, T032)

**Checkpoint**: Users can detect, confirm, and dismiss recurring series entirely from the app.

---

## Phase 4: User Story 1 - See What's Coming Up (Priority: P1)

**Goal**: Confirmed recurring series produce a predicted next date and amount, shown in an
Upcoming view; a passed prediction with no matching new transaction is marked overdue instead of
disappearing; recording a matching occurrence advances the prediction.

**Independent Test**: With a confirmed series whose last occurrence was one cadence-interval ago,
open the dashboard and verify it appears in Upcoming with the correct predicted date/amount;
advance past that date without recording anything and verify it shows overdue; record a matching
occurrence and verify the prediction advances.

### Tests for User Story 1

- [X] T038 [P] [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/GetUpcomingRecurringServiceImplTest.java`: a confirmed series with a recent occurrence predicts the correct next date/amount; a predicted date in the past with no matching new transaction is marked overdue; recording a new matching transaction advances the prediction and clears overdue; `PROPOSED` and `DISMISSED` series never produce upcoming items; no confirmed series → empty list

### Implementation for User Story 1

- [X] T039 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/UpcomingRecurringItemDto.java`, `PriceChangeAlertDto.java`, and `RecurringDashboardResult.java` (`{ List<UpcomingRecurringItemDto> upcoming; List<PriceChangeAlertDto> recentPriceChanges; }`)
- [X] T040 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetUpcomingRecurringService.java` interface (`RecurringDashboardResult getDashboard()`)
- [X] T041 [US1] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/GetUpcomingRecurringServiceImpl.java`: for every `CONFIRMED` series, match members via `GetBillService.getAll()`/`GetIncomeService.getAll()` filtered by `(groupKey, normalizedDescription)`, sort by time, derive `predictedDate`/`predictedAmount`/`overdue` via `RecurringMatching.predictNextDate`; `recentPriceChanges` returns empty for now (implemented in US3) (depends on T014, T022, T039, T040, T038 must fail first)
- [X] T042 [US1] Update `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringGetController.java` to override `getRecurringDashboard`, delegating to `GetUpcomingRecurringService` (depends on T041)
- [X] T043 [US1] Extend `Application/src/main/java/at/ymeri/my/finance/application/mapper/RecurringSeriesMapper.java` with `UpcomingRecurringItemDto` → `upcomingRecurringItem` and `RecurringDashboardResult` → `recurringDashboardResponse` mappings (depends on T042)
- [X] T044 [P] [US1] Add `UpcomingRecurringItem`, `PriceChangeAlert`, `RecurringDashboard` types and `fetchRecurringDashboard` to `frontend/src/types/index.ts` and `frontend/src/api/client.ts`
- [X] T045 [US1] Create `frontend/src/components/UpcomingRecurring.tsx` — dashboard section listing upcoming items with predicted date/amount, overdue items visually distinguished (e.g. colour-coded, mirrors the negative-balance treatment from feature 007) (depends on T044)
- [X] T046 [US1] Mount `UpcomingRecurring` on the dashboard in `frontend/src/App.tsx`, alongside `CategorySpend`/`BudgetStatus` (depends on T045)
- [X] T047 [US1] Add cases to `RecurringSeriesControllerIntegrationTest.java`: `GET /recurring-series/dashboard` returns a correct predicted date/amount for a confirmed series; a passed prediction with no new occurrence is marked overdue; recording a new matching bill advances the prediction on the next call (depends on T042, T043)

**Checkpoint**: The Upcoming view is fully functional — combined with US2, users can detect, confirm, and see what's coming.

---

## Phase 5: User Story 3 - Get Warned About a Price Change (Priority: P2)

**Goal**: A confirmed series whose two most recent occurrences differ in amount by more than
tolerance produces a price-change alert.

**Independent Test**: With a confirmed series' last two occurrences at different amounts beyond
tolerance, verify an alert appears showing the prior amount, new amount, and delta; verify no
alert appears when the difference is within tolerance.

### Tests for User Story 3

- [X] T048 [P] [US3] Add cases to `GetUpcomingRecurringServiceImplTest.java`: the two most recent occurrences differing by more than tolerance produce a `PriceChangeAlertDto` with the correct prior amount, new amount, and delta; a difference within tolerance produces no alert; a series with fewer than two occurrences produces no alert; a `PROPOSED` or `DISMISSED` series never produces one

### Implementation for User Story 3

- [X] T049 [US3] Extend `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/GetUpcomingRecurringServiceImpl.java` to populate `recentPriceChanges`: for each confirmed series with ≥2 matched members, compare the two most recent via `RecurringMatching.isWithinAmountTolerance`, emitting a `PriceChangeAlertDto` when it fails (depends on T022, T048 must fail first)
- [X] T050 [US3] Verify the existing `RecurringSeriesMapper` (Application) mapping from T043 already carries `recentPriceChanges` through to `recurringDashboardResponse` (MapStruct auto-mapping check, same verification done for `envelopeBalance` in feature 009); add an explicit `@Mapping` only if the generated mapper impl omits it (depends on T049)
- [X] T051 [P] [US3] Create `frontend/src/components/PriceChangeAlerts.tsx` — small list showing each alert's description, prior amount, new amount, and delta (colour-coded by increase/decrease) (depends on T044)
- [X] T052 [US3] Mount `PriceChangeAlerts` alongside `UpcomingRecurring` on the dashboard in `frontend/src/App.tsx` (depends on T051, T046)
- [X] T053 [US3] Add cases to `RecurringSeriesControllerIntegrationTest.java`: a new occurrence differing from a confirmed series' prior amount by more than tolerance appears in `GET /recurring-series/dashboard`'s `recentPriceChanges`; an occurrence within tolerance does not (depends on T049, T050)

**Checkpoint**: All three user stories are independently functional — the full recurring-detection loop works end to end.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full-stack verification once all stories are implemented

- [ ] T054 Run `./mvnw clean package` and verify the full build passes with all new/updated tests green
- [ ] T055 [P] Run `./mvnw -pl Domain test` and confirm `RecurringMatchingTest`, `DetectRecurringSeriesServiceImplTest`, `ConfirmRecurringSeriesServiceImplTest`, `DismissRecurringSeriesServiceImplTest`, and `GetUpcomingRecurringServiceImplTest` all pass
- [ ] T056 [P] Run `./mvnw -pl integration-tests test` and confirm all `RecurringSeriesControllerIntegrationTest` cases pass
- [ ] T057 [P] Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new components/hook/types
- [ ] T058 Execute `specs/010-recurring-transaction-detection/quickstart.md` end to end in the browser
- [ ] T059 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows `recurringGet`, `recurringDetect`, `recurringConfirm`, and `recurringDismiss` tags matching `contracts/recurring-model.yaml`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup only where it touches Application code (T007); T008–T017 have no dependency on Phase 1 and can start in parallel with it — BLOCKS all user stories
- **User Story 2 (Phase 3)**: Depends on Foundational (T014–T017) — this is the *first* story phase, ahead of US1, because US1 has no data without it (see note at the top of this file)
- **User Story 1 (Phase 4)**: Depends on Foundational and on US2's `RecurringMatching` (T022) and confirmed-series lifecycle (T024) existing — there is nothing to predict for without a way to reach `CONFIRMED`
- **User Story 3 (Phase 5)**: Depends on US1's `GetUpcomingRecurringServiceImpl` (T041) and dashboard endpoint (T042) already existing — it extends the same file and response rather than creating a new one
- **Polish (Phase 6)**: Depends on all three user stories being complete

### User Story Dependencies

Unlike a feature where every story is independent, this feature's stories are intentionally
sequential: US2 → US1 → US3, tracking real data dependency rather than the spec's priority
numbers (US2 and US1 are both P1; US3 is P2). Each is still independently *testable* once its
dependencies are met — see each phase's Independent Test.

### Parallel Opportunities

- Setup: T001–T005 in parallel; T006–T007 sequential after them
- Foundational: T008–T013 in parallel (different new files); T015–T016 in parallel with each other (both depend only on T008–T010)
- Within each user story, `[P]`-marked test-writing and frontend-type/client tasks can run in parallel with each other, but implementation tasks touching the same file (e.g. `RecurringGetController.java` across T031/T042, or `GetUpcomingRecurringServiceImpl.java` across T041/T049) are sequential

---

## Parallel Example: Foundational Phase

```text
# Launch together (different files, no dependency on each other):
T008: Create TransactionType.java
T009: Create RecurringSeriesStatus.java
T010: Create RecurringSeriesDto.java
T011: Create RecurringSeriesEntity.java
T012: Create RecurringSeriesRepository.java
T013: Create RecurringSeriesMapper.java (Infrastructure)

# Then, in parallel:
T015: Create AddRecurringSeriesPersistencePort.java   (needs T010)
T016: Create UpdateRecurringSeriesStatusPersistencePort.java (needs T009, T010)

# Then sequentially:
T014: Create GetRecurringSeriesPersistencePort.java    (needs T008, T009, T010)
T017: Create RecurringSeriesPostgresAdapter.java        (needs T011, T012, T013, T014, T015, T016)
```

---

## Implementation Strategy

### MVP First (US2 + US1 — both P1)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3 (US2): detection, confirm/dismiss, proposals UI
4. Complete Phase 4 (US1): predicted dates/amounts, overdue flagging, Upcoming dashboard section
5. **STOP and VALIDATE**: record a genuinely recurring bill three times, confirm the proposal,
   verify it shows correctly in Upcoming, verify a missed date shows overdue
6. Add Phase 5 (US3) — price-change alerts — when ready; it's a refinement on top, not a
   prerequisite for daily use

### Incremental Delivery

1. Setup + Foundational → shared plumbing ready
2. US2 → detection and confirm/dismiss work end to end (nothing visible on the dashboard yet)
3. US1 → the Upcoming view; MVP complete
4. US3 → price-change alerts
5. Polish → full build, integration, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- `RecurringMatching` is built once in US2 (T022) and reused by US1 (T041, for `predictNextDate`)
  and US3 (T049, for `isWithinAmountTolerance`) — the same "shared helper built incrementally"
  pattern `EnvelopeBalances` used across 009's stories
- `GetUpcomingRecurringServiceImpl` and `RecurringGetController`'s dashboard endpoint are
  deliberately built in two passes (US1 then US3) rather than all at once, so US1 stays
  independently testable with `recentPriceChanges` simply empty until US3 lands
- Detection (`DetectRecurringSeriesServiceImpl`) never mutates a bill or income row — it only
  reads through `GetBillService`/`GetIncomeService` and writes new `RecurringSeries` rows,
  consistent with this feature's Constitution Check (Principle I)
- Submit/confirm/dismiss actions in `RecurringSeriesProposals.tsx` must disable their buttons
  during their in-flight request, the same edge case already handled by every prior form/dialog
  in this app
