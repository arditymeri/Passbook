---

description: "Task list for the cash flow forecast feature"
---

# Tasks: Cash Flow Forecast

**Input**: Design documents from `/specs/015-cash-flow-forecast/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included and REQUIRED for the new Domain logic — Constitution Principle VI (Test-First,
NON-NEGOTIABLE) mandates unit-test coverage for all new financial calculation / business-rule
logic in the Domain module. No frontend test tasks are included: this repo has no frontend test
runner anywhere (a confirmed pre-existing gap across every prior feature), and this feature does
not introduce one.

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing of each story. Because "warn if at risk" (US1) and "show the day-by-day timeline" (US2)
both read the *same* backend projection, the prediction/projection engine itself is Foundational
(shared, blocking) — the user-story phases below are purely frontend increments over that one
backend response, mirroring how `NetWorthCard` (014) was built incrementally story-by-story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths are included in every task

## Path Conventions

Existing hexagonal Maven multi-module backend + Vite/React frontend (see plan.md Project
Structure). Domain API interfaces are flat under `domain/api/` (e.g. `GetUpcomingRecurringService`
lives directly there, not `domain/api/recurring/`); Domain DTOs are namespaced by feature under
`domain/data/<feature>/`; Application controllers live under
`at.ymeri.my.finance.controller.<feature>` and MapStruct mappers flat under
`at.ymeri.my.finance.application.mapper` — confirmed against the existing `recurring` feature's
files before writing the tasks below.

---

## Phase 1: Setup (OpenAPI contract, spec-first per Constitution Principle VII)

- [ ] T001 [P] Create `Application/src/main/resources/swagger/forecast/forecast-get-controller.yaml`
  and `Application/src/main/resources/swagger/forecast/forecast-model.yaml`, adapting
  `specs/015-cash-flow-forecast/contracts/forecast-api.yaml` /
  `contracts/forecast-model.yaml` to this repo's existing swagger conventions (compare
  `Application/src/main/resources/swagger/recurring/recurring-get-controller.yaml` /
  `recurring-model.yaml` for style: `servers` block, `tags: [forecastGet]`, `$ref` syntax).
- [ ] T002 [P] Register a new `forecast-get` code-gen execution in `Application/pom.xml`,
  modeled exactly on the existing `recurring-get` `<execution>` block (id `recurring-get`,
  currently around line 539): add `<id>forecast-get</id>`, `<inputSpec>` pointing at
  `swagger/forecast/forecast-get-controller.yaml`, `<apiPackage>${api-package}.forecast</apiPackage>`,
  `<modelPackage>${model-package}</modelPackage>`, and the same `configOptions`
  (`delegatePattern=true`, `interfaceOnly=true`, `useSpringBoot3=true`, `skipOverwrite=true`, etc.)
  as every other execution in that file.
- [ ] T003 Run `./mvnw -pl Application generate-sources` (depends on T001, T002) and confirm the
  generated `ForecastGetApi` delegate interface and API models (`CashFlowForecastResponse`,
  `AccountForecast`, `ForecastEntry`) appear under
  `Application/target/generated-sources/openapi/src/main/java/at/ymeri/my/finance/application/controller/forecast/`
  and `.../application/data/`.

---

## Phase 2: Foundational (blocking prerequisites — the forecast engine itself)

**⚠️ CRITICAL**: Both User Story 1 (warning) and User Story 2 (timeline) read the same backend
projection — neither can be implemented until this phase is complete.

- [ ] T004 [P] Extract `GetUpcomingRecurringServiceImpl.membersOf(RecurringSeriesDto)`
  (currently package-private, in
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/GetUpcomingRecurringServiceImpl.java`)
  into a new shared
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/RecurringSeriesMembers.java`,
  keeping the identical groupKey + normalized-description matching against
  `GetBillService`/`GetIncomeService.getAll()`. Add an `accountId` field to the `MemberOccurrence`
  record (from `BillDto.getAccountId()` / `IncomeDto.getAccountId()`, both already present).
  Update `GetUpcomingRecurringServiceImpl` to delegate to the extracted component instead of its
  own private method; confirm `Domain/src/test/java/.../service/recurring/GetUpcomingRecurringServiceImplTest.java`
  still passes unmodified.
- [ ] T005 [P] Add
  `predictOccurrencesWithinWindow(OffsetDateTime asOf, OffsetDateTime latestOccurrence, boolean overdue, RecurringFrequency frequency, OffsetDateTime windowEnd)`
  to `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/RecurringMatching.java`.
  Starting point is `asOf` when `overdue` is true (spec Edge Case: an overdue series is due "now"),
  otherwise `latestOccurrence`; repeatedly call the existing `predictNextDate` on the previous
  result, collecting every date up to and including the last one `<= windowEnd`, stopping once a
  predicted date exceeds `windowEnd`. Returns `List<OffsetDateTime>` in chronological order (may
  be empty).
- [ ] T006 [US-shared] Extend
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/RecurringMatchingTest.java`
  (depends on T005) with unit tests for `predictOccurrencesWithinWindow`: (a) a MONTHLY series
  with only one occurrence inside a 4-week window, (b) a WEEKLY series with multiple occurrences
  inside a 4-week window (asserts every one is returned, in order — SC-002), (c) no occurrences
  when the next predicted date already exceeds the window, (d) an overdue series' first entry is
  `asOf`, not the stale `latestOccurrence`-derived date.
