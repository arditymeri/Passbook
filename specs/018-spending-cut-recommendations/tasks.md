---

description: "Task list for the spending cut recommendations feature"
---

# Tasks: Spending Cut Recommendations

**Input**: Design documents from `/specs/018-spending-cut-recommendations/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included and REQUIRED for the new Domain logic — Constitution Principle VI (Test-First,
NON-NEGOTIABLE) mandates unit-test coverage for all new financial calculation / business-rule
logic in the Domain module. No frontend test tasks are included: this repo has no frontend test
runner anywhere (a confirmed pre-existing gap across every prior feature), and this feature does
not introduce one — the new `computeSpendingCutRecommendations` utility is hand-verified against
worked examples instead (see T024's description), mirroring `computeSpendingTrends` (016).

**Organization**: Tasks are grouped by user story. US1 (ranked recurring costs) and US3
(price-creep call-outs) share one backend read model (`GetRecurringCostSummaryServiceImpl`) since
both need the exact same per-series monthly-equivalent/original-amount data — that shared service
is built in Foundational, and US3's own phase becomes a pure frontend rendering task over data
US1's phase already fetches, mirroring how the 015 forecast engine was shared between its US1/US2.
US2 (necessity tagging) and US4 (category signals) are backend/frontend-independent of that shared
service and of each other.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths are included in every task

## Path Conventions

Existing hexagonal Maven multi-module backend + Vite/React frontend (see plan.md Project
Structure). Confirmed against the existing `bill` and `recurring` features' actual files before
writing the tasks below:

- Domain API interfaces flat under `domain/api/` (e.g. `GetBillService` lives directly there).
- Domain SPI (persistence port) interfaces under `domain/spi/<feature>/`.
- Domain DTOs namespaced by feature under `domain/data/<feature>/`.
- Infrastructure entities flat under `infrastructure/entity/`; adapters under
  `infrastructure/adapter/postgres/<feature>/`; the Infrastructure module's own `BillMapper`
  (DTO↔Entity) lives at `infrastructure/mapper/BillMapper.java` — distinct from the
  **Application**-module `BillMapper` (DTO↔API model) at `application/mapper/BillMapper.java`;
  do not confuse the two when editing.
- Hand-written Application controllers implementing a generated delegate live at
  `at.ymeri.my.finance.controller.<feature>.XxxController` — **not**
  `at.ymeri.my.finance.application.controller...` (that package is reserved for the
  *generated* delegate interfaces themselves, per the `api-package` property in the root
  `pom.xml`). Confirmed against `RecurringGetController`/`BillCorrectionController`.
- Hand-written MapStruct Application mappers live flat under
  `at.ymeri.my.finance.application.mapper.*`.
- `skipOverwrite=true` is set on every `openapi-generator-maven-plugin` execution in
  `Application/pom.xml`, and `Application/target/generated-sources/` already contains a stale
  `Bill.java` (and other models) from prior builds — regenerating with `skipOverwrite=true` will
  **not** add the new `necessityTag` field to it. T005 must `clean` first.

---

## Phase 1: Setup (OpenAPI contracts, spec-first per Constitution Principle VII)

- [X] T001 [P] Create `Application/src/main/resources/swagger/bill/bill-necessity-tag-controller.yaml`
  and add `necessityTag` (enum `NECESSARY|AVOIDABLE|UNNECESSARY`, nullable) to the existing `bill`
  schema plus a new `updateNecessityTagRequest` schema in
  `Application/src/main/resources/swagger/bill/bill-model.yaml`, adapting
  `specs/018-spending-cut-recommendations/contracts/necessity-tag-api.yaml` /
  `contracts/necessity-tag-model.yaml` to this repo's real swagger conventions (compare
  `bill-correction-controller.yaml` for style: `servers` block, `tags:`, `$ref` syntax, and the
  existing `bill` schema in `bill-model.yaml` — add the field there rather than duplicating the
  schema).
- [X] T002 [P] Create `Application/src/main/resources/swagger/recurring/recurring-cost-summary-controller.yaml`
  and add `recurringCostSummaryItem` / `recurringCostSummaryResponse` schemas to
  `Application/src/main/resources/swagger/recurring/recurring-model.yaml`, adapting
  `specs/018-spending-cut-recommendations/contracts/recurring-cost-summary-api.yaml` /
  `contracts/recurring-cost-summary-model.yaml` (compare `recurring-get-controller.yaml` for
  style).
- [X] T003 [P] Register a new `bill-necessity-tag` code-gen execution in `Application/pom.xml`,
  modeled exactly on the existing `bill-correction` `<execution>` block (same `apiPackage`
  `${api-package}.bill`, `modelPackage` `${model-package}`, and `configOptions` —
  `delegatePattern`, `interfaceOnly`, `useSpringBoot3`, `skipOverwrite`, etc. — as every other
  execution in that file): `<id>bill-necessity-tag</id>`, `<inputSpec>` pointing at
  `swagger/bill/bill-necessity-tag-controller.yaml`.
- [X] T004 [P] Register a new `recurring-cost-summary` code-gen execution in `Application/pom.xml`,
  modeled on the existing `recurring-get` `<execution>` block: `<id>recurring-cost-summary</id>`,
  `<inputSpec>` pointing at `swagger/recurring/recurring-cost-summary-controller.yaml`,
  `<apiPackage>${api-package}.recurring</apiPackage>`.
- [X] T005 Run `./mvnw -pl Application clean generate-sources` (depends on T001-T004) — **must**
  use `clean` (or manually delete `Application/target/generated-sources/`) so the stale
  `skipOverwrite=true`-protected `Bill.java` is regenerated with the new `necessityTag` field
  rather than silently kept without it. Confirm the generated `BillNecessityTagApi` delegate,
  updated `Bill`/`UpdateNecessityTagRequest` models, `RecurringCostSummaryApi` delegate, and
  `RecurringCostSummaryItem`/`RecurringCostSummaryResponse` models all appear under
  `Application/target/generated-sources/openapi/src/main/java/at/ymeri/my/finance/application/`.

---

## Phase 2: Foundational (blocking prerequisites)

**⚠️ CRITICAL**: Every user story reads data produced here — no story's frontend work can start
until this phase is complete.

### Necessity tag persistence (feeds US1's page shell and, primarily, US2)

- [X] T006 [P] Add `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/NecessityTag.java`
  (enum `NECESSARY, AVOIDABLE, UNNECESSARY`); add a nullable `NecessityTag necessityTag` field to
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java`; add a nullable
  `@Column(name = "necessity_tag") private String necessityTag;` field to
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java` (stored
  as the enum's `name()`, consistent with how `CategoryEntity.type` stores a String rather than a
  JPA enum type elsewhere in this codebase — check `CategoryEntity.java` for the exact pattern).
- [X] T007 [P] Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/RecurringCostSummaryItemDto.java`
  (`seriesId`, `description`, `monthlyEquivalentAmount` `BigDecimal`, `originalAmount`
  `BigDecimal`, `priceIncreased` `boolean`, `increaseAmount` `BigDecimal`, per data-model.md). Add
  a package-private `static double occurrencesPerMonth(RecurringFrequency frequency)` helper to
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/RecurringMatching.java`
  (`DAILY -> 30.44, WEEKLY -> 4.348, MONTHLY -> 1, YEARLY -> 1.0/12`) — this is the same file that
  already owns `nominalInterval`/`toleranceFor`/`predictNextDate`, so every recurring-frequency
  constant lives in one place (research.md R4).
- [X] T008 Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/spi/bill/UpdateBillNecessityTagPersistencePort.java`
  (single method `BillDto updateNecessityTag(String billId, NecessityTag tag)`, `tag` nullable to
  clear) (depends on T006).
