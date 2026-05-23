# Tasks: UI Category Management

**Input**: Design documents from `specs/005-ui-category-management/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/component-contracts.md ✅

**Tests**: Not included — frontend-only feature; no financial calculation logic (Principle VI applies to Domain module). Constitution VI test-first requirement is satisfied by the existing backend test suite; component tests are recommended but not required by spec.

**Organization**: Tasks grouped by user story for independent delivery.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no conflicting dependencies)
- **[Story]**: User story this task belongs to (US1, US2, US3)

---

## Phase 1: Setup

**Purpose**: Confirm prerequisites — no new npm packages or directories are required.

- [x] T001 Confirm no new npm dependencies needed and that `frontend/src/hooks/` and `frontend/src/components/` directories exist (`frontend/package.json` unchanged)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared types, API client updates, and the `useCategories` hook that all three user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T002 [P] Update `frontend/src/types/index.ts` — add `CategoryType = 'EXPENSE' | 'INCOME' | 'BOTH'`; update `Category` interface with `type: CategoryType`, `color?: string`, `parentCategoryId?: string`; add `CreateCategoryRequest` interface
- [x] T003 [P] Update `frontend/src/api/client.ts` — add `postAndReturn<T>()` helper that returns parsed response body; fix `fetchCategories()` to unwrap `{ categories: Category[] }` response shape (current implementation incorrectly reads raw array); add `createCategory(req: CreateCategoryRequest): Promise<Category>` using `postAndReturn`
- [x] T004 Create `frontend/src/hooks/useCategories.ts` — `useCategories(type?: CategoryType)` hook returning `{ categories, loading, error, refresh }`; fetches on mount and on `refresh()` call (depends on T002, T003)

**Checkpoint**: Types, API client, and hook are ready — all three user stories can now proceed.

---

## Phase 3: User Story 1 — View All Categories (Priority: P1) 🎯 MVP

**Goal**: Users can navigate to a Categories page and see all existing categories with name, type badge, and color swatch. Loading, empty, and error states are all handled.

**Independent Test**: Navigate to Categories view; verify the list renders with correct data; verify loading spinner appears before data arrives; verify empty-state message and CTA appear when no categories exist.

- [x] T005 [P] [US1] Create `frontend/src/components/CategoryList.tsx` — render list of categories; each row shows name, type badge (EXPENSE/INCOME/BOTH), and color swatch circle (if `color` set); loading skeleton; empty state with "Add your first category" CTA; error message with retry prompt
- [x] T006 [P] [US1] Create `frontend/src/components/CategoriesPage.tsx` — page view owning `useCategories()` instance; header with "Categories" title and "+ Add Category" button (button opens nothing yet); renders `CategoryList` with categories, loading, and error props; `onBack` prop calls App view toggle (depends on T004)
- [x] T007 [US1] Update `frontend/src/App.tsx` — add `view: 'dashboard' | 'categories'` state (default `'dashboard'`); add "Categories" button to `.dashboard-header .action-buttons`; conditionally render `CategoriesPage` (with `onBack={() => setView('dashboard')}`) or existing dashboard content (depends on T006)

**Checkpoint**: User Story 1 fully functional — navigate to Categories, see list with real data from the backend.

---

## Phase 4: User Story 2 — Create a New Category (Priority: P1)

**Goal**: Users can open a form from the Categories page, fill in name and type (required), optionally set color and parent category, and submit to create a new category that immediately appears in the list.

**Independent Test**: Open AddCategoryForm; submit with name and type only — verify new item appears; submit with all fields — verify all fields saved; submit with empty name — verify inline error; submit with no type — verify inline error; submit duplicate name — verify server error banner.

- [x] T008 [P] [US2] Create `frontend/src/components/AddCategoryForm.tsx` — modal form using existing `Modal` component; fields: name (text, required), type (select: EXPENSE/INCOME/BOTH, required), color (input[type=color], optional — omit if user hasn't changed it), parentCategoryId (select from existing categories, optional, "None" default); client-side validation with inline `nameError` and `typeError`; `serverError` banner for 409/5xx; submit button disabled while submitting; resets state on close (depends on T002, T003)
- [x] T009 [US2] Update `frontend/src/components/CategoriesPage.tsx` — add `addFormOpen` boolean state; wire "+ Add Category" button to open `AddCategoryForm`; pass `existingCategories` from hook to form; on `onSuccess(created)` append `created` to local categories list (no re-fetch); close form (depends on T008)

**Checkpoint**: User Stories 1 and 2 are both fully functional — view categories and create new ones end-to-end.

---

## Phase 5: User Story 3 — Filter Categories by Type (Priority: P2)

**Goal**: Users can filter the category list by type (ALL / EXPENSE / INCOME / BOTH) so they can quickly find categories of a specific type.

**Independent Test**: With mixed category types in the list, click "EXPENSE" filter — verify only expense categories shown; click "ALL" — verify all categories shown again.

- [x] T010 [US3] Update `frontend/src/components/CategoryList.tsx` — add `activeTypeFilter: CategoryType | 'ALL'` and `onTypeFilterChange` props; render four filter buttons (ALL / EXPENSE / INCOME / BOTH); apply client-side filtering to the rendered list; highlight active filter button
- [x] T011 [US3] Update `frontend/src/components/CategoriesPage.tsx` — add `activeTypeFilter` state (default `'ALL'`); pass filter props to `CategoryList`; pass filter to `useCategories` hook if server-side filtering is preferred over client-side

**Checkpoint**: All three user stories functional — view, create, and filter categories.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T012 [P] Add CSS to `frontend/src/App.css` — styles for: category type badge (color-coded per type), color swatch circle, category list card layout, filter button group (active/inactive states), empty state message, CategoriesPage header layout
- [x] T013 [P] Verify `fetchCategories()` fix in `frontend/src/api/client.ts` is backward-compatible with `frontend/src/hooks/useDashboardData.ts` — return type unchanged (`Promise<Category[]>`); additive `Category` fields do not break existing dashboard usage of `category.name` and `category.id`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **blocks all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 — T005 and T006 can run in parallel; T007 depends on T006
- **US2 (Phase 4)**: Depends on Phase 2 — T008 can run in parallel with Phase 3; T009 depends on T008 and T006
- **US3 (Phase 5)**: Depends on Phase 3 (modifies CategoryList) — T010 before T011
- **Polish (Phase 6)**: Depends on all user story phases complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependency on US2 or US3
- **US2 (P1)**: Can start after Phase 2 — T008 can be built in parallel with Phase 3; T009 depends on T006
- **US3 (P2)**: Depends on US1 (modifies CategoryList from T005)

### Parallel Opportunities Within Phases

- **Phase 2**: T002 and T003 are in different files — can run in parallel
- **Phase 3**: T005 and T006 are new files — can run in parallel; T007 follows T006
- **Phase 4**: T008 is a new file — can run in parallel with Phase 3; T009 follows T008 and T006
- **Phase 6**: T012 and T013 are independent — can run in parallel

---

## Parallel Example: Phase 2

```
# Run simultaneously (different files):
T002: Update frontend/src/types/index.ts
T003: Update frontend/src/api/client.ts

