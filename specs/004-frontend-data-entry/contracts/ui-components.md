# UI Component Contracts: Frontend Data Entry

**Feature**: 004-frontend-data-entry
**Date**: 2026-05-23

---

## `Modal`

**Purpose**: Reusable overlay wrapper for any modal dialog content.

**Props**:
```ts
interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}
```

**Behaviour**:
- When `open` is false: renders nothing (return null).
- When `open` is true: renders a full-viewport semi-transparent backdrop with the dialog centred.
- Clicking the backdrop calls `onClose`.
- Pressing Escape calls `onClose`.
- Renders `title` as an `<h2>` inside the dialog.

---

## `AddBillForm`

**Purpose**: Form for creating a new expense (bill).

**Props**:
```ts
interface AddBillFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];   // list from dashboard's categoryNames source
}
```

**Fields**:

| Field | Input type | Required | Validation |
|-------|-----------|----------|------------|
| Amount | `number`, step=0.01 | Yes | > 0 |
| Date | `date` | Yes | Defaults to today |
| Description | `text` | No | — |
| Category | `select` | No | Options from `categories` prop |

**Behaviour**:
- On mount/open: reset all fields; set date to today.
- On submit: validate amount > 0 (show inline error if not); set `submitting=true`; disable submit button; call `createBill()`; on success call `onSuccess()` then `onClose()`; on error show error message and re-enable button.
- Cancel button and Escape: call `onClose()` without saving.

---

## `AddIncomeForm`

**Purpose**: Form for creating a new income entry.

**Props**:
```ts
interface AddIncomeFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}
```

**Fields**:

| Field | Input type | Required | Validation |
|-------|-----------|----------|------------|
| Amount | `number`, step=0.01 | Yes | > 0 |
| Date | `date` | Yes | Defaults to today |
| Description | `text` | No | — |
| Source | `select` | No | SALARY / FREELANCE / INVESTMENT / RENTAL / GIFT / OTHER |

**Behaviour**: Same as `AddBillForm` but calls `createIncome()` and uses the income source list.

---

## `App` changes

Two new action buttons added to the dashboard header area:

- **"+ Add Expense"** → sets `billFormOpen = true`
- **"+ Add Income"** → sets `incomeFormOpen = true`

`onSuccess` handler for both forms: `setRefreshKey(k => k + 1)` which triggers `useDashboardData` to re-fetch.
