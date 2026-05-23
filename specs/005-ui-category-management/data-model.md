# Data Model: UI Category Management

## Type Changes in `frontend/src/types/index.ts`

### `CategoryType` (new)

```ts
export type CategoryType = 'EXPENSE' | 'INCOME' | 'BOTH';
```

### `Category` (updated — additive)

```ts
// Before: { id: string; name: string }
// After:
export interface Category {
  id: string;
  name: string;
  type: CategoryType;
  color?: string;
  parentCategoryId?: string;
}
```

**Validation rules**:
- `id` — UUID string, set by backend; never empty in a response.
- `name` — non-empty string; uniqueness enforced server-side (409 on conflict).
- `type` — one of `EXPENSE | INCOME | BOTH`; required.
- `color` — optional hex string (`#RRGGBB`). Absent if not set on the backend.
- `parentCategoryId` — optional UUID. Points to another `Category.id`. Absent if no parent.

### `CreateCategoryRequest` (new)

```ts
export interface CreateCategoryRequest {
  name: string;
  type: CategoryType;
  color?: string;
  parentCategoryId?: string;
}
```

**Validation rules (client-side)**:
- `name` — required; trim whitespace; reject if empty after trim.
- `type` — required; must be one of the three enum values.
- `color` — optional; if present must be a valid hex color string.
- `parentCategoryId` — optional; if present must be a UUID from the existing categories list.

---

## State Shape

### `useCategories` hook

```ts
interface UseCategoriesResult {
  categories: Category[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}
```

### `AddCategoryForm` local state

```ts
interface AddCategoryFormState {
  name: string;
  type: CategoryType | '';
  color: string;           // empty string = not set
  parentCategoryId: string; // empty string = no parent
  submitting: boolean;
  nameError: string | null;
  typeError: string | null;
  serverError: string | null;
}
```

---

## API Mapping

| Frontend function | HTTP | Backend endpoint | Request type | Response type |
|---|---|---|---|---|
| `fetchCategories(type?)` | GET | `/api/v1/categories?type=` | — | `{ categories: Category[] }` |
| `createCategory(req)` | POST | `/api/v1/categories` | `CreateCategoryRequest` | `Category` (201) |

**Notes**:
- `fetchCategories` currently reads a raw array; it needs to be updated to unwrap `{ categories: [...] }` response shape (matching the actual `CategoryListResponse` from the backend).
- `createCategory` uses a new `postAndReturn<T>()` helper that parses and returns the response body.
