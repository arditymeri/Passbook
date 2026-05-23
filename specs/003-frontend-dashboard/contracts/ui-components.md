# UI Component Contracts: Frontend Dashboard

**Feature**: 003-frontend-dashboard
**Date**: 2026-05-23

Each section below defines the props contract and rendered behaviour of one component.

---

## `MonthNav`

**Purpose**: Controls the selected period; emits navigation events to `App`.

**Props**:
```ts
interface MonthNavProps {
  year: number;
  month: number;
  onPrevious: () => void;
  onNext: () => void;
}
```

**Rendered**: A row with a left arrow button, the current month/year label (e.g. "May 2026"),
and a right arrow button. Buttons are always enabled (including future months).

---

## `SummaryCard`

**Purpose**: Displays total income, total expenses, and net balance for the selected period.

**Props**:
```ts
interface SummaryCardProps {
  summary: MonthlySummary | null;
  loading: boolean;
  error: string | null;
}
```

**Rendered**:
- Loading: spinner or "Loading…" text.
- Error: "Could not load summary" message.
- Data: three labelled values — Income (green), Expenses (red), Net Balance (green if ≥ 0, red if < 0), each formatted as currency.

---

## `CategorySpend`

**Purpose**: Shows per-category spending as a ranked list with CSS progress bars.

**Props**:
```ts
interface CategorySpendProps {
  spendingByCategory: Record<string, number>; // categoryId → amount
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}
```

**Rendered**:
- Loading: "Loading…" text.
- Error: "Could not load spending data" message.
- Empty: "No spending data for this month" placeholder.
- Data: list ordered by amount descending. Each row: category name (or ID fallback), amount,
  CSS progress bar scaled to the maximum category amount in the list.

---

## `BudgetStatus`

**Purpose**: Shows budget vs. actual per category with an over/under badge.

**Props**:
```ts
interface BudgetStatusProps {
  entries: BudgetStatusEntry[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}
```

**Rendered**:
- Loading: "Loading…" text.
- Error: "Could not load budget data" message.
- Empty: "No budget data for this month" placeholder.
- Data: list of entries. Each row: category name, budgeted amount, actual amount, remaining
  amount, and a badge — green "UNDER BUDGET" or red "OVER BUDGET".

---

## `RecentTransactions`

**Purpose**: Lists the 10 most recent transactions (bills + incomes) for the selected month.

**Props**:
```ts
interface RecentTransactionsProps {
  transactions: Transaction[];
  loading: boolean;
  error: string | null;
}
```

**Rendered**:
- Loading: "Loading…" text.
- Error: "Could not load transactions" message.
- Empty: "No transactions for this month" placeholder.
- Data: list ordered by date descending (max 10). Each row: date (formatted as DD MMM),
  description (or "—" if null), amount formatted as currency, type badge ("EXPENSE" in red or
  "INCOME" in green).

---

## `App` (root)

**Responsibility**: Holds `period` state, renders `MonthNav` and the four section components,
wires `useDashboardData(year, month)` output to each component's props.

**Layout**: Single column, sections stacked vertically:
1. `MonthNav` (top)
2. `SummaryCard`
3. `CategorySpend` and `BudgetStatus` (side by side on wide screens, stacked on narrow)
4. `RecentTransactions` (bottom)
