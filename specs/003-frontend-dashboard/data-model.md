# Data Model: Frontend Dashboard

**Feature**: 003-frontend-dashboard
**Date**: 2026-05-23

All types are TypeScript interfaces defined in `frontend/src/types/index.ts`.
No new backend schema changes — these mirror existing API response shapes.

---

## Period (local state)

```ts
interface Period {
  year: number;   // e.g. 2026
  month: number;  // 1–12
}
```

Held in `App` component state. Default: current calendar month from `new Date()`.

---

## API Response Types (from backend)

### MonthlySummary
Mirrors `GET /api/v1/analysis/monthly` → `summary` field.

```ts
interface MonthlySummary {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  netBalance: number;
  spendingByCategory: Record<string, number>; // categoryId → amount
}
```

### BudgetStatusEntry
Mirrors one entry in `GET /api/v1/budgets/status` → `entries` array.

```ts
type BudgetStatusValue = 'UNDER_BUDGET' | 'OVER_BUDGET';

interface BudgetStatusEntry {
  categoryId: string;
  budgeted: number;
  actual: number;
  remaining: number;
  status: BudgetStatusValue;
}
```

### Bill
Mirrors one item from `GET /api/v1/bills` → `bills` array (relevant fields only).

```ts
interface Bill {
  id: string;
  description: string | null;
  amount: number;
  time: string; // ISO 8601 datetime
  categoryId: string | null;
}
```

### Income
Mirrors one item from `GET /api/v1/incomes` → `incomes` array (relevant fields only).

```ts
interface Income {
  id: string;
  description: string | null;
  amount: number;
  time: string; // ISO 8601 datetime
}
```

### Category
Mirrors one item from `GET /api/v1/categories` → array (relevant fields only).

```ts
interface Category {
  id: string;
  name: string;
}
```

---

## View Models (derived client-side)

### Transaction (US4 merged list)

```ts
interface Transaction {
  id: string;
  description: string | null;
  amount: number;
  time: string;         // ISO 8601
  type: 'BILL' | 'INCOME';
}
```

Assembled by merging filtered bills and incomes, sorting by `time` descending, slicing to 10.

### CategoryNameMap

```ts
type CategoryNameMap = Map<string, string>; // categoryId → name
```

Built once from the categories response. Used by `CategorySpend` and `BudgetStatus` to resolve
display names. Falls back to the raw category ID if name not found.

---

## Dashboard Data State (from `useDashboardData` hook)

```ts
interface DashboardData {
  summary: MonthlySummary | null;
  summaryLoading: boolean;
  summaryError: string | null;

  budgetEntries: BudgetStatusEntry[];
  budgetLoading: boolean;
  budgetError: string | null;

  transactions: Transaction[];
  transactionsLoading: boolean;
  transactionsError: string | null;

  categoryNames: CategoryNameMap;
  categoriesLoading: boolean;
}
```

Each section has its own loading/error state so failures are isolated (FR-006).