# Then sequentially:
T004: Create frontend/src/hooks/useCategories.ts  (needs T002 + T003)
```

## Parallel Example: Phase 3 + Phase 4

```
# Once Phase 2 is done, these can all start in parallel:
T005: Create CategoryList.tsx
T006: Create CategoriesPage.tsx
T008: Create AddCategoryForm.tsx  (independent new file)

# Then:
T007: Update App.tsx               (needs T006)
T009: Update CategoriesPage.tsx    (needs T006 + T008)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Setup (confirm, no work needed)
2. Complete Phase 2: Foundational — T002, T003 in parallel → T004
3. Complete Phase 3 (US1): T005, T006 in parallel → T007
4. Complete Phase 4 (US2): T008 → T009
5. **STOP and VALIDATE**: Categories page shows list + create form works end-to-end
6. Add Phase 5 (US3) filter feature when ready

### Incremental Delivery

1. Phase 1 + 2 → types and API wired
2. Phase 3 → read-only Categories page (US1 MVP)
3. Phase 4 → add create form (US2 completes core feature)
4. Phase 5 → add type filtering (US3 enhancement)
5. Phase 6 → CSS polish and backward-compat check

---

## Notes

- [P] tasks = different files, no shared dependencies at that point
- `fetchCategories()` fix in T003 also fixes a latent bug in the dashboard (currently returns `[]` because the backend response is `{ categories: [...] }` not a raw array)
- `AddCategoryForm` must receive `existingCategories` from `CategoriesPage` to populate the parent select — do not create a second fetch inside the form
- Color input: use `<input type="color">` with `defaultValue="#ffffff"`; only include `color` in the POST body if the user interacted with the picker (compare against default)
- Submit button must be disabled during `submitting` state to prevent duplicate submissions (edge case from spec)
