---
description: "Task list for Net Worth Trend"
---

# Tasks: Net Worth Trend

**Input**: Design documents from `/specs/014-net-worth-trend/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (no contracts/ — no REST endpoint is added or changed, per plan.md)

**Tests**: Not included. Same reasoning as 012/013's tasks.md: this codebase's `frontend/` has never
had a test runner, and this feature adds no Domain code, so the Constitution's Test-First
principle (VI, scoped to "financial calculation and business-rule logic in the Domain module")
does not apply. Verification is `tsc --noEmit`, a manual re-check of the derivation against
`data-model.md`'s worked example (Polish phase), and the manual `quickstart.md` walkthrough.

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3). The
shared pieces every story needs — the trend-point type and the derivation functions
(`currentNetWorth`, `computeNetWorthTrend`) proven correct in `research.md`/`data-model.md` — are
built once in Foundational. `NetWorthCard.tsx` is then a single component built incrementally: US1
gives it the current-total stat; US2 adds the trend chart at a fixed default range; US3 adds the
range selector (the same incremental-single-component pattern `TransactionFilterBar` (012) and
`SetupTemplateDialog` (013) already used).

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

**Purpose**: The trend-point type and the two derivation functions every user story's UI depends
on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T001 [P] Add `NetWorthRangeMonths` (`3 | 6 | 12`) and `NetWorthTrendPoint` (`label: string`, `cutoff: string`, `netWorth: number`) to `frontend/src/types/index.ts`, per `data-model.md`
- [X] T002 Create `frontend/src/utils/netWorthTrend.ts`: `currentNetWorth(accounts: Account[]): number` (sums `account.balance`, `0` for an empty list) and `computeNetWorthTrend(accounts: Account[], allTransactions: Transaction[], monthsBack: NetWorthRangeMonths): NetWorthTrendPoint[]` implementing `data-model.md`'s formula exactly — `monthsBack` cutoffs (end-of-UTC-month for every point except the most recent, which uses `now`), each point's `netWorth = currentNetWorth(accounts) − Σ(future income) + Σ(future bills)`, oldest first (depends on T001)

**Checkpoint**: The trend-point type and both derivation functions exist, matching `data-model.md`'s worked example. User story UI work can now begin.

---

## Phase 2: User Story 1 - See Current Total Net Worth (Priority: P1)

**Goal**: The dashboard shows a single live total net worth figure, equal to the sum of every
account's current balance, with a clear zero/empty state when there are no accounts.

**Independent Test**: Confirm the shown total matches the sum of every account's balance from the
Accounts page; confirm it updates after a new transaction is recorded, with no accounts confirm a
clear empty state instead of an error.

### Implementation for User Story 1

- [X] T003 [US1] Create `frontend/src/components/NetWorthCard.tsx`: a dashboard card (`Paper`, mirroring `SummaryCard.tsx`'s styling) showing `currentNetWorth(accounts)` formatted as currency, with a clear "No accounts yet" message in place of the figure when `accounts.length === 0` (FR-007) (depends on T002)
- [X] T004 [US1] Modify `frontend/src/App.tsx`: mount `NetWorthCard` on the dashboard near `SummaryCard` (outside the month-scoped `Box` row containing `CategorySpend`/`BudgetStatus`/`UpcomingRecurring`, since net worth is deliberately independent of the `MonthNav`-selected month), passing the `accounts` and `allTransactions` `useDashboardData` already fetches (depends on T003)

**Checkpoint**: Users see their current total net worth on the dashboard, always live.

---

## Phase 3: User Story 2 - See the Trend Over Recent Months (Priority: P1)

**Goal**: Below the current total, a chart shows net worth at the end of each of the last several
months, so the user can see whether it's rising, falling, or flat.

**Independent Test**: Confirm each point in the trend matches `computeNetWorthTrend`'s value for
that cutoff, independently recomputed from known transaction history (e.g. a month with a large
bill should show a visible dip); confirm a correction to a past transaction changes the affected
month's point on reload.

### Implementation for User Story 2

- [X] T005 [US2] Extend `frontend/src/components/NetWorthCard.tsx`: below the current total, render a trend chart via a small inline-SVG line (a `<polyline>` scaled to the data's min/max, a `<circle>` and a currency value label per point, month labels below each point — per `research.md`'s hand-rolled-SVG decision), using `computeNetWorthTrend(accounts, allTransactions, 6)` (a fixed default range for now; the `6` becomes a real selection in US3) (depends on T003)

**Checkpoint**: Users see a 6-month net worth trend on the dashboard, combined with US1's live total.

---

## Phase 4: User Story 3 - Adjust How Far Back the Trend Shows (Priority: P2)

**Goal**: The user can switch the trend between 3, 6, and 12 months.

**Independent Test**: Select a shorter range and confirm the chart shows only that many recent
months; select a longer range and confirm every already-shown point's value is unchanged while
more months appear before it.

### Implementation for User Story 3

- [X] T006 [US3] Extend `frontend/src/components/NetWorthCard.tsx`: add a range selector (`ToggleButtonGroup`/`ToggleButton`, mirroring `AccountList.tsx`'s existing type-filter pattern) offering `3`/`6`/`12` months, tracked in local state and passed as `computeNetWorthTrend`'s `monthsBack` argument in place of T005's hardcoded `6` (depends on T005)

**Checkpoint**: All three user stories are independently functional — the full current-total/trend/range-selection experience works end to end.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Full verification once all stories are implemented

- [X] T007 Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new/modified files — confirmed: exit 0, no output.
- [X] T008 Re-verify `frontend/src/utils/netWorthTrend.ts` by hand against `data-model.md`'s worked example (two accounts, one income, one bill, 3-month range) and confirm the three computed points exactly match the worked-example table's values — confirmed: ran the shipped module directly (via `tsx`, with `Date` mocked to the worked example's "now") against the exact scenario; computed points `[6500, 6200, 6200]` match the worked-example table exactly, and `currentNetWorth` independently matches `6200`.
- [X] T009 Execute `specs/014-net-worth-trend/quickstart.md` end to end in the browser — BLOCKED: requires the full Docker Compose stack (Postgres) not available in this environment, same gap as every prior feature (007/009/010/011/012/013). All code paths were verified by direct reading against `data-model.md`, by running the shipped derivation module against the worked example (T008), and by `tsc --noEmit` (T007); no runtime data was available to click through the actual dashboard UI.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately. T002 depends on T001 — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Foundational (T002) — the natural first story, establishing `NetWorthCard` and its `App.tsx` mount point every later story extends
- **User Story 2 (Phase 3)**: Depends on US1's card (T003) already existing — adds the chart below the same total
- **User Story 3 (Phase 4)**: Depends on US2's chart (T005) already existing — adds a range control on top of it
- **Polish (Phase 5)**: Depends on all three user stories being complete

### User Story Dependencies

Like 012/013, priority order already matches dependency order here: each story extends the same
one component (`NetWorthCard.tsx`), so they must be built in sequence (US1 → US2 → US3) even
though each is independently *testable* once its own task is done — see each phase's Independent
Test.

### Parallel Opportunities

- Foundational: T001 has no dependency and could start immediately; T002 is sequential after it (single file, needs the types)
- US2 (T005) and US3 (T006) both touch `NetWorthCard.tsx` and so must run sequentially relative to each other — file-based coordination, not a logical dependency beyond what's already noted

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Foundational
2. Complete Phase 2 (US1): live current total on the dashboard
3. Complete Phase 3 (US2): 6-month trend chart below it
4. **STOP and VALIDATE**: confirm the current total matches the Accounts page's sum, and that the
   trend's points match hand-computed values for a few known transactions (per T008's approach)
5. Add Phase 4 (US3) — the range selector — as a refinement; the feature already delivers its core
   value with the fixed 6-month default from US2

### Incremental Delivery

1. Foundational → trend-point type and both derivation functions ready, proven against the worked
   example
2. US1 → live current total; immediately useful on its own
3. US2 → 6-month trend chart; MVP core value delivered
4. US3 → adjustable range
5. Polish → type-check, hand-verification against the worked example, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- No backend task exists in this file — `Domain/`, `Application/`, `Infrastructure/`, and
  `integration-tests/` are all untouched by this feature, per `plan.md`'s Constitution Check
- `computeNetWorthTrend()` always returns `monthsBack` points regardless of which story is asking
  for them — US1 doesn't call it at all (it only needs `currentNetWorth`), and US2/US3 both call
  the same function with a different `monthsBack`, so there is no story-scoped variant of the
  derivation logic itself, only of what UI drives its `monthsBack` argument
- The trend's correctness is the entire point of this feature (per `research.md`'s proof), so T008
  is not a formality — actually re-deriving the worked example by hand against the shipped code is
  the closest thing this feature has to a regression test, given the frontend has no test runner
