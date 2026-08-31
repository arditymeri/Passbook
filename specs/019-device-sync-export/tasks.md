---

description: "Task list for the device sync via file export feature"
---

# Tasks: Device Sync via File Export

**Input**: Design documents from `/specs/019-device-sync-export/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included and REQUIRED for the new Domain logic — Constitution Principle VI (Test-First,
NON-NEGOTIABLE) mandates unit-test coverage for all new financial calculation / business-rule
logic in the Domain module, and this feature's merge engine is the most test-critical logic in the
project to date. No frontend test tasks are included: this repo has no frontend test runner
anywhere (a confirmed pre-existing gap across every prior feature), and this feature does not
introduce one.

**Organization**: This feature does not decompose as cleanly into independent story-sized slices
as prior features did, and that's worth being upfront about rather than forcing a false
appearance of independence: the merge engine's correctness (natural-key fallback matching,
last-modified-wins, the correction tie-breaker, never-deletes) is not something that can be
"simple for US2 and smarter for US3" — even User Story 2's trivial fresh-device bootstrap runs
through the exact same `ComputeMergePlanService`/`ApplyMergePlanService` that User Story 3's
real conflicts do. So the entire merge engine is Foundational (blocking, shared), fully correct
and fully tested before any user story begins, and the four user-story phases below are almost
entirely about the frontend experience layered on top of it — User Story 3 in particular adds
only one small task (surfacing a count the engine already computes), because its actual
correctness guarantee was already proven by the Foundational phase's own tests.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths are included in every task

## Path Conventions

Existing hexagonal Maven multi-module backend + Vite/React frontend (see plan.md Project
Structure). Confirmed against the real codebase before writing the tasks below: Domain API
interfaces flat under `domain/api/`; SPI ports under `domain/spi/<feature>/`; DTOs under
`domain/data/<feature>/`; Infrastructure adapters under
`infrastructure/adapter/postgres/<feature>/`; Application controllers at
`at.ymeri.my.finance.controller.<feature>.XxxController` (implementing a generated delegate at
`at.ymeri.my.finance.application.controller.<feature>.XxxApi` — different package, confirmed
against `RecurringGetController`/`BillNecessityTagController`); Application mappers flat under
`at.ymeri.my.finance.application.mapper.*`. `Application/target/generated-sources/` accumulates
stale `skipOverwrite=true`-protected files across builds — T005 must `clean` first (the same
gotcha feature 018 hit).

---

## Phase 1: Setup (OpenAPI contracts, spec-first per Constitution Principle VII)

- [X] T001 [P] Create `Application/src/main/resources/swagger/sync/sync-export-controller.yaml`
  (`GET /sync/export`) and `Application/src/main/resources/swagger/sync/sync-model.yaml`
  (`syncSnapshot` plus its seven entity-list sub-schemas and `importSummary`/
  `importSummaryCounts`), adapting `specs/019-device-sync-export/contracts/sync-api.yaml` /
  `contracts/sync-model.yaml` to this repo's real swagger conventions (compare
  `recurring-get-controller.yaml` for style). Every amount field is a decimal string, matching
  `correctBillRequest`'s existing convention (Constitution Principle IV).
- [X] T002 [P] Create `Application/src/main/resources/swagger/sync/sync-import-controller.yaml`
  with both `POST /sync/import/preview` and `POST /sync/import/apply` operations (one controller
  file, one generated delegate with two methods — both take a `syncSnapshot` body and return an
  `importSummary`), referencing `sync-model.yaml`.
- [X] T003 [P] Add additive fields to the existing model files, each a small, non-breaking
  extension of an already-shipped schema: `updatedAt` on `account` (`account-model.yaml`),
  `category` (`category-model.yaml`), `budget` (`budget-model.yaml`), and
  `recurringSeriesResponse` (`recurring-model.yaml`); `updatedAt` on the goal schema in
  `goal-model.yaml`; `recordedAt` on `bill` (`bill-model.yaml`, alongside the `necessityTag` field
  feature 018 already added) and on `income` (`income-model.yaml`); `necessityTagUpdatedAt`
  (nullable) on `bill`.
- [X] T004 [P] Register two new codegen executions in `Application/pom.xml`, modeled on the
  existing `bill-necessity-tag`/`recurring-cost-summary` executions (same `configOptions` as
  every other execution in that file): `<id>sync-export</id>` (`apiPackage`
  `${api-package}.sync`, input `swagger/sync/sync-export-controller.yaml`) and
  `<id>sync-import</id>` (same `apiPackage`, input `swagger/sync/sync-import-controller.yaml`).
- [X] T005 Run `./mvnw -pl Application clean generate-sources` (depends on T001-T004) — **must**
  use `clean` so the already-stale `skipOverwrite=true`-protected generated `Bill`/`Account`/etc.
  models are regenerated with their new fields rather than silently kept without them. Confirm
  `SyncExportApi`, `SyncImportApi`, `SyncSnapshot`, `ImportSummary`, and the per-entity sync
  models all appear under
  `Application/target/generated-sources/openapi/src/main/java/at/ymeri/my/finance/application/`.

---

## Phase 2: Foundational (blocking prerequisites — the entire merge engine)

**⚠️ CRITICAL**: Every user story below reads or writes through the export/import engine built
here — none of them can be implemented, let alone correctly, until this phase is complete and
its own tests pass.

### Schema, DTOs, and timestamp bookkeeping (research.md R3/R4)

- [X] T006 [P] Add `updatedAt` (`OffsetDateTime`) to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/account/AccountDto.java` and an
  `updated_at` (nullable timestamp) column to
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/AccountEntity.java`.
  Set it to `now()` in `AddAccountPostgresAdapter`'s insert and bump it in
  `UpdateAccountPostgresAdapter`'s update.
- [X] T007 [P] Add `updatedAt` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/category/CategoryDto.java` and
  `updated_at` to
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/CategoryEntity.java`.
  Set/bump it in `AddCategoryPostgresAdapter` and `UpdateCategoryPostgresAdapter`.
- [X] T008 [P] Add `updatedAt` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/budget/BudgetDto.java` and `updated_at`
  to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BudgetEntity.java`.
  Set it to `now()` on every call into `SetBudgetPostgresAdapter.upsert` — both the insert and
  update case, since `upsert` handles both today.
- [X] T009 [P] Add `updatedAt` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/RecurringSeriesDto.java` and
  `updated_at` to
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/RecurringSeriesEntity.java`.
  Set it alongside `createdAt` in `RecurringSeriesPostgresAdapter.add(...)`, and bump it in the
  same adapter's status-update method (`UpdateRecurringSeriesStatusPersistencePort`
  implementation — confirm/dismiss both go through it).
- [X] T010 [P] Add `updatedAt` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/goal/SavingsGoalDto.java` and
  `updated_at` to
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/SavingsGoalEntity.java`.
  Set/bump it in `SavingsGoalPostgresAdapter`'s add and update methods.
- [X] T011 [P] Add `recordedAt` (`OffsetDateTime`, set once, never changed) and
  `necessityTagUpdatedAt` (nullable `OffsetDateTime`) to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java`, and matching
  `recorded_at` / `necessity_tag_updated_at` columns on
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java`. Set
  `recordedAt = now()` in `AddBillPostgresAdapter` (covers original bills, correction
  replacements, and reversals alike, since `CorrectBillServiceImpl` writes both through this same
  adapter). Set `necessityTagUpdatedAt = now()` in
  `UpdateBillNecessityTagPostgresAdapter` (feature 018).
- [X] T012 [P] Add `recordedAt` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java` and `recorded_at`
  to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java`.
  Set it to `now()` in `AddIncomePostgresAdapter` (covers original incomes, correction
  replacements, and reversals via `CorrectIncomeServiceImpl`).
- [X] T013 Add `List<BudgetDto> getAll()` to
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetBudgetService.java` and its
  implementation `Domain/src/main/java/at/ymeri/my/finance/domain/service/budget/GetBudgetServiceImpl.java`
  — a pure pass-through to the persistence port's `getAll()`, which already exists (used today by
  `EnvelopeBalances`). Export needs every budget ever set, not just one month's.

