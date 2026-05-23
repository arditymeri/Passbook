# Data Model: Frontend Data Entry

**Feature**: 004-frontend-data-entry
**Date**: 2026-05-23

All types extend `frontend/src/types/index.ts`.

---

## New Types

### CreateBillRequest
Maps to the `bill` schema in `bill-model.yaml`.

```ts
interface CreateBillRequest {
  amount: number;       // required, > 0
  time: string;         // ISO 8601 datetime (from date input + T00:00:00Z)
  description?: string;
  categoryId?: string;  // UUID of existing category, optional
}
```

### CreateIncomeRequest
Maps to the `createIncomeRequest` schema in `income-model.yaml`.

```ts
type IncomeSource = 'SALARY' | 'FREELANCE' | 'INVESTMENT' | 'RENTAL' | 'GIFT' | 'OTHER';

interface CreateIncomeRequest {
  amount: number;        // required, > 0
  time: string;          // ISO 8601 datetime
  description?: string;
  source?: IncomeSource; // optional
}
```

---

## Form State (local component state, not persisted)

### BillFormState
```ts
interface BillFormState {
  amount: string;       // controlled input value (string until parsed)
  date: string;         // YYYY-MM-DD from date input
  description: string;
  categoryId: string;   // '' if none selected
  submitting: boolean;
  error: string | null;
}
```

### IncomeFormState
```ts
interface IncomeFormState {
  amount: string;
  date: string;         // YYYY-MM-DD
  description: string;
  source: string;       // '' if none selected
  submitting: boolean;
  error: string | null;
}
```

---

## API Calls (additions to `client.ts`)

```ts
// POST /api/v1/createBill
createBill(req: CreateBillRequest): Promise<void>

// POST /api/v1/incomes
createIncome(req: CreateIncomeRequest): Promise<void>
```

Both throw on non-2xx status so the form can show an error message.
