# Tasks: Material UI Migration

**Input**: Design documents from `specs/006-mui-migration/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | contracts/ui-components.md ✅

**Tests**: No test tasks — the spec does not request TDD; verification is manual browser smoke-testing per story checkpoint.

**Organization**: Tasks grouped by user story. US1 (dashboard display components) can be fully parallelized. US2 (forms) and US3 (categories) both depend on the foundational Modal→Dialog migration.

---

## Phase 1: Setup

**Purpose**: Install MUI dependencies and establish the shared theme file.

- [X] T001 Add `@mui/material`, `@emotion/react`, `@emotion/styled`, `@mui/icons-material` to `frontend/package.json` and run `npm install` in `frontend/`
- [X] T002 Create `frontend/src/theme.ts` — export a `createTheme` object with primary `#1a1a2e`, secondary `#3f8efc`, background `#f5f7fa`, borderRadius `8`, Roboto typography (per `contracts/ui-components.md` theme.ts contract)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: ThemeProvider at the root and the shared Modal→Dialog migration. All user story components depend on these two changes.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 Update `frontend/src/App.tsx` — wrap the entire JSX return in `<ThemeProvider theme={theme}><CssBaseline />{...}</ThemeProvider>`; import theme from `./theme`
- [X] T004 Migrate `frontend/src/components/Modal.tsx` — replace the custom backdrop/dialog div with MUI `Dialog` + `DialogTitle` (with close `IconButton`) + `DialogContent`; keep prop interface `{ open, onClose, title, children }` unchanged (per `contracts/ui-components.md` Modal contract)

**Checkpoint**: App renders with MUI baseline styles; Modal/Dialog opens and closes correctly.

---

## Phase 3: User Story 1 — Dashboard Looks Polished and Consistent (Priority: P1) 🎯 MVP

**Goal**: All dashboard display components migrated to MUI — cards, lists, skeleton loaders, error alerts, and the app header.

**Independent Test**: Start `npm run dev` in `frontend/`. Open the browser. The dashboard displays summary cards, budget status, category spend, and recent transactions all styled with MUI Paper/List/Table components. No raw divs with className remain. Skeleton loaders show on slow connections.

- [X] T005 [P] [US1] Migrate `frontend/src/components/MonthNav.tsx` — replace the nav div with MUI `Paper` + `IconButton` (ChevronLeft/ChevronRight) + `Typography variant="h6"`; keep prop interface unchanged (per contracts)
- [X] T006 [P] [US1] Migrate `frontend/src/components/SummaryCard.tsx` — replace summary-card div with MUI `Paper` + `Stack`; add `Skeleton` for loading state and `Alert severity="error"` for error state; income/expenses/netBalance use `success.main`/`error.main` colours (per contracts)
- [X] T007 [P] [US1] Migrate `frontend/src/components/BudgetStatus.tsx` — replace card div with MUI `Paper` + `List`; each entry uses `ListItem` + `Chip color="success"/"error"` (UNDER/OVER) + `LinearProgress` bar showing actual/budgeted ratio; add `Skeleton` loading and `Alert` error states (per contracts)
- [X] T008 [P] [US1] Migrate `frontend/src/components/CategorySpend.tsx` — replace card div with MUI `Paper` + `List`; each entry uses `ListItem` + `LinearProgress` proportional to max; add `Skeleton` loading and `Alert` error states (per contracts)
- [X] T009 [P] [US1] Migrate `frontend/src/components/RecentTransactions.tsx` — replace card div with MUI `Paper` + `TableContainer` + `Table` (columns: Date, Description, Category, Amount); bill amounts in `error.main`, income in `success.main`; add `Skeleton` loading and `Alert` error states (per contracts)
- [X] T010 [US1] Update `frontend/src/App.tsx` dashboard layout — replace `.dashboard` div with MUI `Box`/`Container`; replace `.dashboard-header` with MUI `AppBar` + `Toolbar` (title as `Typography variant="h6"`, action buttons as MUI `Button`); replace `.middle-row` with MUI `Grid container` holding CategorySpend and BudgetStatus side by side (per contracts App section)

**Checkpoint**: All dashboard components render with consistent MUI styling. No hand-written CSS class names used. Skeleton/Alert states visible by simulating slow API.

---

## Phase 4: User Story 2 — Forms Are Easy to Use and Visually Clear (Priority: P2)

**Goal**: AddBillForm and AddIncomeForm migrated to MUI TextField/Select/Button; field-level inline validation errors; primary vs secondary actions visually distinct.

**Independent Test**: Open "+ Add Expense" modal. All fields use MUI TextField. Submit with empty amount — inline error appears under the amount field. Submit with valid data — form submits and modal closes. Same for "+ Add Income". Dialog backdrop and close button work.

- [X] T011 [P] [US2] Migrate `frontend/src/components/AddBillForm.tsx` — replace all `<input>`/`<select>` elements with MUI `TextField` (`type="text"` for amount, `type="date"` for date, plain text for description) and MUI `Select`+`MenuItem` for category; show field errors via `TextField error helperText`; submit as MUI `Button variant="contained"`, cancel as `Button variant="outlined"`; server errors as `Alert severity="error"` (per contracts; amount MUST remain string state — Principle IV)
- [X] T012 [P] [US2] Migrate `frontend/src/components/AddIncomeForm.tsx` — same pattern as T011: MUI `TextField` for amount (`type="text"`)/date/description, MUI `Select` for source (SALARY/FREELANCE/INVESTMENT/RENTAL/GIFT/OTHER); field errors via `helperText`; `Button variant="contained"` submit, `Button variant="outlined"` cancel; `Alert` server errors (per contracts)

