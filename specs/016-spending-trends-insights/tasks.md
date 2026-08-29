---
description: "Task list for Spending Trends & Insights"
---

# Tasks: Spending Trends & Insights

**Input**: Design documents from `/specs/016-spending-trends-insights/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (no contracts/ — no REST endpoint is added or changed, per plan.md)

**Tests**: Not included. Same reasoning as 012/013/014's tasks.md: this codebase's `frontend/` has
never had a test runner, and this feature adds no Domain code, so the Constitution's Test-First
principle (VI, scoped to "financial calculation and business-rule logic in the Domain module")
does not apply. Verification is `tsc --noEmit`, a manual re-check of the aggregation against
`data-model.md`'s worked example (Polish phase), and the manual `quickstart.md` walkthrough.

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3). The
shared piece every story needs — the new types and the single `computeSpendingTrends` function
(which produces both `trends` and `movers` from one pass over `allTransactions`, per
`data-model.md`) — is built once in Foundational. `SpendingTrendsCard.tsx` is then a single
component built incrementally: US1 gives it the per-category trend list at a fixed default window;
US2 adds the movers section; US3 adds the window selector (the same incremental-single-component
pattern `TransactionFilterBar` (012), `SetupTemplateDialog` (013), and `NetWorthCard` (014) already
used).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US3)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure — this feature
touches only `frontend/src/`. No backend module (`Domain/`, `Application/`, `Infrastructure/`,
`integration-tests/`) is touched.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The new types and the one aggregation function every user story's UI depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T001 [P] Add `SpendingTrendRangeMonths` (`3 | 6 | 12`), `CategoryTrendPoint` (`label: string`,
  `cutoff: string`, `amount: number`), `CategorySpendingTrend` (`categoryId: string`,
  `categoryName: string`, `points: CategoryTrendPoint[]`), and `SpendingMover` (`categoryId:
  string`, `categoryName: string`, `previousAmount: number`, `currentAmount: number`, `change:
  number`, `percentChange: number | null`) to `frontend/src/types/index.ts`, per `data-model.md`
- [X] T002 Create `frontend/src/utils/spendingTrends.ts`:
  `computeSpendingTrends(allTransactions: Transaction[], categoryNames: CategoryNameMap,
  monthsBack: SpendingTrendRangeMonths): { trends: CategorySpendingTrend[]; movers: SpendingMover[]
  }` implementing `data-model.md`'s algorithm exactly — one pass over `allTransactions` bucketing
  every `BILL` transaction with a `categoryId` into `perCategoryPerMonth[categoryId][year-month0]`
  by its UTC month; `trends` = one entry per category with ≥1 non-zero month within the
  `monthsBack` window (zero-filled for other in-window months, oldest→newest, category omitted
  entirely if zero throughout the window — FR-002/FR-007); `movers` = every category present in
  `allTransactions` at all, comparing its fixed current-month vs previous-month total (independent
  of `monthsBack` — research.md §3), sorted by `Math.abs(change)` descending, `percentChange` null
  when `previousAmount` is 0 (depends on T001)

**Checkpoint**: The new types and the aggregation function exist, matching `data-model.md`'s
worked example. User story UI work can now begin.

---

## Phase 2: User Story 1 - See How a Category's Spending Trends Over Time (Priority: P1)

**Goal**: The dashboard shows, per expense category, how much was spent in each of the last
several months — not just the current one.

**Independent Test**: Confirm a category with rising/falling/flat spending across the shown months
is visibly distinguishable without doing the math by hand; confirm a month with no spending in a
category shows as zero rather than being skipped; confirm a category with zero spending in every
shown month does not appear at all.

### Implementation for User Story 1

- [X] T003 [US1] Create `frontend/src/components/SpendingTrendsCard.tsx`: a dashboard card
  (`Paper`, mirroring `CategorySpend.tsx`'s list styling) rendering
  `computeSpendingTrends(allTransactions, categoryNames, 6).trends` — one row per category with a
  per-month amount breakdown (each month's figure shown explicitly, including zero — FR-002), using
  a fixed default window of 6 months for now (US3 makes it selectable); a clear "No spending data
  yet" message when `trends` is empty (depends on T002)
- [X] T004 [US1] Modify `frontend/src/App.tsx`: mount `SpendingTrendsCard` in the month-scoped
  `Box` row alongside `CategorySpend` (which it directly extends — plan.md's Structure Decision),
  passing the `allTransactions` and `categoryNames` `useDashboardData` already fetches (depends on
  T003)

**Checkpoint**: Users see each category's spending across the last several months on the
dashboard.

---

## Phase 3: User Story 2 - See Which Categories Drove This Month's Change (Priority: P1)

**Goal**: Alongside the trend list, a "movers" section calls out the categories whose spending
changed the most versus last month, in either direction.

**Independent Test**: Set up one category with a sharp increase and one with a sharp decrease from
last month, confirm both appear as movers with the size of their change shown and distinguishable
by direction; set up a category with no spending last month but spending this month and confirm it
still appears as a mover.

### Implementation for User Story 2

- [X] T005 [US2] Extend `frontend/src/components/SpendingTrendsCard.tsx`: add a "Biggest Movers"
  section rendering `computeSpendingTrends(allTransactions, categoryNames, 6).movers` — each entry
  showing the category name, the signed euro change (color/icon distinguishing increase from
  decrease), and the percentage change when `previousAmount > 0` (omitted/labeled "new" when
  `percentChange` is `null`, per FR-004) (depends on T003)

**Checkpoint**: Users see both the multi-month trend (US1) and which categories drove the most
recent month's change (US2) — the feature's MVP.

---

## Phase 4: User Story 3 - Adjust How Far Back the Trend Looks (Priority: P2)

**Goal**: The user can switch the trend list between 3, 6, and 12 months of history.

**Independent Test**: Select a shorter window and confirm the trend list shows only that many
recent months; select a longer window and confirm it extends further back; confirm the movers
section is unaffected by the window change (it always compares the same two most recent months —
research.md §3).

### Implementation for User Story 3

- [X] T006 [US3] Extend `frontend/src/components/SpendingTrendsCard.tsx`: add a window selector
  (`ToggleButtonGroup`/`ToggleButton`, mirroring `NetWorthCard.tsx`'s existing range-selector
  pattern) offering `3`/`6`/`12` months, tracked in local state and passed as
  `computeSpendingTrends`'s `monthsBack` argument in place of T003's hardcoded `6` for the
  `trends` half only — the `movers` call keeps passing a fixed `6` (or any constant; `monthsBack`
  has no effect on `movers`'s output per its own logic) so the movers section stays stable as the
  window changes (depends on T005)

**Checkpoint**: All three user stories are independently functional — the full trend/movers/window
experience works end to end.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Full verification once all stories are implemented

- [X] T007 Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the
  new/modified files
- [X] T008 Re-verify `frontend/src/utils/spendingTrends.ts` by hand against `data-model.md`'s
  worked example (groceries/dining across Jun/Jul/Aug) and confirm the computed `trends` and
  `movers` output exactly match the worked example's table and tie-handling
- [ ] T009 Execute `specs/016-spending-trends-insights/quickstart.md` end to end in the browser —
  expected BLOCKED in this development sandbox (no Docker daemon available, consistent with every
  prior feature 007-015); report honestly rather than marking complete if not actually run

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately. T002 depends on T001 —
  BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Foundational (T002) — the natural first story,
  establishing `SpendingTrendsCard` and its `App.tsx` mount point every later story extends
- **User Story 2 (Phase 3)**: Depends on US1's card (T003) already existing — adds the movers
  section alongside the same trend list
- **User Story 3 (Phase 4)**: Depends on US2's card (T005) already existing — adds a window
  control on top of it
- **Polish (Phase 5)**: Depends on all three user stories being complete

### User Story Dependencies

Like 012/013/014, priority order already matches dependency order here: each story extends the
same one component (`SpendingTrendsCard.tsx`), so they must be built in sequence (US1 → US2 → US3)
even though each is independently *testable* once its own task is done — see each phase's
Independent Test.

### Parallel Opportunities

- Foundational: T001 has no dependency and could start immediately; T002 is sequential after it
  (single file, needs the types)
- US1's T004 (`App.tsx`) and any later story's `SpendingTrendsCard.tsx` edit are different files
  and don't conflict, but T004 itself depends on T003 completing first (the component must exist
  before it can be mounted)
- T005 and T006 both touch `SpendingTrendsCard.tsx` and so must run sequentially relative to each
  other — file-based coordination, not a logical dependency beyond what's already noted

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Foundational
2. Complete Phase 2 (US1): per-category trend list at a fixed 6-month window
3. Complete Phase 3 (US2): movers section
4. **STOP and VALIDATE**: confirm trend figures match hand-summed totals for a few known
   transactions, and movers match the expected two-most-recent-months comparison (per T008's
   approach)
5. Add Phase 4 (US3) — the window selector — as a refinement; the feature already delivers its
   core value with the fixed 6-month default from US1/US2

### Incremental Delivery

1. Foundational → types and the aggregation function ready, proven against the worked example
2. US1 → per-category trend list; immediately useful on its own
3. US2 → movers section; MVP core value delivered
4. US3 → adjustable window
5. Polish → type-check, hand-verification against the worked example, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- No backend task exists in this file — `Domain/`, `Application/`, `Infrastructure/`, and
  `integration-tests/` are all untouched by this feature, per `plan.md`'s Constitution Check
- `computeSpendingTrends()` always returns both `trends` and `movers` from a single call — US1
  only renders the `trends` half and US2 only renders the `movers` half, but both come from the one
  shared aggregation pass built in Foundational, so there is no story-scoped variant of the
  aggregation logic itself
- The aggregation's correctness is central to this feature's value (per `data-model.md`'s worked
  example), so T008 is not a formality — actually re-deriving the worked example by hand against
  the shipped code is the closest thing this feature has to a regression test, given the frontend
  has no test runner
