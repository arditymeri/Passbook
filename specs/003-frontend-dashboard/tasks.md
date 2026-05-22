---
description: "Task list for Frontend Dashboard"
---

# Tasks: Frontend Dashboard

**Input**: Design documents from `/specs/003-frontend-dashboard/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Manual browser testing (no automated frontend test framework in scope for MVP).

**Organization**: Tasks are grouped by user story. Each story delivers an independently
visible and testable increment of the dashboard.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Include exact file paths in descriptions

## Path Conventions

All paths are relative to the repo root:
- `frontend/vite.config.ts` — Vite configuration
- `frontend/src/types/index.ts` — TypeScript interfaces
- `frontend/src/api/client.ts` — fetch wrappers
- `frontend/src/hooks/useDashboardData.ts` — data-fetching hook
- `frontend/src/components/` — React components
- `frontend/src/App.tsx` — root layout
- `frontend/src/App.css` — dashboard styles

---

## Phase 1: Setup

**Purpose**: Project wiring that must exist before any component can work.

- [x] T001 Add Vite dev proxy to `frontend/vite.config.ts`: inside `defineConfig` add `server: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } }` so all `/api/v1/...` fetch calls are forwarded to the backend without CORS issues
- [x] T002 [P] Create directory structure: `frontend/src/api/`, `frontend/src/types/`, `frontend/src/hooks/`, `frontend/src/components/` (run `mkdir -p` for each)
- [x] T003 [P] Create `frontend/src/types/index.ts` with all TypeScript interfaces from data-model.md: `Period`, `MonthlySummary`, `BudgetStatusEntry`, `BudgetStatusValue`, `Bill`, `Income`, `Category`, `Transaction`, `CategoryNameMap`, `DashboardData`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The API client and data hook are shared by all four user story components.

**⚠️ CRITICAL**: No component work can begin until this phase is complete.

- [x] T004 Create `frontend/src/api/client.ts` with five typed async functions using native `fetch`:
  - `fetchMonthlySummary(year: number, month: number): Promise<MonthlySummary>` → `GET /api/v1/analysis/monthly?year={year}&month={month}`, returns `response.summary`
  - `fetchBudgetStatus(year: number, month: number): Promise<BudgetStatusEntry[]>` → `GET /api/v1/budgets/status?year={year}&month={month}`, returns `response.entries`
  - `fetchBills(): Promise<Bill[]>` → `GET /api/v1/bills`, returns `response.bills`
  - `fetchIncomes(): Promise<Income[]>` → `GET /api/v1/incomes`, returns `response.incomes`
  - `fetchCategories(): Promise<Category[]>` → `GET /api/v1/categories`, returns the array
  - Each function throws on non-2xx status; import types from `../types`
- [x] T005 Create `frontend/src/hooks/useDashboardData.ts` — custom hook `useDashboardData(year: number, month: number): DashboardData`:
  - Use `useEffect` to re-run when `year` or `month` changes
  - Fetch categories once on mount (separate `useEffect` with `[]` dep array); build `CategoryNameMap` from the result
  - For period data: call `fetchMonthlySummary`, `fetchBudgetStatus`, `fetchBills`, `fetchIncomes` in parallel using `Promise.allSettled`; each has its own loading/error state in the returned `DashboardData` object
  - Filter bills and incomes to the selected month client-side (compare `time` field year+month), merge into `Transaction[]` tagged with `type`, sort by `time` descending, slice to 10
  - Return the full `DashboardData` shape defined in `frontend/src/types/index.ts`
- [x] T006 Replace `frontend/src/App.tsx` content with a skeleton dashboard layout: import `useState`, initialise `period` state to current month (`new Date()`), import and wire `useDashboardData(period.year, period.month)`, render four placeholder `<section>` divs labelled Summary, Category Spending, Budget Status, Recent Transactions — no real components yet, just the structure with period state flowing correctly

**Checkpoint**: `npm run dev` opens without errors; browser shows the four placeholder sections.

---

## Phase 3: User Story 1 — Monthly Financial Summary (Priority: P1) 🎯 MVP

**Goal**: Dashboard shows income, expenses, net balance for the selected month, and the user can
navigate between months with previous/next controls.

**Independent test**: Open dashboard. Verify current month/year is displayed. Verify income,
expenses, and net balance figures appear (even if zero). Click previous month — verify all
figures update and the month label changes.

- [x] T007 [US1] Create `frontend/src/components/MonthNav.tsx` — functional component accepting `{ year: number, month: number, onPrevious: () => void, onNext: () => void }`; renders a centred row with a `<button>` labelled "‹", a `<span>` showing the month name and year (use `new Date(year, month-1).toLocaleString('default', { month: 'long', year: 'numeric' })`), and a `<button>` labelled "›"; each button calls its respective prop on click
- [x] T008 [US1] Create `frontend/src/components/SummaryCard.tsx` — functional component accepting `{ summary: MonthlySummary | null, loading: boolean, error: string | null }`; shows "Loading…" when `loading`, "Could not load summary" when `error`, otherwise three labelled values: Income (formatted with `Intl.NumberFormat`), Expenses, Net Balance; apply `color: green` when net balance ≥ 0 and `color: red` when < 0; import types from `../types`
- [x] T009 [US1] Wire `MonthNav` and `SummaryCard` into `frontend/src/App.tsx`: replace the Summary placeholder `<section>` with `<MonthNav>` + `<SummaryCard>`; implement `onPrevious` handler (decrement month, wrap year: if month=1 go to month=12 of year-1); implement `onNext` handler (increment month, wrap year: if month=12 go to month=1 of year+1); pass `summary`, `summaryLoading`, `summaryError` from `useDashboardData` to `SummaryCard`

---

## Phase 4: User Story 2 — Category Spending Breakdown (Priority: P2)

**Goal**: Dashboard shows per-category spending for the selected month, ordered by amount
descending, with a CSS progress bar scaled to the largest category.

**Independent test**: With bills assigned to multiple categories, open dashboard — verify category
section lists each category with its name (or ID fallback) and amount, highest first. Navigate
to an empty month — verify "No spending data" placeholder appears.

- [x] T010 [US2] Create `frontend/src/components/CategorySpend.tsx` — functional component accepting `{ spendingByCategory: Record<string, number>, categoryNames: CategoryNameMap, loading: boolean, error: string | null }`:
  - Shows "Loading…" when loading, "Could not load spending data" on error
  - Derives sorted entries: `Object.entries(spendingByCategory).sort((a, b) => b[1] - a[1])`
  - Shows "No spending data for this month" placeholder when entries array is empty
  - For each entry renders: category name (look up in `categoryNames`, fallback to ID), amount formatted with `Intl.NumberFormat`, a `<div>` acting as a CSS progress bar with `width` set to `(amount / maxAmount * 100)%`
- [x] T011 [US2] Wire `CategorySpend` into `frontend/src/App.tsx`: replace the Category Spending placeholder `<section>` with `<CategorySpend spendingByCategory={summary?.spendingByCategory ?? {}} categoryNames={categoryNames} loading={summaryLoading} error={summaryError} />`

---

## Phase 5: User Story 3 — Budget vs. Actual Status (Priority: P3)

**Goal**: Dashboard shows per-category budget vs actual with a green UNDER / red OVER badge.

**Independent test**: With at least one budget set and bills in the same month, open the
budget section — verify each entry shows category name, budgeted, actual, remaining, and the
correct badge colour. Navigate to a month with no budget data — verify "No budget data" placeholder.

- [x] T012 [US3] Create `frontend/src/components/BudgetStatus.tsx` — functional component accepting `{ entries: BudgetStatusEntry[], categoryNames: CategoryNameMap, loading: boolean, error: string | null }`:
  - Shows "Loading…", "Could not load budget data", or "No budget data for this month" as appropriate
  - For each entry renders: category name (with ID fallback), budgeted amount, actual amount, remaining amount (all `Intl.NumberFormat` formatted), and a badge `<span>` — text "UNDER BUDGET" with `background: green` or "OVER BUDGET" with `background: red` based on `entry.status`
- [x] T013 [US3] Wire `BudgetStatus` into `frontend/src/App.tsx`: replace the Budget Status placeholder with `<BudgetStatus entries={budgetEntries} categoryNames={categoryNames} loading={budgetLoading} error={budgetError} />`

---

## Phase 6: User Story 4 — Recent Transactions (Priority: P4)

**Goal**: Dashboard shows the 10 most recent bills and incomes for the selected month,
ordered by date descending, with a type badge (EXPENSE / INCOME).

**Independent test**: With several bills and incomes in the selected month, open the transactions
section — verify entries appear newest first, each showing date, description, amount, and type
badge. Navigate to an empty month — verify "No transactions" placeholder.

- [x] T014 [US4] Create `frontend/src/components/RecentTransactions.tsx` — functional component accepting `{ transactions: Transaction[], loading: boolean, error: string | null }`:
  - Shows "Loading…", "Could not load transactions", or "No transactions for this month" as appropriate
  - For each transaction renders: date formatted as "DD MMM" (`new Date(t.time).toLocaleDateString('default', { day: '2-digit', month: 'short' })`), description (or "—" if null), amount formatted with `Intl.NumberFormat`, type badge — "INCOME" in green or "EXPENSE" in red based on `t.type`
- [x] T015 [US4] Wire `RecentTransactions` into `frontend/src/App.tsx`: replace the Recent Transactions placeholder with `<RecentTransactions transactions={transactions} loading={transactionsLoading} error={transactionsError} />`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T016 Replace `frontend/src/App.css` with dashboard styles: reset margins, set `max-width: 960px; margin: 0 auto; padding: 1rem` on `.dashboard`, style `.summary-cards` as a three-column flexbox row, style `.middle-row` as a two-column flexbox row (CategorySpend + BudgetStatus side by side), style progress bars (height: 8px, background: #e0e0e0, border-radius: 4px, inner fill with `background: #4caf50`), style UNDER/OVER badges with padding and border-radius, add red/green utility classes for net balance
- [x] T017 [P] Verify Vite dev server starts cleanly: run `cd frontend && npm install && npm run dev` and confirm `http://localhost:5173` opens without console errors
- [x] T018 [P] Manual browser smoke test: with backend running, open `http://localhost:5173`, verify all four sections render (even with empty data), navigate previous/next month and confirm all sections refresh