- [X] T009 Implement
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/bill/UpdateBillNecessityTagPostgresAdapter.java`
  (`@Service`, constructor-injects `BillRepository`; `findById(UUID.fromString(billId))`, set
  `necessityTag` (or `null`), `save`, map back via `BillMapper.INSTANCE.map(...)` — mirror
  `AddBillPostgresAdapter`'s shape) (depends on T006, T008).
- [X] T010 Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/UpdateBillNecessityTagService.java`
  (single method `BillDto updateNecessityTag(String billId, NecessityTag tag)`) (depends on T006).
- [X] T011 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/UpdateBillNecessityTagServiceImpl.java`
  (`@Service`, constructor-injects `UpdateBillNecessityTagPersistencePort` and `GetBillService`).
  Validates the bill id is one of `getBillService.getAll()`'s current/visible ids — throw
  `NoSuchElementException("Bill not found: " + billId)` otherwise (covers "doesn't exist", "is a
  reversal row", and "has been superseded by a correction" in one check, per data-model.md's
  validation rule) — then delegates to the port (depends on T008, T010).
- [X] T012 [US-shared] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/UpdateBillNecessityTagServiceImplTest.java`
  (depends on T011), with a fake/mock port and `GetBillService`, covering: setting each of the
  three tag values on a visible bill; clearing a tag (`null`); not-found when the id isn't in
  `getAll()`'s result (covers both "never existed" and "reversal/superseded" cases).
