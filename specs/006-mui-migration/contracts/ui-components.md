# UI Component Contracts: MUI Migration

**Feature**: 006-mui-migration | **Date**: 2026-05-25

These contracts define the external interface (props) and visible behaviour of each component after migration. Internal implementation details are not specified — only the contract that parent components and users depend on. All existing prop interfaces remain unchanged.

---

## App (root)

**File**: `frontend/src/App.tsx`

**Changes**: Wraps the entire tree in `ThemeProvider` + `CssBaseline`. Layout switches from `className="dashboard"` to MUI `Box`/`Container`. Header uses MUI `AppBar` + `Toolbar`. Action buttons use MUI `Button`.

**Contract**:
- Renders a top app bar with the title "MyFinance Dashboard" and three action buttons: "Categories", "+ Add Expense", "+ Add Income".
- Renders `MonthNav`, `SummaryCard`, a two-column middle row (`CategorySpend` + `BudgetStatus`), `RecentTransactions`, `AddBillForm`, `AddIncomeForm`.
- "Categories" button navigates to `CategoriesPage`; the other two buttons open their respective modals.
- `CssBaseline` resets browser default margins.

---

## theme.ts (new file)

**File**: `frontend/src/theme.ts`

**Contract**:
- Exports a single MUI `Theme` object created via `createTheme`.
- Primary colour: `#1a1a2e` (matches existing dark navy).
- Secondary colour: `#3f8efc` (accent blue for actions).
- Background default: `#f5f7fa` (matches existing background).
- Typography: `font-family: 'Roboto', system-ui, sans-serif`.
- Shape border radius: `8px`.

---

## SummaryCard

**File**: `frontend/src/components/SummaryCard.tsx`

**Props** (unchanged):
```ts
{ summary: MonthlySummary | null; loading: boolean; error: string | null }
```

**Contract**:
- Loading state: shows MUI `Skeleton` (3 rows, matching the 3 summary items).
- Error state: shows MUI `Alert severity="error"` with the error message.
- Data state: MUI `Paper` containing three `Stack` rows: Income (green), Expenses (red), Net Balance (green if ≥ 0, red if < 0).
- Amounts formatted as `de-AT` locale EUR currency strings.

---

## MonthNav

**File**: `frontend/src/components/MonthNav.tsx`

**Props** (unchanged):
```ts
{ year: number; month: number; onPrevious: () => void; onNext: () => void }
```

**Contract**:
- Renders an MUI `Paper` bar with left `IconButton` (ChevronLeft icon), centred month/year label as `Typography variant="h6"`, right `IconButton` (ChevronRight icon).
- Clicking left triggers `onPrevious`; clicking right triggers `onNext`.
- `aria-label` values preserved: "Previous month", "Next month".

---

## BudgetStatus

**File**: `frontend/src/components/BudgetStatus.tsx`

**Props** (unchanged):
```ts
{ entries: BudgetStatusEntry[]; categoryNames: CategoryNameMap; loading: boolean; error: string | null }
```

**Contract**:
- Loading: MUI `Skeleton` list (3 rows).
- Error: MUI `Alert severity="error"`.
- Empty: MUI `Typography color="text.secondary"` with "No budget data for this month."
- Data: MUI `Paper` + `List`. Each `ListItem` shows category name + MUI `Chip` with label "UNDER" or "OVER", `color="success"` or `color="error"` respectively. A `LinearProgress` bar shows actual/budgeted ratio, capped at 100%.

---

## CategorySpend

**File**: `frontend/src/components/CategorySpend.tsx`

**Props** (unchanged):
```ts
{ spendingByCategory: Record<string, number>; categoryNames: CategoryNameMap; loading: boolean; error: string | null }
```

**Contract**:
- Loading: MUI `Skeleton` list (3 rows).
- Error: MUI `Alert severity="error"`.
- Empty: MUI `Typography color="text.secondary"` with "No spending data for this month."
- Data: MUI `Paper` + `List`. Each entry shows category name, amount formatted in EUR, and a `LinearProgress` bar proportional to the maximum category amount.

---

## RecentTransactions

**File**: `frontend/src/components/RecentTransactions.tsx`

**Props** (unchanged):
```ts
{ transactions: Transaction[]; categoryNames: CategoryNameMap; loading: boolean; error: string | null }
```

**Contract**:
- Loading: MUI `Skeleton` table (5 rows × 4 columns).
- Error: MUI `Alert severity="error"`.
- Empty: MUI `Typography color="text.secondary"` with "No transactions for this month."
- Data: MUI `TableContainer` + `Table` with columns: Date, Description, Category, Amount. Bills shown with amount in red (`error.main`), incomes in green (`success.main`).

