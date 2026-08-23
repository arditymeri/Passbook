# Component Contracts: Accounts Page

## `useAccounts` hook

**File**: `frontend/src/hooks/useAccounts.ts` (new — mirrors `useCategories.ts`)

```ts
function useAccounts(type?: AccountType): UseAccountsResult
```

| Prop/Return | Type | Required | Description |
|---|---|---|---|
| `type` (param) | `AccountType` | No | If provided, filters the fetched list client-side |
| `accounts` | `Account[]` | — | Fetched list; `[]` while loading |
| `loading` | `boolean` | — | True from first fetch until response arrives |
| `error` | `string \| null` | — | Human-readable error; null on success |
| `refresh()` | `() => void` | — | Increments internal key to re-fetch |

**Behaviour**:
- Fetches `GET /api/v1/accounts` on mount and whenever `type` or the internal refresh key changes.
- `Account.balance` returned by the API is already the derived current balance — the hook does no
  client-side calculation.
- On HTTP error, sets `error` and keeps the previous `accounts` list (same as `useCategories`).

---

## `AccountList` component

**File**: `frontend/src/components/AccountList.tsx` (new — mirrors `CategoryList.tsx`)

```ts
interface AccountListProps {
  accounts: Account[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: AccountType | 'ALL';
  onTypeFilterChange: (filter: AccountType | 'ALL') => void;
  onAddClick: () => void;
}
```

**Rendered output**:
- `ToggleButtonGroup` filter: `ALL` / `CHECKING` / `SAVINGS` / `CREDIT_CARD` / `CASH` / `INVESTMENT`.
- "Add Account" `Button variant="contained" startIcon={<AddIcon />}`.
- While `loading`: `Skeleton` placeholders (same shape as `CategoryList`).
- On `error`: `Alert severity="error"`.
- When the (filtered) list is empty after load: `Typography` "No accounts yet." + "+ Add your
  first account" CTA `Button`.
- For each account: `ListItem` with `ListItemText primary={name} secondary={`${type} · ${institution ?? '—'}`}`,
  and a balance `Typography` on the trailing side formatted with
  `new Intl.NumberFormat('de-AT', { style: 'currency', currency: account.defaultCurrency })`.
  Balance `Typography` uses `sx={{ color: balance < 0 ? 'error.main' : 'text.primary' }}`.

---

## `AddAccountForm` component

**File**: `frontend/src/components/AddAccountForm.tsx` (new — mirrors `AddCategoryForm.tsx`)

```ts
interface AddAccountFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (created: Account) => void;
}
```

**Fields**:
- `name` (`TextField`, required, `helperText` error on blank)
- `type` (`Select`+`MenuItem`, required: CHECKING/SAVINGS/CREDIT_CARD/CASH/INVESTMENT, `helperText` error if unselected)
- `defaultCurrency` (`TextField`, required — free-text ISO 4217 code, e.g. "EUR"; also seeds the
  single-element `currencies` array sent to the API, matching this feature's scope: one currency
  per account at creation, matching the account-model's "at least one currency" minimum without
  building a multi-currency picker UI)
- `balance` (`TextField type="text" inputMode="decimal"`, optional, defaults to empty → omitted
  from the request, matching the `AddBillForm`/`AddIncomeForm` string-state amount pattern —
  Constitution Principle IV)
- `institution` (`TextField`, optional)
- Submit: `Button variant="contained"`; Cancel: `Button variant="outlined"`
- Server errors (e.g. duplicate name → 409): `Alert severity="error"`, matching `AddCategoryForm`'s
  409-handling pattern

**Behaviour**: identical submit/reset/error lifecycle to `AddCategoryForm` — validates required
fields client-side before calling `createAccount`, shows a friendly message on 409 ("An account
with this name already exists"), generic message otherwise.

---

## `AccountsPage` component

**File**: `frontend/src/components/AccountsPage.tsx` (new — mirrors `CategoriesPage.tsx`)

```ts
interface AccountsPageProps {
  onBack: () => void;
}
```

Composes `useAccounts` + `AccountList` + `AddAccountForm` exactly as `CategoriesPage` composes
`useCategories` + `CategoryList` + `AddCategoryForm`, including the "auto-open the create form
when the list is empty on first load" behaviour.

---

## `App.tsx` changes

- `view` state gains a third value: `'dashboard' | 'categories' | 'accounts'`.
- Toolbar gains an "Accounts" `Button variant="outlined"` next to the existing "Categories" button.
- Dashboard's `useDashboardData` gains an `accounts: Account[]` field (fetched once, like
  `categories`) so it can be passed down to `AddBillForm`/`AddIncomeForm` without those forms
  independently re-fetching.

---

## `AddBillForm` changes

```ts
interface AddBillFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];
  accounts: Account[];   // NEW
}
```

- New `FormControl` + `Select` "Account" field, positioned after the existing Category `Select`,
  with a `<MenuItem value="">No account</MenuItem>` default — same optional-select pattern as the
  existing Category field on this same form.
- `CreateBillRequest` gains `accountId?: string`, sent as `accountId || undefined` (same
  `undefined`-when-empty convention already used for `categoryId`).

## `AddIncomeForm` changes

```ts
interface AddIncomeFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  accounts: Account[];   // NEW
}
```

- New `FormControl` + `Select` "Account" field, positioned after the existing Source `Select`,
  with a `<MenuItem value="">No account</MenuItem>` default.
- `CreateIncomeRequest` gains `accountId?: string`, sent as `accountId || undefined`.

---

## Type additions (`frontend/src/types/index.ts`)

```ts
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'CASH' | 'INVESTMENT';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currencies: string[];
  defaultCurrency: string;
  balance: number;        // derived current balance, per data-model.md
  institution?: string;
}

export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  currencies: string[];
  defaultCurrency: string;
  balance?: number;        // starting balance
  institution?: string;
}

// Existing interfaces gain one optional field each:
export interface CreateBillRequest {
  amount: number;
  time: string;
  description?: string;
  categoryId?: string;
  accountId?: string;      // NEW
}

export interface CreateIncomeRequest {
  amount: number;
  time: string;
  description?: string;
  source?: IncomeSource;
  accountId?: string;      // NEW
}
```

## API client additions (`frontend/src/api/client.ts`)

```ts
export async function fetchAccounts(): Promise<Account[]> {
  const data = await request<{ accounts: Account[] }>('/api/v1/accounts');
  return data.accounts ?? [];
}

export async function createAccount(req: CreateAccountRequest): Promise<Account> {
  return postAndReturn<Account>('/api/v1/accounts', req);
}
```

Both mirror the existing `fetchCategories`/`createCategory` pair exactly (same wrapped-list
response shape for GET, same direct-object response for POST-and-return).