### Export engine

- [X] T014 Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/sync/SyncSnapshotDto.java`
  (`schemaVersion` int, `exportedAt`, and one `List<...Dto>` field per entity type — accounts,
  categories, budgets, recurringSeries, bills, incomes, savingsGoals) (depends on T006-T012 for
  the field additions each list element needs).
- [X] T015 Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/ExportSyncSnapshotService.java`
  (single method `SyncSnapshotDto export()`) (depends on T014).
- [X] T016 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/ExportSyncSnapshotServiceImpl.java`
  (`@Service`, constructor-injects `GetAccountService`, `GetCategoryService`, `GetBudgetService`,
  `GetRecurringSeriesService`, `GetBillService`, `GetIncomeService`, `GetSavingsGoalService`).
  Assembles the snapshot by calling each service's `getAll()` (bills/incomes via
  `GetBillService.getAll()`/`GetIncomeService.getAll()` — note these already exclude nothing
  relevant here; unlike normal display use, sync needs every row *including* reversal/superseded
  ones, so use the raw persistence-port-level read where `getAll()` filters them out — confirm
  against `GetBillServiceImpl.getAll()`'s existing filtering behavior and use
  `GetBillPersistencePort.getAll()` directly instead if so, since a faithful snapshot must include
  every row, not just the currently-visible ones). Sets `schemaVersion` to a constant defined
  alongside this class and `exportedAt = now()` (depends on T013, T015).

### Merge engine (the substantial new business logic — Constitution Principle VI applies in full)

- [X] T017 [P] Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/sync/MergePlanDto.java`,
  `EntityMergeCounts.java` (`added`, `updated`, `unchanged` ints), and
  `CorrectionConflict.java` (the two sibling rows and which one won) (depends on T014).
