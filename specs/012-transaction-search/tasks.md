---
description: "Task list for Transaction Search & Filtering"
---

# Tasks: Transaction Search & Filtering

**Input**: Design documents from `/specs/012-transaction-search/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (no contracts/ — no REST endpoint is added or changed, per plan.md)

**Tests**: Not included. Per `research.md`'s "No new automated tests for `filterTransactions()`"
decision: this codebase's `frontend/` has never had a test runner (no vitest/jest/testing-library
dependency, no `*.test.ts` file anywhere), and this feature adds no Domain code, so the
Constitution's Test-First principle (VI, scoped to "financial calculation and business-rule logic
in the Domain module") does not apply. Verification is `tsc --noEmit` plus the manual
`quickstart.md` walkthrough (Polish phase), matching every prior feature's documented frontend
testing convention.

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3 → US4).
The shared pieces every story needs — the `TransactionFilters` type, the `filterTransactions()`
pure function (built complete, covering every filter dimension, since it's one small cohesive
utility with no useful "partial" version — unlike 010/011's helpers, which had a genuinely
story-scoped subset to defer), and `useDashboardData.ts`'s new `allTransactions` value — are built
once in Foundational. Each user story then only adds the UI control(s) for its own filter
dimension(s) to the already-complete `TransactionFilterBar`, and (for US1 only) the `App.tsx`
wiring that switches between the default month-scoped view and the filtered view.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure — this feature
touches only `frontend/src/`. No backend module (`Domain/`, `Application/`, `Infrastructure/`,
`integration-tests/`) is touched.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The filter type shape, the matching logic, and the full (unsliced) transaction data
source every user story's UI work depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T001 [P] Add `TransactionTypeFilter` (`'ALL' | 'BILL' | 'INCOME'`) and `TransactionFilters` (`searchText`, `categoryId`, `source`, `accountId`, `startDate`, `endDate`, `minAmount`, `maxAmount`, `type`) to `frontend/src/types/index.ts`, per `data-model.md`
- [ ] T002 [P] Modify `frontend/src/hooks/useDashboardData.ts`: compute and return a new `allTransactions: Transaction[]` value — the same merged bill+income mapping the hook already does for `transactions`, but without the `inMonth(...)` filter and without the `.slice(0, 10)` cap, sorted newest-first, reusing the `bills`/`incomes` arrays already fetched (no new network call) (depends on nothing — independent of T001)
- [ ] T003 Create `frontend/src/utils/transactionFilters.ts`: a pure `filterTransactions(transactions: Transaction[], filters: TransactionFilters): Transaction[]` implementing every predicate from `data-model.md`'s pseudocode (case-insensitive substring match on `description` for `searchText`; exact match for `categoryId`, `source`, `accountId`; inclusive bounds for `startDate`/`endDate` and `minAmount`/`maxAmount`; `type` filter), all combined with AND logic (FR-001–FR-009) (depends on T001)

**Checkpoint**: The filter data shape, matching logic, and full transaction source all exist. User story UI work can now begin.

---

## Phase 2: User Story 1 - Find a Transaction by Description (Priority: P1)

**Goal**: A user can search their full transaction history (not just the current month) by
description text and see matching results, with a clear empty state when nothing matches.

**Independent Test**: Type a partial description matching a transaction from a month other than
the one currently selected on the dashboard; confirm it appears. Search for text matching nothing;
confirm a "No transactions found" message appears. Clear the search; confirm the view returns to
the normal month-scoped list.

### Implementation for User Story 1

- [ ] T004 [US1] Create `frontend/src/components/TransactionFilterBar.tsx`: a search text field (the only control at this stage), calling an `onFiltersChange` callback with the updated `TransactionFilters` object (other fields left at their default/unset values) (depends on T001)
- [ ] T005 [US1] Modify `frontend/src/components/RecentTransactions.tsx`: accept an optional `emptyMessage?: string` prop, defaulting to the existing "No transactions for this month." text when not provided
- [ ] T006 [US1] Modify `frontend/src/App.tsx`: add `filters` state initialized to the all-cleared `TransactionFilters` shape from `data-model.md`; compute `isFiltering` (true when any field differs from its cleared default); compute the transactions passed to `RecentTransactions` as `filterTransactions(allTransactions, filters)` when `isFiltering`, else the existing month-scoped `transactions`; pass `emptyMessage="No transactions found"` to `RecentTransactions` when `isFiltering`; mount `TransactionFilterBar` directly above `RecentTransactions` (depends on T002, T003, T004, T005)

**Checkpoint**: Users can search their full transaction history by description from the dashboard.

---

## Phase 3: User Story 2 - Narrow Results by Category, Source, or Account (Priority: P1)

**Goal**: A user can filter by bill category, income source, and/or account, combined with AND
logic and with any active search text.

**Independent Test**: With no search text, select a category; confirm only matching bills appear
and income is excluded. Switch to an income source filter; confirm only matching income appears.
Add an account filter on top; confirm only transactions matching both appear.

### Implementation for User Story 2

- [ ] T007 [US2] Extend `frontend/src/components/TransactionFilterBar.tsx`: add a combined "Category / Source" dropdown (bill categories from the existing `categories` list, then income sources from the existing `IncomeSource` values, in one list — selecting a category clears `source` and vice versa, per `research.md`'s UI decision) and an "Account" dropdown (from the existing `accounts` list), both calling `onFiltersChange` (depends on T004, T006)

**Checkpoint**: Users can narrow by category/source and account, combined with search text from US1.

---

## Phase 4: User Story 3 - Narrow Results by Date Range or Amount Range (Priority: P2)

**Goal**: A user can filter by a date range and/or an amount range, combined with every other
active filter.

**Independent Test**: Set a start and end date; confirm only transactions dated within that range
(inclusive) appear. Set a minimum and/or maximum amount; confirm only transactions within those
bounds appear. Combine with a search term or category filter; confirm all apply together.

### Implementation for User Story 3

- [ ] T008 [US3] Extend `frontend/src/components/TransactionFilterBar.tsx`: add start-date and end-date inputs (mirroring `AddBillForm.tsx`'s `type="date"` `TextField` pattern) and minimum/maximum amount number inputs, all calling `onFiltersChange` (depends on T004, T006)

**Checkpoint**: Users can narrow by date range and amount range, combined with every filter from US1/US2.

---

## Phase 5: User Story 4 - Filter by Transaction Type and Clear All Filters (Priority: P3)

**Goal**: A user can restrict results to bills only or income only, and can clear every active
filter and search term in one action.

**Independent Test**: Select "Bills only"; confirm no income appears (and vice versa for "Income
only"). Activate several filters at once, then select "Clear filters"; confirm every filter resets
and the view returns to the default month-scoped list.

### Implementation for User Story 4

- [ ] T009 [US4] Extend `frontend/src/components/TransactionFilterBar.tsx`: add a transaction-type control ("All" / "Bills only" / "Income only", mapping to `TransactionFilters.type`) and a "Clear filters" button that calls `onFiltersChange` with the all-cleared `TransactionFilters` shape (depends on T004, T006)

**Checkpoint**: All four user stories are independently functional — the full search/filter experience works end to end.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification once all stories are implemented

- [ ] T010 Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new/modified files
- [ ] T011 Re-read `frontend/src/utils/transactionFilters.ts` against `data-model.md`'s pseudocode and confirm every predicate (searchText, categoryId, source, accountId, startDate, endDate, minAmount, maxAmount, type) is implemented exactly as specified, combined with AND logic
- [ ] T012 Execute `specs/012-transaction-search/quickstart.md` end to end in the browser

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately. T001 and T002 can run in parallel; T003 depends on T001 — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Foundational (T001–T003) — the natural first story, since it establishes the `App.tsx` wiring (filter state, `isFiltering`, the switch between month-scoped and filtered views) every later story's new controls plug into without further `App.tsx` changes
- **User Story 2 (Phase 3)**: Depends on US1's `TransactionFilterBar`/`App.tsx` wiring (T004, T006) already existing — only extends the filter bar with new controls
- **User Story 3 (Phase 4)**: Same dependency as US2 — extends the same filter bar, independent of US2's controls (could be built in either order)
- **User Story 4 (Phase 5)**: Same dependency as US2/US3 — extends the same filter bar
- **Polish (Phase 6)**: Depends on all four user stories being complete

### User Story Dependencies

US1 establishes the shared plumbing (filter state, the filtered-vs-default view switch) every
later story relies on; US2, US3, and US4 are otherwise independent of each other — each only adds
its own control(s) to the already-existing `TransactionFilterBar` and could be built in any order
once US1 is done.

### Parallel Opportunities

- Foundational: T001 and T002 in parallel (different files, no dependency on each other); T003 sequential after T001
- US2, US3, and US4's single tasks (T007, T008, T009) all touch the same file (`TransactionFilterBar.tsx`) and so must run sequentially relative to each other, even though they don't depend on each other's *content* — file-based coordination, not a logical dependency

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Foundational
2. Complete Phase 2 (US1): search by description, full history, empty state
3. Complete Phase 3 (US2): category/source and account filters
4. **STOP and VALIDATE**: search for a transaction from a prior month, confirm it's found; filter
   by category and confirm only matching bills show
5. Add Phase 4 (US3) and Phase 5 (US4) — date/amount range and type/clear-all — as refinements on
   top; neither is required for the feature to already be useful

### Incremental Delivery

1. Foundational → filter shape, matching logic, and full transaction data ready
2. US1 → search works end to end; MVP core value delivered
3. US2 → category/source and account narrowing
4. US3 → date and amount range narrowing
5. US4 → type filter and one-click clear
6. Polish → type-check and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- No backend task exists in this file — `Domain/`, `Application/`, `Infrastructure/`, and
  `integration-tests/` are all untouched by this feature, per `plan.md`'s Constitution Check
- `filterTransactions()` is built complete in Foundational (T003) rather than split across
  stories, since — unlike 010's `RecurringMatching` or 011's `SavingsGoalProgress` — there is no
  meaningful "partial" version of a filter function; only the *UI controls* that populate each
  filter field are story-scoped
- `TransactionFilterBar.tsx` submit-as-you-type controls need no in-flight/submitting state (unlike
  every prior feature's create/edit forms) — filtering is a pure, synchronous, local computation
  with no network request per keystroke or per filter change
