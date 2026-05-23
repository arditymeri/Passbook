# Research: UI Category Management

## Existing Frontend Inventory

### What already exists

| Artifact | Location | Notes |
|---|---|---|
| `fetchCategories()` | `frontend/src/api/client.ts:42` | Fetches `GET /api/v1/categories`; returns raw array |
| `Category` type | `frontend/src/types/index.ts:40` | Only has `id` and `name` — missing `type`, `color`, `parentCategoryId` |
| `Modal` component | `frontend/src/components/Modal.tsx` | Reusable; used by `AddBillForm` and `AddIncomeForm` |
| Form pattern | `frontend/src/components/AddBillForm.tsx` | `useState` + `Modal` + inline error — no form library |
| API client | `frontend/src/api/client.ts` | Plain `fetch`; `post()` helper returns `void` |
| No router | `frontend/src/App.tsx` | Single-page; views are toggled via `useState` in `App.tsx` |
| No UI library | `frontend/package.json` | Vanilla CSS + React; no component framework |

### What is missing

- `createCategory()` API function
- `CategoryType` union type and full `Category` type fields
- `CreateCategoryRequest` type
- `useCategories` hook (load + refresh lifecycle)
- `CategoriesPage` view (list + filter + add button)
- `CategoryList` component (with type badge and color swatch)
- `AddCategoryForm` modal component
- Navigation between Dashboard and Categories views

---

## Decision Log

### D-001: Navigation approach

**Decision**: Add a simple tab/link toggle (`view` state in `App.tsx`) between "Dashboard" and "Categories" — no react-router.

**Rationale**: The app has no existing router. Adding react-router for a single additional view adds routing config, URL management, and a new dependency that the project has not adopted. A `useState`-based view switch is consistent with the current pattern and is trivially reversible if routing is added later.

**Alternatives considered**:
- `react-router-dom` — over-engineered for one additional route; no existing usage in the project.

---

### D-002: Category list rendering

**Decision**: Render categories in a simple card/table list showing name, a type badge, and a colored circle if a color is set. No virtual list or pagination for v1.

**Rationale**: Personal finance app; expected category count is < 50. A flat list is sufficient. Premature optimization (virtualization, pagination) is prohibited by the Constitution.

**Alternatives considered**:
- Paginated table — unnecessary for expected data volume.

---

### D-003: Color input

**Decision**: Use `<input type="color">` (native browser color picker) for the color field, storing the value as a hex string (e.g., `#4CAF50`).

**Rationale**: Zero added dependencies; works in all modern desktop browsers; produces a valid hex string that the backend already accepts.

**Alternatives considered**:
- Text input with hex validation — worse UX, requires manual format enforcement.
- Third-party color picker library — adds a dependency without proportional benefit.

---

### D-004: Parent category selection

**Decision**: Render a `<select>` populated from the same `useCategories` hook to pick an optional parent. No tree-view or nested display for v1.

**Rationale**: The spec limits nesting to one level. A flat `<select>` is sufficient and consistent with the category dropdown in `AddBillForm`.

---

### D-005: `post()` return type for createCategory

**Decision**: Create a new `postAndReturn<T>()` helper in `client.ts` that returns the parsed JSON body. `createCategory()` returns `Promise<Category>` so the caller can append the new item to state optimistically.

**Rationale**: The existing `post()` helper discards the response body (`returns void`). `POST /api/v1/categories` responds with the created `CategoryResponse` (201). Capturing it avoids a second fetch to refresh the list.

---

### D-006: Constitution compliance

- **Principles I–IV**: Not applicable — categories are reference/metadata, not financial transactions or monetary values.
- **Principle V (Audit Trail)**: No additional instrumentation required in the frontend beyond standard HTTP error logging already present in the API client.
- **Principle VI (Test-First)**: Component tests should be written for `AddCategoryForm` (validation logic) and `useCategories` (loading/error states). This is a frontend feature — Vitest + React Testing Library is the appropriate toolchain.
- **Principle VII (API Contract Stability)**: No new endpoints. Consuming existing stable contracts (`GET /api/v1/categories`, `POST /api/v1/categories`). The `fetchCategories` function needs to be updated to return the richer `Category` shape (additive change — non-breaking).
- **Principle VIII (Hexagonal Architecture)**: Not applicable — this is a purely frontend change. No backend domain layer is touched.