- [X] T018 [P] Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/SyncEntityMatching.java` — one
  matching method per entity type with a natural key, per data-model.md's table: `matchAccount`
  (by `name`), `matchCategory` (by `name`), `matchBudget` (by `categoryId`+`year`+`month`),
  `matchRecurringSeries` (by `transactionType`+`groupKey`+
  `RecurringMatching.normalizeDescription(description)`, reusing the existing normalizer rather
  than reimplementing it). Savings goals and bills/incomes have no natural-key method — they match
  by `id` only, directly in the merge planner (depends on T014).
- [X] T019 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/ComputeMergePlanService.java` —
  pure and read-only (loads current local state via the `Get*Service`/`Get*PersistencePort`s, is
  handed the incoming `SyncSnapshotDto`, never writes anything). Processes entity types in
  dependency order (research.md R6): accounts → categories → budgets & recurring series → bills &
  incomes → savings goals. For each mutable entity type: match incoming to local by id, else by
  natural key (T018); no match → `toInsert`; match with incoming `updatedAt` later than local →
  `toUpdate`; otherwise → `unchanged`. For bills/incomes: match by id only; no match → `toInsert`;
  after matching, group by non-null `correctsTransactionId` — where more than one non-reversal row
  (across the merged local+incoming set) shares one `correctsTransactionId`, the row with the
  latest `recordedAt` is the winner (added to the plan as the entity's current value if not
  already applied locally) and every other sibling is recorded as a `CorrectionConflict` entry,
  never dropped (depends on T016, T017, T018).
- [X] T020 [US-shared] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/sync/ComputeMergePlanServiceTest.java`
  (depends on T019), covering per entity type: a new incoming item with no local match →
  `toInsert`; an id match with a later incoming `updatedAt` → `toUpdate`; an id match with an
  earlier or equal incoming `updatedAt` → `unchanged`; a natural-key match with *different* ids
  (category by name, budget by categoryId+year+month, recurring series by
  groupKey+normalizedDescription) resolved the same last-modified-wins way; for bills/incomes
  specifically: two sibling corrections of the same original with different `recordedAt` values
  resolve deterministically to the later one, and the earlier one appears as a `CorrectionConflict`
  rather than vanishing; re-planning against an already-fully-merged snapshot produces an
  all-`unchanged` plan (idempotency, mirrors FR-009).
- [X] T021 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/ApplyMergePlanService.java` —
  takes a `MergePlanDto` and writes it, in the same dependency order as T019, through each
  entity's persistence port *directly* (`AddAccountPersistencePort`, `AddBillPersistencePort`,
  `SetBudgetPersistencePort.upsert`, etc. — never through `Add*Service`/`Update*Service`, per
  research.md R5, since those re-run origin-device validation that could incorrectly reject
  already-valid incoming data), wrapped in one transaction via the existing `UnitOfWork` port
  (depends on T019).