---

## Modal (replaced by Dialog)

**File**: `frontend/src/components/Modal.tsx`

**Props** (unchanged):
```ts
{ open: boolean; onClose: () => void; title: string; children: React.ReactNode }
```

**Contract**:
- Renders MUI `Dialog` with `open` prop.
- `DialogTitle` contains `title` prop text + a close `IconButton` (Close icon) in the top-right corner.
- `DialogContent` renders `children`.
- Clicking the backdrop or pressing Escape calls `onClose`.
- Dialog `maxWidth="sm"` `fullWidth`.

---

## AddBillForm

**File**: `frontend/src/components/AddBillForm.tsx`

**Props** (unchanged):
```ts
{ open: boolean; onClose: () => void; onSuccess: () => void; categories: Category[] }
```

**Contract**:
- Rendered inside `Modal` (which uses MUI `Dialog`).
- Amount: MUI `TextField` `type="text"` `label="Amount (EUR)"` — string state, required.
- Date: MUI `TextField` `type="date"` `label="Date"` — required.
- Description: MUI `TextField` `label="Description"` — optional.
- Category: MUI `Select` + `MenuItem` list of categories — optional.
- Validation errors shown as `TextField` `error` + `helperText` on the relevant field.
- Submit button: MUI `Button variant="contained"` "Save Expense"; disabled while submitting.
- Cancel button: MUI `Button variant="outlined"` "Cancel".
- Server errors shown as MUI `Alert severity="error"` above the action buttons.

---

## AddIncomeForm

**File**: `frontend/src/components/AddIncomeForm.tsx`

**Props** (unchanged):
```ts
{ open: boolean; onClose: () => void; onSuccess: () => void }
```

**Contract**:
- Rendered inside `Modal` (MUI `Dialog`).
- Amount: MUI `TextField` `type="text"` `label="Amount (EUR)"` — string state, required.
- Date: MUI `TextField` `type="date"` `label="Date"` — required.
- Description: MUI `TextField` `label="Description"` — optional.
- Source: MUI `Select` with options SALARY, FREELANCE, INVESTMENT, RENTAL, GIFT, OTHER — optional.
- Validation errors as `TextField` `error` + `helperText`.
- Submit: MUI `Button variant="contained"` "Save Income"; Cancel: MUI `Button variant="outlined"`.
- Server errors: MUI `Alert severity="error"`.

---

## AddCategoryForm

**File**: `frontend/src/components/AddCategoryForm.tsx`

**Props** (unchanged):
```ts
{ open: boolean; onClose: () => void; onSuccess: (created: Category) => void; existingCategories: Category[] }
```

**Contract**:
- Rendered inside `Modal` (MUI `Dialog`).
- Name: MUI `TextField` `label="Name"` — required; error if blank.
- Type: MUI `Select` with options EXPENSE, INCOME, BOTH — required; error if blank.
- Color: MUI `TextField` `type="color"` `label="Colour"` — optional.
- Parent Category: MUI `Select` listing `existingCategories` — optional, includes a "None" option.
- Validation errors on Name and Type fields via `helperText`.
- Submit: MUI `Button variant="contained"` "Add Category"; Cancel: MUI `Button variant="outlined"`.
- Server errors: MUI `Alert severity="error"`.

---

## CategoryList

**File**: `frontend/src/components/CategoryList.tsx`

**Props** (unchanged):
```ts
{
  categories: Category[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: CategoryType | 'ALL';
  onTypeFilterChange: (filter: CategoryType | 'ALL') => void;
  onAddClick: () => void;
}
```

**Contract**:
- Filter bar: MUI `ToggleButtonGroup` (exclusive) with values ALL, EXPENSE, INCOME, BOTH.
- Loading: MUI `Skeleton` list (5 rows).
- Error: MUI `Alert severity="error"`.
- Empty (after filtering): MUI `Typography color="text.secondary"` "No categories match this filter."
- Data: MUI `List` where each `ListItem` shows a coloured `Avatar` (category colour or default grey), category name as `ListItemText primary`, type as `ListItemText secondary`.
- "Add Category" button: MUI `Button variant="contained" startIcon={<AddIcon />}` at the top-right of the page.

---

## CategoriesPage

**File**: `frontend/src/components/CategoriesPage.tsx`

**Props** (unchanged):
```ts
{ onBack: () => void }
```

**Contract**:
- Back button: MUI `IconButton` with ChevronLeft or ArrowBack icon at the top-left.
- Page title: MUI `Typography variant="h5"` "Categories".
- Renders `CategoryList` and `AddCategoryForm` modal.
- Empty state (no categories): `AddCategoryForm` auto-opens (existing behaviour preserved).