- [X] T013 Update `replacement(...)` in
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/CorrectBillServiceImpl.java` to
  add `replacement.setNecessityTag(current.getNecessityTag());` (research.md R3 — a tag must
  survive a correction of amount/date/category/account). Do **not** add it to
  `BillCorrections.reversalOf(...)` — a reversal is a system-generated bookkeeping row, never
  shown to the user. Add a case to the existing
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/CorrectBillServiceImplTest.java`
  asserting a tagged bill's replacement carries the same tag (depends on T006).

### Recurring cost summary (feeds US1 and US3)

- [X] T014 Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetRecurringCostSummaryService.java`
  (single method `List<RecurringCostSummaryItemDto> getSummary()`) (depends on T007).
- [X] T015 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/GetRecurringCostSummaryServiceImpl.java`
  (`@Service`, constructor-injects `GetRecurringSeriesService` and `RecurringSeriesMembers`,
  mirroring `GetUpcomingRecurringServiceImpl`'s constructor shape). For each `CONFIRMED` series
  with a non-empty `RecurringSeriesMembers.membersOf(series)` list: `first = members.get(0)`,
  `last = members.get(size-1)`; `monthlyEquivalentAmount = last.amount() *
  occurrencesPerMonth(series.getFrequency())`, rounded per Constitution Principle IV
  (`RoundingMode.HALF_EVEN`, scale 2); `originalAmount = first.amount()`; `priceIncreased =
  !RecurringMatching.isWithinAmountTolerance(first.amount(), last.amount()) &&
  last.amount().compareTo(first.amount()) > 0`; `increaseAmount = priceIncreased ?
  last.amount().subtract(first.amount()) : null` (depends on T007, T014).
- [X] T016 [US-shared] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/recurring/GetRecurringCostSummaryServiceImplTest.java`
  (depends on T015), covering: monthly-equivalent conversion for each of DAILY/WEEKLY/MONTHLY/YEARLY;
  a series whose latest amount exceeds its first beyond tolerance → `priceIncreased=true` with the
  correct `increaseAmount`; a series within tolerance → not flagged; a series whose latest amount
  is *lower* than its first → not flagged (FR-010); a `PROPOSED` series excluded entirely; a
  `CONFIRMED` series with zero members skipped defensively (mirrors
  `GetUpcomingRecurringServiceImpl`'s existing `members.isEmpty()` guard).

### Application layer (both endpoints)

- [X] T017 [P] Implement
  `Application/src/main/java/at/ymeri/my/finance/controller/bill/BillNecessityTagController.java`
  implementing the generated `BillNecessityTagApi` delegate (depends on T005, T011): calls
  `updateBillNecessityTagService.updateNecessityTag(id, request.getTag() != null ?
  NecessityTag.valueOf(request.getTag().name()) : null)`, maps the result through the existing
  **Application**-module `BillMapper.INSTANCE.map(...)` (`at.ymeri.my.finance.application.mapper.BillMapper`
  — confirm no code change is needed there: MapStruct auto-maps the new same-named `necessityTag`
  field once both `BillDto` and the generated `Bill` model declare it, exactly like every other
  field on that interface, which has no hand-written method bodies today), and add
  `@ExceptionHandler(NoSuchElementException.class)` → 404, mirroring `BillCorrectionController`.
- [X] T018 [P] Create
  `Application/src/main/java/at/ymeri/my/finance/application/mapper/RecurringCostSummaryMapper.java`
  (MapStruct interface, `@Mapper` + `INSTANCE` static field, mirroring `RecurringSeriesMapper`),
  mapping `List<RecurringCostSummaryItemDto>` → the generated `RecurringCostSummaryResponse`
  (depends on T005, T007).
- [X] T019 Implement
  `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringCostSummaryController.java`
  implementing the generated `RecurringCostSummaryApi` delegate (depends on T005, T015, T018),
  mirroring `RecurringGetController`'s shape.

### Frontend data plumbing (both endpoints)

- [X] T020 [P] In `frontend/src/types/index.ts`: add `export type NecessityTag = 'NECESSARY' |
  'AVOIDABLE' | 'UNNECESSARY';`; add `necessityTag?: NecessityTag` to the existing `Bill` and
  `Transaction` interfaces; add `RecurringCostSummaryItem` (mirroring
  `recurring-cost-summary-model.yaml` field-for-field, same pattern as the existing
  `UpcomingRecurringItem`/`PriceChangeAlert` types added for feature 010).
- [X] T021 [P] In `frontend/src/api/client.ts`: add `updateBillNecessityTag(id: string, tag:
  NecessityTag | null): Promise<Bill>` (`PUT /api/v1/bills/${id}/necessity-tag`, body `{ tag }`,
  mirroring `correctBill`'s `putAndReturn` usage but returning the updated bill rather than
  `void`); add `fetchRecurringCostSummary(): Promise<RecurringCostSummaryItem[]>` (`GET
  /api/v1/recurring-series/cost-summary`, unwrapping `.items`, mirroring `fetchRecurringSeries`).
- [X] T022 In `frontend/src/hooks/useDashboardData.ts`, thread `necessityTag: b.necessityTag ??
  undefined` into the existing `billTxns` mapping (around line 105-113) so `allTransactions`
  carries each bill's tag (depends on T020).

**Checkpoint**: `PUT /api/v1/bills/{id}/necessity-tag` and `GET
/api/v1/recurring-series/cost-summary` are fully functional end-to-end; the frontend has every
type and fetch function it needs. All four user-story phases below can now proceed.

---

## Phase 3: User Story 1 - See Recurring Costs Ranked by Size (Priority: P1) 🎯 MVP

**Goal**: A user sees every confirmed recurring bill/subscription ranked from highest to lowest
monthly cost, with a running total.

**Independent Test**: Confirm three or more recurring bill series with different amounts, open
the recommendations view, and verify they appear ordered from most to least expensive with a
correct running total.

- [X] T023 [P] [US1] Create `frontend/src/utils/spendingCutRecommendations.ts` exporting
  `computeSpendingCutRecommendations(recurringItems: RecurringCostSummaryItem[]):
  SpendingCutRecommendations` (initial shape per data-model.md — `recurringItems` sorted by
  `monthlyEquivalentAmount` descending, `totalMonthlyRecurringSpend` = their sum,
  `taggedTransactions: []`, `categoryOpportunities: []`, `potentialMonthlySavings =
  totalMonthlyRecurringSpend` for now — later phases extend this same function's signature and
  body, mirroring how `computeSpendingTrends` (016) grew incrementally). Hand-verify against a
  worked example (three items of different amounts) in a scratch script before moving on, per this
  repo's established no-test-runner convention.
- [X] T024 [US1] Create `frontend/src/components/SpendingCutRecommendationsPage.tsx` (depends on
  T021, T023): on mount, call `fetchRecurringCostSummary()`, run
  `computeSpendingCutRecommendations`, render the ranked list (description, monthly-equivalent
  amount) and the running total; render the explanatory empty state (FR-018) when there is nothing
  to show yet.
- [X] T025 [US1] Mount `<SpendingCutRecommendationsPage />` in `frontend/src/App.tsx` — a new
  navigation entry/section, consistent with how prior feature pages/cards were mounted (depends on
  T024).

**Checkpoint**: User Story 1 is independently testable per its Independent Test above.

---

## Phase 4: User Story 2 - Tag Transactions by Necessity (Priority: P2)

**Goal**: A user can tag a bill Necessary/Avoidable/Unnecessary, and Avoidable/Unnecessary-tagged
transactions appear in the recommendations view, folded into the total.

**Independent Test**: Tag a handful of bill transactions with each of the three labels, open the
recommendations view, and verify the Avoidable/Unnecessary-tagged transactions and their total
amount are shown, while the Necessary-tagged one is excluded.

- [X] T026 [P] [US2] Create `frontend/src/components/NecessityTagControl.tsx` (depends on T021): a
  small control (e.g. a `Chip` showing the current tag or a neutral "Tag" placeholder, opening a
  MUI `Menu` with the three tag options plus "Clear tag" on click — mirrors the existing
  three-dot-menu pattern in `RecentTransactions.tsx`), calling `updateBillNecessityTag` and a
  passed-in `onChanged` callback on selection.
- [X] T027 [US2] Mount `<NecessityTagControl />` in `frontend/src/components/RecentTransactions.tsx`
  for `t.type === 'BILL'` rows only (income has no necessity tag — FR edge case), wired through a
  new `onTagChanged` prop so the existing transactions-refresh mechanism (already used after
  correct/remove) also fires after a tag change (depends on T026).
- [X] T028 [US2] Extend `computeSpendingCutRecommendations` in
  `frontend/src/utils/spendingCutRecommendations.ts` to accept `allTransactions: Transaction[]`
  and populate `taggedTransactions` (every `BILL` transaction with `necessityTag` `AVOIDABLE` or
  `UNNECESSARY`, per FR-007/008), and fold their amounts into `potentialMonthlySavings` (depends
  on T023).
- [X] T029 [US2] Extend `SpendingCutRecommendationsPage.tsx` to fetch `allTransactions` (reuse
  `useDashboardData` or fetch bills directly — match whichever this repo's existing pages do for a
  standalone page vs. the dashboard) and pass them into the util; render the tagged-transactions
  list (depends on T024, T028).

**Checkpoint**: User Stories 1 AND 2 both independently functional.

---

## Phase 5: User Story 3 - Catch Recurring Charges That Have Crept Up in Price (Priority: P3)

**Goal**: A recurring series whose most recent charge is higher than its earliest recorded charge
is called out with the original amount, current amount, and increase size.

**Independent Test**: Confirm a recurring series whose amount increased between its first and most
recent occurrence, open the recommendations view, and verify the increase is called out with the
correct before/after amounts.

- [X] T030 [US3] Extend `SpendingCutRecommendationsPage.tsx`'s ranked-recurring-list rendering
  (from T024) to show a "price increased" badge/annotation (original → current, delta) on any item
  where `priceIncreased` is `true` — every field needed (`originalAmount`, `monthlyEquivalentAmount`,
  `increaseAmount`) is already present on each `RecurringCostSummaryItem` fetched in Phase 2/US1;
  no util or backend change needed (depends on T024).

**Checkpoint**: User Stories 1, 2, AND 3 all independently functional.

---

## Phase 6: User Story 4 - Spot Categories Trending Up or Running Over Budget (Priority: P4)

**Goal**: Expense categories over budget in the most recently completed month, or trending upward
over recent months, are shown with their excess amount versus their typical/target level.

**Independent Test**: Set a budget for a category, record spend in that category above the budget
for the most recent completed month, open the recommendations view, and verify the category
appears with the correct excess amount.

- [X] T031 [US4] Extend `computeSpendingCutRecommendations` in
  `frontend/src/utils/spendingCutRecommendations.ts` to accept `budgetStatusEntries:
  BudgetStatusEntry[]` (for the most recently completed month) and `categoryTrendMovers:
  SpendingMover[]` (the output of the existing `computeSpendingTrends` from
  `frontend/src/utils/spendingTrends.ts` — reused, not reimplemented, per research.md R5).
  Produce `categoryOpportunities`: every `BudgetStatusEntry` with `status === 'OVER_BUDGET'`
  (excess = `actual - budgeted`) unioned with every mover where `change > 0` (excess = `change`);
  when a category qualifies both ways, emit it once with the larger excess (FR-008/013). Fold the
  sum into `potentialMonthlySavings` (depends on T023).
- [X] T032 [US4] Extend `SpendingCutRecommendationsPage.tsx` to compute the most-recently-completed
  calendar month, call the existing `fetchBudgetStatus(year, month)` for it, call the existing
  `computeSpendingTrends` for the movers, pass both into the util, and render the
  category-opportunities section (depends on T024, T031).

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T033 [P] Run `./mvnw -pl Domain test` and confirm T012, T016, and the T013 correction-
  propagation addition all pass, plus every pre-existing Domain test unaffected.
- [X] T034 [P] Run `cd frontend && npx tsc --noEmit` to typecheck the new frontend code (this repo
  has no frontend test runner anywhere — a pre-existing gap this feature does not introduce or
  worsen).
- [ ] T035 Execute `specs/018-spending-cut-recommendations/quickstart.md`'s 6 manual scenarios
  end-to-end against a running stack. **BLOCKED** in this development sandbox — confirmed no Docker
  daemon available (`docker info` fails), consistent with every prior feature 007-015. Must be run
  manually once implementation lands in an environment with Docker.
- [X] T036 Mark all tasks in this file `[X]`, then commit and push the implementation to
  `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T017/T019 need the generated APIs from T005) —
  BLOCKS all four user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational completion. No dependency on US2/US3/US4.