- [X] T022 [US-shared] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/sync/ApplyMergePlanServiceTest.java`
  (depends on T021), covering: a plan with only `toInsert`/`toUpdate` entries never calls any
  delete-capable port method (FR-011's never-deletes guarantee, verified via mock interaction,
  not just absence of a delete call site); writes go through the persistence ports, not
  `Add*Service`/`Update*Service` (verified the same way); an account referenced by a bill is
  applied before that bill regardless of their relative order inside the plan's own collections.

### Preview/Apply services + Application layer

- [X] T023 [P] Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/PreviewSyncImportService.java` and
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/ApplySyncImportService.java` (each a
  single method taking a `SyncSnapshotDto` and returning an `ImportSummaryDto`) (depends on T017).
- [X] T024 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/PreviewSyncImportServiceImpl.java`
  (calls `ComputeMergePlanService` only, summarizes the resulting `MergePlanDto` into an
  `ImportSummaryDto` with `applied=false`, never calls `ApplyMergePlanService`) and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/sync/ApplySyncImportServiceImpl.java`
  (calls `ComputeMergePlanService` then `ApplyMergePlanService` then summarizes with
  `applied=true`) (depends on T019, T021, T023).
- [X] T025 [P] Implement
  `Application/src/main/java/at/ymeri/my/finance/controller/sync/SyncExportController.java`
  (implements the generated `SyncExportApi` delegate) and
  `Application/src/main/java/at/ymeri/my/finance/application/mapper/SyncMapper.java` (MapStruct
  interface, `@Mapper` + `INSTANCE`, mirroring `RecurringCostSummaryMapper`'s shape — maps
  `SyncSnapshotDto` ↔ the generated `SyncSnapshot`, and `ImportSummaryDto` ↔ the generated
  `ImportSummary`; amount fields need explicit `@Mapping` decimal-string conversion, matching how
  `correctBillRequest` is handled in `BillCorrectionController.toAmount`) (depends on T005, T016).
- [X] T026 Implement
  `Application/src/main/java/at/ymeri/my/finance/controller/sync/SyncImportController.java`
  (implements the generated `SyncImportApi` delegate; both `previewSyncImport` and
  `applySyncImport` methods map the incoming API `SyncSnapshot` to `SyncSnapshotDto` via
  `SyncMapper` — including parsing every decimal-string amount to `BigDecimal` — before calling
  `previewSyncImportService`/`applySyncImportService`, and add an `@ExceptionHandler` for a
  malformed/unrecognized-`schemaVersion` request → 400, per FR-010) (depends on T005, T024, T025).

**Checkpoint**: `GET /api/v1/sync/export`, `POST /api/v1/sync/import/preview`, and
`POST /api/v1/sync/import/apply` are fully functional and correct end-to-end, with the merge
engine's hardest cases (natural-key fallback, last-modified-wins, the correction tie-breaker,
never-deletes) proven by T020/T022. Every user story below is a frontend increment on top of this
already-correct backend.

---

## Phase 3: User Story 1 - Export a Full Snapshot (Priority: P1) 🎯 MVP

**Goal**: A user can export everything on this device into one file.

**Independent Test**: Trigger an export on a device with existing data and confirm a single file
downloads containing it.

- [X] T027 [P] [US1] In `frontend/src/types/index.ts`, add `ImportSummary` and
  `EntityMergeCounts` types mirroring `sync-model.yaml` field-for-field. Type `SyncSnapshot` as an
  opaque `Record<string, unknown>` rather than fully mirroring every nested entity shape — the
  frontend only ever fetches it, hands it back unmodified, and downloads/reads it as a file; it
  never constructs or inspects individual snapshot fields itself.
- [X] T028 [P] [US1] Create `frontend/src/utils/downloadFile.ts` exporting
  `downloadJsonFile(filename: string, data: unknown): void` — builds a `Blob`, an
  `URL.createObjectURL`, and a temporary anchor element with a `download` attribute to trigger the
  browser's save flow (this repo's first use of this pattern — confirmed no existing
  `createObjectURL`/`Blob`/`download=` usage anywhere in `frontend/src`).
- [X] T029 [P] [US1] Add `fetchSyncExport(): Promise<unknown>` to `frontend/src/api/client.ts`
  (`GET /api/v1/sync/export`, returns the raw parsed JSON body).
- [X] T030 [US1] Create `frontend/src/components/SyncPage.tsx` with an "Export" button that calls
  `fetchSyncExport()` then `downloadJsonFile(...)` with a filename like
  `passbook-sync-<ISO date>.json` (depends on T027, T028, T029).
- [X] T031 [US1] Mount `<SyncPage />` in `frontend/src/App.tsx` as its own settings-style entry
  point, mirroring how `CategoriesPage`/`AccountsPage`/`BudgetingPage`/`SavingsGoalsPage` are each
  reached as their own view rather than a dashboard card (depends on T030).

**Checkpoint**: User Story 1 is independently testable per its Independent Test above.

---

## Phase 4: User Story 2 - Bootstrap a New Device from an Export (Priority: P2)

**Goal**: A user can import a snapshot into a fresh device and end up with a faithful copy.

**Independent Test**: Export from a device with data, import into a fresh device with none, and
verify the fresh device's data now matches the source.

- [X] T032 [P] [US2] Add `previewSyncImport(snapshot: unknown): Promise<ImportSummary>` and
  `applySyncImport(snapshot: unknown): Promise<ImportSummary>` to `frontend/src/api/client.ts`
  (`POST /api/v1/sync/import/preview`, `POST /api/v1/sync/import/apply`) (depends on T027).
- [X] T033 [US2] Extend `SyncPage.tsx` with an import flow: a file picker
  (`<input type="file" accept=".json,application/json">` + `FileReader`, mirroring
  `ImportTransactionsDialog.tsx`'s existing pattern) parses the chosen file as JSON, calls
  `previewSyncImport`, and renders a basic summary (total items to be added, total to be updated)
  with Confirm/Cancel actions. Confirm calls `applySyncImport` and shows a completion message;
  Cancel discards the parsed file and makes no further call — per FR-008, nothing is applied
  unless the user explicitly confirms (depends on T030, T032).

**Checkpoint**: User Stories 1 AND 2 both independently functional.

---

## Phase 5: User Story 3 - Reconcile Two Devices That Changed Independently (Priority: P3)

**Goal**: Importing an export that reflects independent changes on both devices merges correctly
— nothing lost, nothing duplicated, conflicts resolved deterministically.

**Independent Test**: Diverge two linked devices, export from one, import into the other, and
verify the combined result — this is already guaranteed correct by the Foundational phase's
`ComputeMergePlanService`/`ApplyMergePlanService` and their tests (T019-T022); this phase's only
remaining task makes that correctness *visible* to the user.

- [X] T034 [US3] Extend `SyncPage.tsx`'s import summary (from T033) to surface
  `correctionConflictsResolved` as its own explicit line whenever it is greater than zero (e.g.
  "2 correction conflicts resolved — the more recently made correction was kept on each"). No
  backend change: the count was already computed correctly by the Foundational merge engine and
  is already present on every `previewSyncImport`/`applySyncImport` response (depends on T033).

**Checkpoint**: User Stories 1, 2, AND 3 all independently functional.

---

## Phase 6: User Story 4 - Review Before Applying an Import (Priority: P4)

**Goal**: The import summary the user reviews before confirming is a genuine, informative
breakdown, not just a total.

**Independent Test**: Select an import file, see counts broken down by data type before
confirming, and confirm cancelling leaves local data untouched (already guaranteed by T033's
Cancel path — this phase upgrades what Confirm's preview actually shows).

- [X] T035 [US4] Extend `SyncPage.tsx`'s import summary (from T033/T034) from the basic
  added/updated totals into a full per-data-type table — accounts, categories, budgets, recurring
  series, bills, incomes, savings goals, each row showing added/updated/unchanged — reusing the
  already-complete `ImportSummary` shape the Foundational phase's endpoints have returned from the
  start (depends on T033).

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T036 [P] Run `./mvnw -pl Domain test` and confirm T020, T022, plus every pre-existing Domain
  test, all pass.
- [X] T037 [P] Run `cd frontend && npx tsc --noEmit` to typecheck the new frontend code (this repo
  has no frontend test runner anywhere — a pre-existing gap this feature does not introduce or
  worsen).
- [ ] T038 Execute `specs/019-device-sync-export/quickstart.md`'s 6 manual scenarios end-to-end
  against a running stack, simulating two devices with two separate database instances. Expected
  BLOCKED in this development sandbox (no Docker daemon available, and this feature specifically
  also needs a second simulated device/database beyond what every prior feature's quickstart
  needed) — must be run manually once implementation lands in an environment with Docker; report
  honestly rather than marking complete if not actually run.
- [X] T039 Mark all tasks in this file `[X]`, then commit and push the implementation to
  `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T025/T026 need the generated APIs from T005) —
  BLOCKS all four user stories in full (unlike prior features, there is no meaningful partial
  start here: even User Story 2's trivial case runs through the complete merge engine).
- **User Story 1 (Phase 3)**: Depends on Foundational completion (needs `GET /sync/export`
  working). No dependency on US2/US3/US4.
- **User Story 2 (Phase 4)**: Depends on Foundational completion, and extends the component T030
  created — start after T030, not merely after Phase 2.
- **User Story 3 (Phase 5)**: Depends on Foundational completion and extends the summary T033
  rendered — start after T033.
- **User Story 4 (Phase 6)**: Same — depends on Foundational completion and T033; independent of
  US3's T034 (both extend the same summary block but touch different, non-overlapping parts of
  it — sequence them one after the other to avoid a same-file conflict, not because either
  logically depends on the other).
