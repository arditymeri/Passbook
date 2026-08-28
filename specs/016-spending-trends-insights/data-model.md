# Phase 1 Data Model: Spending Trends & Insights

No new persisted entity or table is introduced (research.md §1 — pure frontend, nothing stored).
This document describes the new **derived/transient** frontend types and the pure computation
function's algorithm, plus a worked example proving the aggregation is straightforward and correct.

## New frontend types (`frontend/src/types/index.ts`)

```ts
export type SpendingTrendRangeMonths = 3 | 6 | 12;

export interface CategoryTrendPoint {
  label: string;   // e.g. "Jun" — short month label, UTC
  cutoff: string;  // ISO string for the last instant of that month, UTC (mirrors NetWorthTrendPoint.cutoff)
  amount: number;  // total spent in this category this month; 0 if none
}

export interface CategorySpendingTrend {
  categoryId: string;
  categoryName: string;   // resolved via categoryNames, falls back to categoryId (see research.md §5)
  points: CategoryTrendPoint[];  // one per month in the selected window, oldest to newest
}

export interface SpendingMover {
  categoryId: string;
  categoryName: string;
  previousAmount: number;  // prior month's total for this category (0 if none)
  currentAmount: number;   // current month's total for this category (0 if none)
  change: number;          // currentAmount - previousAmount (signed: positive = spent more)
  percentChange: number | null; // null when previousAmount is 0 (percentage is undefined from a zero base)
}
```

## `computeSpendingTrends(allTransactions, categoryNames, monthsBack)`

**Signature**: `(allTransactions: Transaction[], categoryNames: CategoryNameMap, monthsBack:
SpendingTrendRangeMonths) => { trends: CategorySpendingTrend[]; movers: SpendingMover[] }`

**Algorithm**:

1. Build the ordered list of `monthsBack` calendar months ending at the current UTC month (oldest
   to newest), the same `Date.UTC(year, month0 - i, ...)` construction `computeNetWorthTrend`
   already uses for its window.
2. Walk `allTransactions` once; for each transaction where `type === 'BILL'` and `categoryId` is
   set, compute its UTC `(year, month0)` bucket from `time`, and accumulate `amount` into
   `perCategoryPerMonth[categoryId][year-month0]`.
3. **Trends**: for each `categoryId` with at least one non-zero bucket *within the selected
   window*, emit a `CategorySpendingTrend` with one `CategoryTrendPoint` per window month (reading
   the accumulated amount, or `0` if that month had none for this category). A category with zero
   in every window month is omitted (FR-007).
4. **Movers**: independently of step 3's window, look up each `categoryId`'s accumulated total for
   the current month and the immediately preceding month (both fixed at "now" and "one month
   before now," never affected by `monthsBack` — research.md §3). For every `categoryId` that
   appears in `allTransactions` at all, if `currentAmount !== previousAmount`, emit a
   `SpendingMover`. Sort by `Math.abs(change)` descending.

**Validation**: `amount`/`change` fields are plain `number` (JS), matching this app's established,
previously-accepted precedent for frontend display-only arithmetic (Constitution Principle IV
governs backend `BigDecimal` computation; the frontend already sums and displays already-serialized
amounts this way in `SummaryCard`, `CategorySpend`, `NetWorthCard`, etc. — not a new deviation).

## Worked example

Given `allTransactions` containing these BILL rows (category `groceries`, `dining`):

| time (UTC)   | categoryId  | amount |
|--------------|-------------|--------|
| 2026-06-05   | groceries   | 120    |
| 2026-06-20   | groceries   | 80     |
| 2026-07-03   | groceries   | 150    |
| 2026-07-10   | dining      | 40     |
| 2026-08-01   | groceries   | 200    |
| 2026-08-15   | dining      | 90     |

With "now" = 2026-08-28 and `monthsBack = 3` (window: Jun, Jul, Aug):

- `groceries` trend points: Jun=200, Jul=150, Aug=200 — all three months shown (has activity in
  the window).
- `dining` trend points: Jun=0, Jul=40, Aug=90 — June explicitly zero (FR-002), not omitted,
  because dining has non-zero activity elsewhere in the window.
- Movers (current month Aug vs previous month Jul, regardless of the 3-month window):
  - `groceries`: previous=150, current=200, change=+50
  - `dining`: previous=40, current=90, change=+50
  - Both tie at +50 — both appear (spec Edge Case: ties are both shown, no arbitrary pick).

This confirms: zero-filling works within a category's own window, whole-window-zero categories are
correctly excludable, and the movers comparison is independent of the trend window's length.