- [ ] T007 [P] Create Domain DTOs per data-model.md:
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/forecast/ForecastEntryDto.java`,
  `AccountForecastDto.java`, and `CashFlowForecastResult.java` (wrapping `List<AccountForecastDto>
  accounts`). All amount fields `BigDecimal` (Constitution Principle IV); `timeline` field ordered
  `List<ForecastEntryDto>`.
- [ ] T008 Create port interface
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetCashFlowForecastService.java` (depends
  on T007) with a single method `CashFlowForecastResult forecast(int windowWeeks)`.
- [ ] T009 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/forecast/GetCashFlowForecastServiceImpl.java`
  (depends on T004, T005, T007, T008; `@Service`, constructor-injects `GetAccountService`,
  `GetRecurringSeriesService`, `GetBillService`, `GetIncomeService`). For each account from
  `GetAccountService.getAll()` (already balance-derived): for each `CONFIRMED` series from
  `GetRecurringSeriesService.getAll()` whose latest member (via T004's `RecurringSeriesMembers`,
  sorted oldest-first) has this account's id, compute `windowEnd = now.plusWeeks(windowWeeks)`,
  call T005's `predictOccurrencesWithinWindow` (using the series' `latestOccurrence.time()` and
  whether `RecurringMatching.predictNextDate(latest.time(), frequency)` is already before `now`
  for `overdue`), and for each predicted date emit a `ForecastEntryDto` carrying the series'
  `latestOccurrence.amount()` (carry-forward rule, same as `GetUpcomingRecurringServiceImpl` —
  no averaging). Merge every account's entries across all its series, sort by `date`, walk them
  in order accumulating `projectedBalance` from `currentBalance` (BILL subtracts, INCOME adds),
  and set `atRisk = currentBalance.signum() < 0 || timeline.stream().anyMatch(e ->
  e.getProjectedBalance().signum() < 0)`. An account with no confirmed series attributed to it
  gets an empty `timeline` and `atRisk` reflecting only `currentBalance` (FR-006).
- [ ] T010 [US-shared] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/forecast/GetCashFlowForecastServiceImplTest.java`
  (depends on T009), with fakes/mocks for the four injected ports, covering: confirmed bills
  driving an account negative within the window → `atRisk=true` (US1.1); confirmed income+bills
  keeping it positive → `atRisk=false` (US1.2); an already-negative `currentBalance` with no
  series → `atRisk=true` from the start (US1.3); a WEEKLY series recurring more than once in the
  window → every occurrence present in `timeline`, in date order (US2.1/US2.2); a corrected bill
  (simulate `GetBillService.getAll()` returning the corrected amount, not the original) → the
  forecast entries use the corrected amount (US2.3, FR-007); an overdue series → its first
  timeline entry lands at "now", not its stale predicted date; two series both due the same date →
  both entries present and both reflected in that date's balance change; an account with zero
  confirmed series → flat forecast (`timeline` empty), no false warning (FR-006, SC-004).