- **User Story 2 (Phase 4)**: Depends on Foundational completion, and T028/T029 extend files T023/
  T024 created — start those after T023/T024, not merely after Phase 2.
- **User Story 3 (Phase 5)**: Depends on Foundational completion and extends the component T024
  produced — start after T024.
- **User Story 4 (Phase 6)**: Depends on Foundational completion, and T031/T032 extend T023/T024 —
  start after those.
- **Polish (Phase 7)**: Depends on every phase above being complete.

### Within Each Phase

- T001/T002/T003/T004 (different files) can run in parallel; T005 needs all four.
- T006/T007 (different files, no cross-dependency) can run in parallel. T008 needs T006; T009
  needs T006+T008; T010 needs T006; T011 needs T008+T010; T012 needs T011; T013 needs T006.
  T014 needs T007; T015 needs T007+T014; T016 needs T015.
- T017/T018 can run in parallel (different files); T017 needs T005+T011; T018 needs T005+T007;
  T019 needs T005+T015+T018.
- T020/T021 (different files) can run in parallel; T022 needs T020.
- T023 can start once T020 lands (needs the types, not the endpoints yet — though realistically
  wait for the full Phase 2 checkpoint). T024 needs T021+T023. T025 needs T024.
- T026 needs T021; T027 needs T026; T028 needs T023; T029 needs T024+T028.
- T030 needs T024 (single-file edit, strictly after T024/T029 to avoid merge conflicts on the same
  component — sequence T030 after Phase 4 completes even though it has no logical dependency on
  US2's content).
- T031 needs T023; T032 needs T024+T031 (same same-file-conflict consideration as T030 — sequence
  after Phase 5).

### Parallel Opportunities

- Phase 1: T001 ∥ T002 ∥ T003 ∥ T004.
- Phase 2: T006 ∥ T007 (then their respective downstream chains proceed independently of each
  other); T017 ∥ T018; T020 ∥ T021.
- Phase 7: T033 ∥ T034.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Launch independent foundational tasks together:
Task: "Add NecessityTag enum + BillDto/BillEntity fields in Domain/Infrastructure"
Task: "Add RecurringCostSummaryItemDto + occurrencesPerMonth helper in Domain"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (OpenAPI contracts + codegen wiring for both endpoints).
2. Complete Phase 2: Foundational (CRITICAL — blocks all four stories; this is where nearly all
   new backend logic and required tests live).
3. Complete Phase 3: User Story 1 (ranked recurring list + total).
4. **STOP and VALIDATE**: Run Domain tests (T033) and confirm US1's Independent Test manually.
5. This is a deployable MVP — a user already sees their priciest recurring costs even before
   tagging (US2), price-creep call-outs (US3), or category signals (US4) exist.

### Incremental Delivery

1. Setup + Foundational → both endpoints ready and tested.
2. Add User Story 1 → ranked recurring list → deploy/demo (MVP).
3. Add User Story 2 → tagging + tagged-transaction section → deploy/demo.
4. Add User Story 3 → price-creep badges → deploy/demo.
5. Add User Story 4 → category opportunities → deploy/demo.
6. Polish (T033-T036).

---

## Notes

- [P] tasks touch different files with no dependency on an incomplete task.
- [Story] labels map each user-story-phase task to spec.md's US1/US2/US3/US4 for traceability.
- Tests are included per Constitution Principle VI (NON-NEGOTIABLE for Domain financial logic) —
  T012, T016, and T013's added case must pass before Phase 2 is considered complete.
- Commit after each phase checkpoint, matching this project's established pattern across features
  012-015.
- T035 (quickstart.md walkthrough) is expected to be BLOCKED in this sandbox — report that
  honestly rather than marking it complete without having actually run it, consistent with every
  prior feature.