- **Polish (Phase 7)**: Depends on every phase above being complete.

### Within Each Phase

- T001/T002/T003/T004 (different files) run in parallel; T005 needs all four.
- T006-T012 (seven different entity/DTO pairs, no cross-dependency) run in parallel; T013 is
  independent of all of them (the port method it wraps already exists). T014 needs T006-T012;
  T015 needs T014; T016 needs T013+T015.
- T017/T018 (different files, both depend only on T014) run in parallel; T019 needs T016+T017+T018;
  T020 needs T019; T021 needs T019; T022 needs T021.
- T023 needs T017; T024 needs T019+T021+T023; T025 needs T005+T016 (parallel with T024); T026
  needs T005+T024+T025.
- T027/T028/T029 (different files) run in parallel; T030 needs all three; T031 needs T030.
- T032 needs T027 (parallel with anything in Phase 3 after T027 lands); T033 needs T030+T032.
- T034 is a single-file edit, strictly after T033.
- T035 is a single-file edit, strictly after T033 — sequence after T034 to avoid a same-file
  conflict with it (see Phase Dependencies note above).

### Parallel Opportunities

- Phase 1: T001 ∥ T002 ∥ T003 ∥ T004.
- Phase 2: T006 ∥ T007 ∥ T008 ∥ T009 ∥ T010 ∥ T011 ∥ T012 ∥ T013; T017 ∥ T018; T024 ∥ T025.
- Phase 3: T027 ∥ T028 ∥ T029.
- Phase 7: T036 ∥ T037.