- [ ] T011 Implement `Application/src/main/java/at/ymeri/my/finance/controller/forecast/CashFlowForecastController.java`
  implementing the generated `ForecastGetApi` delegate (depends on T003, T009), and
  `Application/src/main/java/at/ymeri/my/finance/application/mapper/CashFlowForecastMapper.java`
  (MapStruct interface, `@Mapper` + `INSTANCE` static field, mirroring
  `Application/src/main/java/at/ymeri/my/finance/application/mapper/RecurringSeriesMapper.java`)
  mapping `CashFlowForecastResult` → the generated `CashFlowForecastResponse`. Controller
  validates the `weeks` query parameter against `{2, 4, 8, 12}` (default `4` when omitted;
  `400` when present but not one of these values) before calling
  `getCashFlowForecastService.forecast(weeks)`.

**Checkpoint**: `GET /api/v1/cash-flow-forecast[?weeks=N]` is fully functional end-to-end and
returns correct per-account forecasts. Both remaining user-story phases are frontend-only from
here.

---

## Phase 3: User Story 1 - Get Warned About an Upcoming Low Balance (Priority: P1) 🎯 MVP

**Goal**: A user sees, per account, a clear warning when it is (or will become) negative within
the default forecast window.

**Independent Test**: Set up an account whose confirmed bills would drive it negative before the
window ends; view the forecast; that account is flagged. An account whose income covers its bills
shows no warning. An already-negative account is flagged immediately.

- [ ] T012 [P] [US1] Add frontend types to `frontend/src/types/index.ts`: `CashFlowWindowWeeks`
  (`2 | 4 | 8 | 12`), `ForecastEntry`, `AccountForecast`, `CashFlowForecastResponse` — mirroring
  the `forecast-model.yaml` schemas field-for-field (same pattern as the existing `RecurringSeries`
  / `UpcomingRecurringItem` types added for feature 010).
- [ ] T013 [P] [US1] Add `fetchCashFlowForecast(weeks?: CashFlowWindowWeeks)` to
  `frontend/src/api/client.ts`, calling `GET /api/v1/cash-flow-forecast` with an optional `weeks`
  query param, returning `Promise<CashFlowForecastResponse>`.
- [ ] T014 [US1] Create `frontend/src/components/CashFlowForecastCard.tsx` (depends on T012,
  T013): on mount, call `fetchCashFlowForecast()` (default window); render one row per account
  (name, current balance, a clearly distinct warning chip/color — e.g. MUI `Chip`
  color="error" — shown only when `atRisk` is true). No per-entry timeline yet (US2).
- [ ] T015 [US1] Mount `<CashFlowForecastCard />` in `frontend/src/App.tsx`, near
  `<NetWorthCard />` (both summary-style cards near the top of the dashboard).

**Checkpoint**: User Story 1 is independently testable per its Independent Test above.

---

## Phase 4: User Story 2 - See the Day-by-Day Forecast (Priority: P1)

**Goal**: Beyond the yes/no warning, show the actual per-occurrence timeline driving the
projection for each account.

**Independent Test**: View an account's forecast; see one entry per confirmed recurring
bill/income occurrence expected within the window, each with its predicted date and amount, with
the projected running balance visibly changing at each one; a series recurring more than once in
the window shows every occurrence; correcting a past transaction updates the shown projection on
reload.

- [ ] T016 [US2] Extend `frontend/src/components/CashFlowForecastCard.tsx` (depends on T014) to
  render each account's `timeline` array in date order under its summary row (date, description,
  signed amount, resulting `projectedBalance`) — e.g. a nested MUI `List`/`Collapse`, consistent
  with the app's existing list-style rendering (`RecentTransactions.tsx`, `UpcomingRecurring.tsx`).

**Checkpoint**: User Stories 1 AND 2 both independently functional — the backend's
correction-aware reads (T009/T010) already guarantee a corrected transaction updates the shown
projection with no additional frontend work.

---

## Phase 5: User Story 3 - Adjust the Forecast Window (Priority: P2)

**Goal**: Let the user pick how far ahead the forecast looks.

**Independent Test**: Select a shorter window; forecast and any warning update to that nearer-term
period. Select a longer window; forecast extends further out, potentially revealing a risk not
visible in the shorter window.

