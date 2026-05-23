# Component Contracts: UI Category Management

## `useCategories` hook

**File**: `frontend/src/hooks/useCategories.ts`

```ts
function useCategories(type?: CategoryType): UseCategoriesResult
```

| Prop/Return | Type | Required | Description |
|---|---|---|---|
| `type` (param) | `CategoryType` | No | If provided, filters the fetched list |
| `categories` | `Category[]` | — | Fetched list; `[]` while loading |
| `loading` | `boolean` | — | True from first fetch until response arrives |
| `error` | `string \| null` | — | Human-readable error; null on success |
| `refresh()` | `() => void` | — | Increments internal key to re-fetch |

**Behaviour**:
- Fetches on mount and whenever `type` or internal refresh key changes.
- On HTTP error, sets `error` and keeps the previous `categories` list.

---

## `CategoryList` component

**File**: `frontend/src/components/CategoryList.tsx`

```ts
interface CategoryListProps {
  categories: Category[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: CategoryType | 'ALL';
  onTypeFilterChange: (filter: CategoryType | 'ALL') => void;
}
```

**Rendered output**:
- Filter buttons: ALL / EXPENSE / INCOME / BOTH.
- While `loading`: spinner / skeleton placeholder.
- On `error`: error message with retry prompt.
- When `categories` is empty (after load): empty state message + "Add your first category" CTA.
- For each category: colored circle (if `color` set), name, type badge.

---

## `AddCategoryForm` component

**File**: `frontend/src/components/AddCategoryForm.tsx`

```ts
interface AddCategoryFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (created: Category) => void;
  existingCategories: Category[];   // for parent category <select>
}
```

**Form fields**:

| Field | Input type | Required | Validation |
|---|---|---|---|
| Name | `text` | Yes | Non-empty after trim; server also enforces uniqueness |
| Type | `select` (EXPENSE / INCOME / BOTH) | Yes | Must select a value |
| Color | `color` | No | Native color picker; omitted if unchanged from default |
| Parent category | `select` (from `existingCategories`) | No | Optional; top-level option = "None" |

**Behaviour**:
- Submit button disabled while `submitting`.
- Inline `nameError` shown below the Name field on empty submission.
- Inline `typeError` shown below the Type field on empty submission.
- `serverError` shown as a banner at the top of the form on 409 / 5xx.
- On success: calls `onSuccess(created)`, resets form state, closes modal.
- On close (cancel or backdrop): resets form state without submitting.

---

## `CategoriesPage` view

**File**: `frontend/src/components/CategoriesPage.tsx`

```ts
interface CategoriesPageProps {
  onBack: () => void;   // returns to Dashboard view
}
```

**Responsibilities**:
- Owns `useCategories()` hook instance.
- Owns `AddCategoryForm` open/close state.
- Renders page header with title ("Categories") and "+ Add Category" button.
- Renders `CategoryList` with categories, loading, error, and filter state.
- On `AddCategoryForm.onSuccess`: appends returned `Category` to local list (no re-fetch needed).

---

## `App.tsx` changes

- Add `view: 'dashboard' | 'categories'` state (default `'dashboard'`).
- Add "Categories" navigation button in `dashboard-header`.
- Conditionally render `CategoriesPage` when `view === 'categories'`, `App` dashboard content otherwise.
