# Component Contracts: Bill/Income Correction Flow

## Type additions (`frontend/src/types/index.ts`)

```ts
export interface Transaction {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  type: 'BILL' | 'INCOME';
  categoryId?: string;
  accountId?: string;             // NEW — needed to pre-fill the correction form's account select
  source?: IncomeSource;          // NEW — income-only; needed to pre-fill the correction form
  correctsTransactionId?: string; // NEW — non-null means this transaction was itself a correction
}

export interface CorrectBillRequest {
  amount: string;   // decimal string — Principle IV forbids JS `number` for money
  time: string;
  description?: string;
  categoryId?: string;
  accountId?: string;
}

export interface CorrectIncomeRequest {
  amount: string;   // decimal string — Principle IV forbids JS `number` for money
  time: string;
  description?: string;
  source?: IncomeSource;
  accountId?: string;
}

export interface TransactionHistoryEntry {
  id: string;
  amount: number;
  time: string;
  description: string | null;
}
```

`useDashboardData.ts`'s existing `Bill[]`/`Income[]` → `Transaction[]` mapping gains `accountId`,
`source` (income only), and `correctsTransactionId` alongside the fields it already copies over —
no new fetch, just carrying three more fields from data it already has.

## API client additions (`frontend/src/api/client.ts`)

```ts
export async function correctBill(id: string, req: CorrectBillRequest): Promise<void> {
  await putAndReturn<unknown>(`/api/v1/bills/${id}`, req);
}

export async function removeBill(id: string): Promise<void> {
  await del(`/api/v1/bills/${id}`);
}

export async function fetchBillHistory(id: string): Promise<TransactionHistoryEntry[]> {
  const data = await request<{ history: TransactionHistoryEntry[] }>(`/api/v1/bills/${id}/history`);
  return data.history ?? [];
}

export async function correctIncome(id: string, req: CorrectIncomeRequest): Promise<void> {
  await putAndReturn<unknown>(`/api/v1/incomes/${id}`, req);
}

export async function removeIncome(id: string): Promise<void> {
  await del(`/api/v1/incomes/${id}`);
}

export async function fetchIncomeHistory(id: string): Promise<TransactionHistoryEntry[]> {
  const data = await request<{ history: TransactionHistoryEntry[] }>(`/api/v1/incomes/${id}/history`);
  return data.history ?? [];
}
```

Two new low-level helpers are needed alongside the existing `request`/`post`/`postAndReturn`:

```ts
async function putAndReturn<T>(url: string, body: unknown): Promise<T> { /* mirrors postAndReturn, method: 'PUT' */ }
async function del(url: string): Promise<void> { /* mirrors post, method: 'DELETE', no body */ }
```

## `RecentTransactions` component changes

**File**: `frontend/src/components/RecentTransactions.tsx` (existing, modified)

- Each row gains a trailing `IconButton` (`MoreVertIcon`) opening a small MUI `Menu` with three
  items: **Correct**, **Remove**, **History**.
- A row whose `correctsTransactionId` is set shows a small outlined `Chip` (or an `EditIcon` with a
  tooltip "Corrected") next to its description, giving the User Story 3 "user can see it was
  corrected" signal at a glance, without needing to open History.
- New props: `onCorrect: (t: Transaction) => void`, `onRemove: (t: Transaction) => void`,
  `onHistory: (t: Transaction) => void` — `RecentTransactions` stays presentational; `App.tsx` owns
  which dialog is open, matching the existing pattern where `App.tsx` owns `billFormOpen`/`incomeFormOpen`.

## `CorrectBillForm` / `CorrectIncomeForm` components

**Files**: `frontend/src/components/CorrectBillForm.tsx`, `frontend/src/components/CorrectIncomeForm.tsx` (new)

```ts
interface CorrectBillFormProps {
  open: boolean;
  transaction: Transaction | null;  // the bill being corrected; form is pre-filled from this
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];
  accounts: Account[];
}
```

(`CorrectIncomeFormProps` is the same shape minus `categories`, mirroring how `AddIncomeForm`
already omits it.)

Structurally these are `AddBillForm`/`AddIncomeForm` with two differences:
- On `open`, local field state initializes from `transaction`'s current values instead of blank
  defaults (matching FR-002).
- Submit calls `correctBill(transaction.id, req)` / `correctIncome(transaction.id, req)` instead of
  `createBill`/`createIncome`; a `409` response shows `"This transaction was already corrected or
  removed — please refresh and try again"` instead of the create-forms' generic error.

All field-level validation (amount > 0, required fields, `type="text"` amount input) is identical to
the existing Add forms — Constitution Principle IV compliance carries over unchanged.

## `RemoveConfirmDialog` component

**File**: `frontend/src/components/RemoveConfirmDialog.tsx` (new, shared by both bill and income)

```ts
interface RemoveConfirmDialogProps {
  open: boolean;
  transaction: Transaction | null;
  onClose: () => void;
  onConfirm: () => void;   // caller (App.tsx) knows whether to call removeBill or removeIncome
  submitting: boolean;
}
```

A simple MUI `Dialog` with the transaction's description/amount shown for confirmation context, a
warning that this cannot show a corrected value afterward (only a plain removal), and
`Button variant="outlined"` Cancel / `Button variant="contained" color="error"` Remove — satisfying
FR-008's mandatory confirmation step.

## `TransactionHistoryDialog` component

**File**: `frontend/src/components/TransactionHistoryDialog.tsx` (new, shared by both types)

```ts
interface TransactionHistoryDialogProps {
  open: boolean;
  transaction: Transaction | null;
  history: TransactionHistoryEntry[];
  loading: boolean;
  onClose: () => void;
}
```

Renders a simple MUI `List` of `history` entries (each showing its amount, date, and description),
newest-first, in a `Dialog`. `App.tsx` fetches history via `fetchBillHistory`/`fetchIncomeHistory`
(based on `transaction.type`) when the dialog opens.

## `App.tsx` changes

- Owns three new pieces of dialog state: `correctingTransaction: Transaction | null`,
  `removingTransaction: Transaction | null`, `viewingHistoryFor: Transaction | null` — each drives
  the corresponding dialog's `open` prop (`!= null`).
- On any successful correct/remove, calls the existing `handleSaveSuccess` (`refreshKey` bump) so
  every dependent view (summary, category spend, budget status, account balances, recent
  transactions) refreshes exactly as it already does after creating a new bill/income.