- [ ] T017 [US3] Extend `frontend/src/components/CashFlowForecastCard.tsx` (depends on T016) with
  a window selector — MUI `ToggleButtonGroup` with options 2/4/8/12 weeks, default 4, mirroring
  `frontend/src/components/NetWorthCard.tsx`'s existing range-selector pattern — that re-calls
  `fetchCashFlowForecast(weeks)` and re-renders the whole card (summary rows + timelines) on
  change.

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Run `./mvnw -pl Domain test` and confirm every new/updated test (T006, T010) plus
  the pre-existing `GetUpcomingRecurringServiceImplTest` (unaffected by the T004 refactor) pass.
- [ ] T019 [P] Run `cd frontend && npx tsc --noEmit` to typecheck the new frontend code (this repo
  has no frontend test runner anywhere — a pre-existing gap this feature does not introduce or
  worsen).
- [ ] T020 Execute `specs/015-cash-flow-forecast/quickstart.md`'s 7 manual scenarios end-to-end
  against a running stack. BLOCKED in this development sandbox (no Docker daemon available,
  consistent with every prior feature 007-014) — must be run manually once implementation lands
  in an environment with Docker; report honestly rather than marking complete if not actually run.
- [ ] T021 Mark all tasks in this file `[X]`, then commit and push the implementation to
  `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T003 needs the generated API from T001/T002) —
  BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational completion. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational completion, and extends the same component
  T014 created — start after T014, not merely after Phase 2, since it edits the same file.
- **User Story 3 (Phase 5)**: Depends on Foundational completion, and extends the component T016
  produced — start after T016 for the same reason.
- **Polish (Phase 6)**: Depends on every phase above being complete.

### Within Each Phase

- T001/T002 (different files) can run in parallel; T003 needs both.
- T004/T005/T007 (different files, no cross-dependency) can run in parallel; T006 needs T005;
  T008 needs T007; T009 needs T004+T005+T007+T008; T010 needs T009; T011 needs T003+T009.
- T012/T013 (different files) can run in parallel; T014 needs both; T015 needs T014.
- T016 is a single-file edit, strictly after T014.
- T017 is a single-file edit, strictly after T016.

### Parallel Opportunities

- Phase 1: T001 ∥ T002.
- Phase 2: T004 ∥ T005 ∥ T007.
- Phase 3: T012 ∥ T013.
- Phase 6: T018 ∥ T019.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Launch independent foundational tasks together:
Task: "Extract RecurringSeriesMembers + accountId in Domain/.../service/recurring/RecurringSeriesMembers.java"
Task: "Add predictOccurrencesWithinWindow to Domain/.../service/recurring/RecurringMatching.java"
Task: "Create ForecastEntryDto/AccountForecastDto/CashFlowForecastResult in Domain/.../data/forecast/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (OpenAPI contract + codegen wiring).
2. Complete Phase 2: Foundational (the entire prediction/projection engine — CRITICAL, blocks
   both stories; this is where nearly all the new logic and required tests live).
3. Complete Phase 3: User Story 1 (warning chips per account).
4. **STOP and VALIDATE**: Run Domain tests (T018) and confirm US1's Independent Test manually.
5. This is a deployable MVP — a user already gets the feature's core value (a proactive warning)
   even before the timeline (US2) or window selector (US3) exist.

### Incremental Delivery

1. Setup + Foundational → backend forecast engine ready and tested.
2. Add User Story 1 → warning chips → deploy/demo (MVP).
3. Add User Story 2 → day-by-day timeline → deploy/demo.
4. Add User Story 3 → window selector → deploy/demo.
5. Polish (T018-T021).

---

## Notes

- [P] tasks touch different files with no dependency on an incomplete task.
- [Story] labels map each user-story-phase task to spec.md's US1/US2/US3 for traceability.
- Tests are included per Constitution Principle VI (NON-NEGOTIABLE for Domain financial logic) —
  T006 and T010 must be written test-concurrently with T005 and T009 respectively, and must pass
  before Phase 2 is considered complete.
- Commit after each phase checkpoint, matching this project's established pattern across features
  012-014.
- T020 (quickstart.md walkthrough) is expected to be BLOCKED in this sandbox — report that
  honestly rather than marking it complete without having actually run it, consistent with every
  prior feature.