---

## Parallel Example: Phase 2 (Schema/DTO/timestamp work)

```bash
# Launch all seven independent entity/DTO timestamp tasks together:
Task: "Add updatedAt to AccountDto/AccountEntity, set/bump in Add/UpdateAccountPostgresAdapter"
Task: "Add updatedAt to CategoryDto/CategoryEntity, set/bump in Add/UpdateCategoryPostgresAdapter"
Task: "Add updatedAt to BudgetDto/BudgetEntity, set/bump in SetBudgetPostgresAdapter.upsert"
Task: "Add updatedAt to RecurringSeriesDto/RecurringSeriesEntity, set/bump in RecurringSeriesPostgresAdapter"
Task: "Add updatedAt to SavingsGoalDto/SavingsGoalEntity, set/bump in SavingsGoalPostgresAdapter"
Task: "Add recordedAt + necessityTagUpdatedAt to BillDto/BillEntity"
Task: "Add recordedAt to IncomeDto/IncomeEntity"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (OpenAPI contracts + codegen wiring for both endpoints).
2. Complete Phase 2: Foundational (CRITICAL and unusually large for this feature — the entire
   merge engine, including everything User Stories 2-4 will ever need, lives here; this is where
   nearly all new logic and all required tests are).
3. Complete Phase 3: User Story 1 (Export button + file download).
4. **STOP and VALIDATE**: Run Domain tests (T036) and confirm US1's Independent Test manually.
5. This is a deployable MVP on its own terms — a working manual backup mechanism — even before
   import (US2-US4) exists at all.

### Incremental Delivery

1. Setup + Foundational → the entire export/import engine ready and tested.
2. Add User Story 1 → Export → deploy/demo (MVP).
3. Add User Story 2 → Import with a basic summary → deploy/demo (this is the point two devices can
   first actually sync).
4. Add User Story 3 → correction-conflict visibility → deploy/demo.
5. Add User Story 4 → full per-type summary breakdown → deploy/demo.
6. Polish (T036-T039).

---

## Notes

- [P] tasks touch different files with no dependency on an incomplete task.
- [Story] labels map each user-story-phase task to spec.md's US1/US2/US3/US4 for traceability.
- Tests are included per Constitution Principle VI (NON-NEGOTIABLE for Domain financial logic) —
  T020 and T022 must pass before Phase 2 is considered complete; they carry the real correctness
  weight of this feature, not the thinner per-story frontend tasks that follow them.
- Commit after each phase checkpoint, matching this project's established pattern across features
  012-018.
- T038 (quickstart.md walkthrough) is expected to be BLOCKED in this sandbox — report that
  honestly rather than marking it complete without having actually run it, consistent with every
  prior feature.
