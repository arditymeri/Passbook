---
description: "Task list for Frontend Data Entry"
---

# Tasks: Frontend Data Entry

**Input**: Design documents from `/specs/004-frontend-data-entry/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Manual browser testing (consistent with the dashboard approach).

**Organization**: Tasks are grouped by user story. US1 (Add Expense) and US2 (Add Income) can
be implemented in parallel after the foundational phase. US3 (category dropdown) builds directly
on US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US3)
- Include exact file paths in descriptions

## Path Conventions

All paths are relative to the repo root:
- `frontend/src/types/index.ts` — shared TypeScript interfaces
- `frontend/src/api/client.ts` — fetch functions
- `frontend/src/hooks/useDashboardData.ts` — data-fetching hook
- `frontend/src/components/` — React components
- `frontend/src/App.tsx` — root layout and state
- `frontend/src/App.css` — styles

---

## Phase 1: Setup

No new project structure or dependencies required.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Types, API functions, hook update, and shared Modal component needed by both form
user stories before any form can be built.

- [x] T001 Add three new exports to `frontend/src/types/index.ts`: `type IncomeSource = 'SALARY' | 'FREELANCE' | 'INVESTMENT' | 'RENTAL' | 'GIFT' | 'OTHER'`, `interface CreateBillRequest { amount: number; time: string; description?: string; categoryId?: string; }`, `interface CreateIncomeRequest { amount: number; time: string; description?: string; source?: IncomeSource; }`
- [x] T002 [P] Add `createBill(req: CreateBillRequest): Promise<void>` to `frontend/src/api/client.ts` — `POST /api/v1/createBill` with `Content-Type: application/json` body; throw if response is not 2xx; import `CreateBillRequest` from `../types`
- [x] T003 [P] Add `createIncome(req: CreateIncomeRequest): Promise<void>` to `frontend/src/api/client.ts` — `POST /api/v1/incomes` with JSON body; throw if response is not 2xx; import `CreateIncomeRequest` from `../types`
- [x] T004 Update `frontend/src/hooks/useDashboardData.ts`: add a third parameter `refreshKey: number` to the function signature; add `refreshKey` to the dependency array of the period `useEffect` so all five fetches re-run when it changes; also store the raw `Category[]` array from the categories fetch in a `categories` state variable and include it in the returned `DashboardData` object (add `categories: Category[]` field to the `DashboardData` interface in `frontend/src/types/index.ts`)
- [x] T005 Create `frontend/src/components/Modal.tsx` — functional component accepting `{ open: boolean; onClose: () => void; title: string; children: React.ReactNode }`: return `null` when `open` is false; when open render a `<div className="modal-backdrop">` covering the full viewport that calls `onClose` on click, containing a `<div className="modal-dialog">` that stops click propagation; render `<h2 className="modal-title">{title}</h2>` and `{children}` inside the dialog; add a `useEffect` that attaches a `keydown` listener calling `onClose` when `event.key === 'Escape'`, cleaned up on unmount
- [x] T006 Add modal and form styles to `frontend/src/App.css`: `.modal-backdrop` (position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 100); `.modal-dialog` (background: #fff; border-radius: 10px; padding: 1.5rem; min-width: 320px; max-width: 480px; width: 100%); `.modal-title` (font-size: 1.1rem; font-weight: 600; margin-bottom: 1rem); `.form-group` (display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 0.75rem); `.form-group label` (font-size: 0.85rem; color: #555); `.form-group input, .form-group select` (padding: 0.4rem 0.6rem; border: 1px solid #ccc; border-radius: 6px; font-size: 0.95rem); `.form-error` (color: #c62828; font-size: 0.82rem; margin-top: 0.25rem); `.form-actions` (display: flex; gap: 0.75rem; justify-content: flex-end; margin-top: 1rem); `.btn-primary` (background: #1a1a2e; color: #fff; border: none; padding: 0.45rem 1rem; border-radius: 6px; cursor: pointer); `.btn-primary:disabled` (opacity: 0.5; cursor: not-allowed); `.btn-secondary` (background: transparent; border: 1px solid #ccc; padding: 0.45rem 1rem; border-radius: 6px; cursor: pointer); `.action-buttons` (display: flex; gap: 0.75rem)

**Checkpoint**: `npm run build` in `frontend/` passes after T001–T006.

---

## Phase 3: User Story 1 — Record an Expense (Priority: P1) 🎯 MVP

**Goal**: "Add Expense" button opens a modal form; valid submission saves a bill and refreshes
the dashboard; invalid amount shows an error without submitting.

**Independent test**: Click "Add Expense", enter amount=50, today's date, click Save — verify
the dashboard monthly expenses figure increases. Enter amount=0, click Save — verify inline error
appears and no network request is made.

- [x] T007 [US1] Create `frontend/src/components/AddBillForm.tsx` — functional component accepting `{ open: boolean; onClose: () => void; onSuccess: () => void; categories: Category[] }` (import `Category`, `CreateBillRequest` from `../types`, `createBill` from `../api/client`, `Modal` from `./Modal`): local state: `amount` (string, ''), `date` (string, today's date as YYYY-MM-DD via `new Date().toISOString().slice(0,10)`), `description` (string, ''), `categoryId` (string, ''), `submitting` (boolean, false), `error` (string | null, null); on open/mount reset all fields; render inside `<Modal open={open} onClose={onClose} title="Add Expense">`: form with four `.form-group` divs for amount (type=number, step=0.01, min=0.01, required), date (type=date, required), description (type=text), category (select with empty first option and one option per category: value=cat.id label=cat.name); inline `.form-error` shown when `error` is not null; `.form-actions` with Cancel (calls onClose) and Save (disabled when submitting); on submit: prevent default, validate `parseFloat(amount) > 0` (set error "Amount must be greater than zero" and return if not), set submitting=true, call `createBill({ amount: parseFloat(amount), time: new Date(date).toISOString(), description: description || undefined, categoryId: categoryId || undefined })`, on success call `onSuccess()` then `onClose()`, on catch set error "Could not save — please try again", finally set submitting=false
- [x] T008 [US1] Update `frontend/src/App.tsx`: add `refreshKey` state (`useState(0)`); add `billFormOpen` state (`useState(false)`); pass `refreshKey` as third argument to `useDashboardData(period.year, period.month, refreshKey)`; destructure `categories` from the hook result; add `<AddBillForm open={billFormOpen} onClose={() => setBillFormOpen(false)} onSuccess={() => setRefreshKey(k => k + 1)} categories={categories} />`; add an `<div className="action-buttons">` with a `<button className="btn-primary" onClick={() => setBillFormOpen(true)}>+ Add Expense</button>` in the dashboard header area; import `AddBillForm` from `./components/AddBillForm`

---

## Phase 4: User Story 2 — Record an Income Entry (Priority: P2)

**Goal**: "Add Income" button opens a modal form; valid submission saves an income and refreshes
the dashboard; invalid amount shows an error.

**Independent test**: Click "Add Income", enter amount=1000, today's date, source=SALARY, click
Save — verify the dashboard total income figure increases.

- [x] T009 [US2] Create `frontend/src/components/AddIncomeForm.tsx` — same structure as `AddBillForm` but: title "Add Income"; local state has `source` (string, '') instead of `categoryId`; no `categories` prop; replace category select with a source select whose options are the six values `['SALARY','FREELANCE','INVESTMENT','RENTAL','GIFT','OTHER']` each as `<option value={s}>{s}</option>`; on submit call `createIncome({ amount: parseFloat(amount), time: new Date(date).toISOString(), description: description || undefined, source: (source as IncomeSource) || undefined })`; import `CreateIncomeRequest`, `IncomeSource` from `../types`, `createIncome` from `../api/client`, `Modal` from `./Modal`
- [x] T010 [US2] Update `frontend/src/App.tsx`: add `incomeFormOpen` state (`useState(false)`); add `<AddIncomeForm open={incomeFormOpen} onClose={() => setIncomeFormOpen(false)} onSuccess={() => setRefreshKey(k => k + 1)} />`; add `<button className="btn-primary" onClick={() => setIncomeFormOpen(true)}>+ Add Income</button>` alongside the "Add Expense" button in the action buttons row; import `AddIncomeForm` from `./components/AddIncomeForm`

---

## Phase 5: User Story 3 — Category Assignment on Expense Entry (Priority: P3)

**Goal**: The category dropdown in the Add Expense form is populated from the real categories in
the system. Selecting a category and saving causes the entry to appear under that category in the
spending breakdown section.

**Independent test**: Create a category via Swagger UI if none exist. Open "Add Expense",
confirm the category appears in the dropdown, select it, save. Verify the dashboard's spending
breakdown section shows an amount under that category name.

- [x] T011 [US3] Verify `frontend/src/components/AddBillForm.tsx` category select renders correctly: the first option MUST have `value=""` and label "No category"; subsequent options MUST have `value={cat.id}` and label `{cat.name}` for each item in the `categories` prop; when `categories` is empty the select shows only "No category"; when a category is selected and the form is submitted `categoryId` is sent in the request body as the category's UUID string

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T012 Run `cd frontend && npm run build` and confirm TypeScript compiles with no errors after all changes
- [x] T013 [P] Manual browser smoke test: (a) "Add Expense" opens, submits, dashboard refreshes; (b) "Add Income" opens, submits, dashboard refreshes; (c) entering amount=0 shows validation error without submitting; (d) pressing Escape closes both forms without saving; (e) submitting while backend is down shows "Could not save" error with form staying open

---

## Dependencies

```
T001 → T002, T003 (parallel)
T001 → T004
T004 → T005 (Modal uses no types but App wiring needs hook update)
T005, T006 → US phases

US1: T007 → T008
US2: T009 → T010 (can run in parallel with US1 after T005+T006)
US3: T008 complete → T011 (verify category wiring in already-written AddBillForm)

T011 → T012 → T013
```

## Parallel Execution Opportunities

**Phase 2**: T002 and T003 can run in parallel (both add to client.ts but touch different functions — write sequentially if editing the same file).

**US1 and US2**: T007+T008 can run in parallel with T009+T010 — they create different files.

**Polish**: T013 can run after T012.

## Implementation Strategy

| Phase | Deliverable | Value |
|-------|-------------|-------|
| Phase 2 (T001–T006) | Types, API, hook, Modal ready | Foundation for both forms |
| Phase 3 (T007–T008) | "Add Expense" live | MVP — can record bills from UI |
| Phase 4 (T009–T010) | "Add Income" live | Both sides of the ledger enterable |
| Phase 5 (T011) | Category assignment verified | Spending breakdown stays accurate |
| Phase 6 (T012–T013) | Build + smoke tested | Ship-ready |

**MVP scope**: Complete Phases 2 and 3 (T001–T008). The expense form alone makes the app
usable day-to-day for the most frequent data entry action.