---

## Dependencies

```
T001, T002, T003 (parallel) → T004 → T005 → T006
T006 → T007, T008 (parallel) → T009 (US1)
T009 complete → T010 → T011 (US2)
T011 complete → T012 → T013 (US3)
T013 complete → T014 → T015 (US4)
T015 → T016 → T017, T018 (parallel)
```

## Parallel Execution Opportunities

**Phase 1**: T002 and T003 can run in parallel with T001 (different files).

**US1**: T007 and T008 can be written in parallel (different component files); T009 must wait for both.

**Polish**: T017 and T018 can run in parallel.

## Implementation Strategy

| Phase | Deliverable | Value |
|-------|-------------|-------|
| Phase 1–2 (T001–T006) | Wiring + skeleton layout | Dev server runs, API calls wired |
| Phase 3 (T007–T009) | Summary + month nav live | MVP — dashboard shows financial totals |
| Phase 4 (T010–T011) | Category spending visible | Spending breakdown actionable |
| Phase 5 (T012–T013) | Budget status visible | Budget vs actual at a glance |
| Phase 6 (T014–T015) | Recent transactions visible | Full dashboard complete |
| Phase 7 (T016–T018) | Styled + smoke tested | Ship-ready |

**MVP scope**: Complete Phases 1–3 (T001–T009). The monthly summary with month navigation
delivers the primary value and satisfies US1 acceptance criteria independently.