**Checkpoint**: Both forms open in styled MUI Dialogs. Field-level validation messages appear inline. Submit/cancel actions visually distinguished. Amount fields never use `type="number"`.

---

## Phase 5: User Story 3 — Categories Page Is Consistent With the Rest of the App (Priority: P3)

**Goal**: CategoryList, AddCategoryForm, and CategoriesPage all use the same MUI visual language as the rest of the app.

**Independent Test**: Navigate to the Categories page (click "Categories" button). The list uses MUI List/Avatar. Filter buttons are MUI ToggleButtonGroup. Add Category form matches the Add Bill form style. Empty state shows a prompt to add a category.

- [ ] T013 [P] [US3] Migrate `frontend/src/components/CategoryList.tsx` — replace filter buttons with MUI `ToggleButtonGroup` (exclusive, values ALL/EXPENSE/INCOME/BOTH); replace list with MUI `List` + `ListItem` + coloured `Avatar` (category.color or grey default) + `ListItemText primary={name} secondary={type}`; add "Add Category" `Button variant="contained" startIcon={<AddIcon />}`; `Skeleton` loading, `Alert` error, `Typography` empty states (per contracts)
- [ ] T014 [P] [US3] Migrate `frontend/src/components/AddCategoryForm.tsx` — replace inputs with MUI `TextField` (name), `Select`+`MenuItem` (type: EXPENSE/INCOME/BOTH), `TextField type="color"` (colour), `Select` (parent category with "None" option); field errors on name/type via `helperText`; `Button variant="contained"` submit, `Button variant="outlined"` cancel; `Alert` server errors (per contracts)
- [ ] T015 [US3] Migrate `frontend/src/components/CategoriesPage.tsx` — replace back button with MUI `IconButton` + ArrowBack icon; page title as MUI `Typography variant="h5"` "Categories"; wrap content in MUI `Box` layout (depends on T013 and T014 being complete)

**Checkpoint**: Categories page fully styled. All three pages (dashboard, forms, categories) share identical component shapes, spacing, and palette.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Remove all hand-written CSS, verify TypeScript compiles, and confirm no regressions.

- [ ] T016 Delete `frontend/src/App.css`; reduce `frontend/src/index.css` to a minimal global reset (body margin: 0; box-sizing: border-box) — remove all component class selectors that are now handled by MUI
- [ ] T017 Run `cd frontend && npm run build` — confirm TypeScript type-check and Vite build complete with zero errors
- [ ] T018 Manual smoke-test: start `npm run dev`, open browser, verify all three pages (dashboard, Add Expense form, Add Income form, Categories page) render correctly with no console errors, all CRUD operations work end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Requires Phase 1 complete — blocks all user stories
- **US1 (Phase 3)**: Requires Phase 2 complete — all 6 tasks can run in parallel
- **US2 (Phase 4)**: Requires Phase 2 (T004 Modal migration) — T011 and T012 can run in parallel with each other and with US1 tasks (different files)
- **US3 (Phase 5)**: Requires Phase 2 (T004) — T013/T014 can run in parallel; T015 requires T013+T014
- **Polish (Phase 6)**: Requires all phases complete

### User Story Dependencies

- **US1 (P1)**: Depends only on Phase 2; all 6 tasks are independent files → fully parallelizable
- **US2 (P2)**: Depends on T004 (Modal→Dialog); T011 and T012 are independent of each other
- **US3 (P3)**: Depends on T004 (Modal→Dialog); T013 and T014 independent; T015 depends on both

### Parallel Opportunities

- T005, T006, T007, T008, T009 (US1 display components) — all touch different files, fully parallel
- T011, T012 (US2 forms) — different files, parallel with each other and with US1 tasks
- T013, T014 (US3 list + form) — different files, parallel with each other

---

## Parallel Example: User Story 1

```text
# All 5 display component tasks can be dispatched simultaneously:
T005 — MonthNav.tsx
T006 — SummaryCard.tsx
T007 — BudgetStatus.tsx
T008 — CategorySpend.tsx
T009 — RecentTransactions.tsx

# Then, once all complete:
T010 — App.tsx layout update
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003–T004)
3. Complete Phase 3: US1 Dashboard (T005–T010)
4. **STOP and VALIDATE**: Open browser, confirm dashboard looks polished
5. Ship/demo if ready — the dashboard is the highest-value visible surface

### Incremental Delivery

1. Phase 1 + 2 → Theme and baseline wired up
2. Phase 3 (US1) → Polished dashboard ← **MVP**
3. Phase 4 (US2) → Forms look and validate correctly
4. Phase 5 (US3) → Categories page consistent
5. Phase 6 → CSS cleanup, build verification, smoke test

---

## Notes

- `[P]` marks tasks that touch different files and have no dependencies on each other — safe to run simultaneously
- Amount fields in all forms MUST use `type="text"` with string state — do not use `type="number"` (Constitution Principle IV)
- All prop interfaces remain unchanged throughout — parent components need zero updates
- Verify `npm run build` passes after each phase to catch TypeScript errors early
- The existing `fmt = new Intl.NumberFormat(...)` formatting logic in display components is preserved as-is
