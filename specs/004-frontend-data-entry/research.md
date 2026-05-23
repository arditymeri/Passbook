# Research: Frontend Data Entry

**Feature**: 004-frontend-data-entry
**Date**: 2026-05-23

## Decision 1: Dashboard Refresh Strategy After Submission

**Decision**: Add a `refreshKey: number` state to `App`. Increment it on every successful form
submission. Pass `refreshKey` as a third argument to `useDashboardData(year, month, refreshKey)`.
The hook includes `refreshKey` in its `useEffect` dependency array, causing all five fetches to
re-run whenever it changes.

**Rationale**: The simplest correct pattern for triggering a re-fetch without adding a state
management library. It avoids optimistic UI complexity and keeps the hook pure and predictable.
The dashboard is not performance-critical (personal-scale data), so a full re-fetch on save is
acceptable.

**Alternatives considered**:
- Optimistic UI update: splice the new entry into local state immediately. More responsive but
  significantly more complex and requires careful rollback on error.
- React Query / SWR `invalidateQueries`: correct approach at scale, but adds a dependency and
  changes the entire data-fetching architecture.
- Page reload: simplest possible but jarring UX; loses the selected month.

---

## Decision 2: Modal Implementation — No Library

**Decision**: Implement a thin `Modal.tsx` wrapper using a `<div>` with CSS `position: fixed;
inset: 0; background: rgba(0,0,0,0.4)` as the backdrop and a centred `<div>` as the dialog.
Close on backdrop click and Escape key via `useEffect` + `keydown` listener.

**Rationale**: The project already avoids unnecessary dependencies. A modal needs ~30 lines of
CSS and ~15 lines of React — no library justified. The `dialog` HTML element is an alternative
but has inconsistent styling across browsers and is harder to animate.

**Alternatives considered**:
- `@headlessui/react` Modal: correct, accessible, but adds a dependency.
- Native `<dialog>`: good accessibility but browser support for `.showModal()` / `.close()` is
  inconsistent and styling is tricky.

---

## Decision 3: Bill POST Endpoint URL

**Decision**: The bill creation endpoint is `POST /api/v1/createBill` (not `/api/v1/bills`).
This is the existing endpoint defined in `bill-post-controller.yaml`. The request body uses the
`bill` model schema which accepts `amount: number`, `time: string (datetime)`, `categoryId: string`.

**Rationale**: Discovered from reading the existing OpenAPI YAML. The URL is non-standard but
already deployed and working. The frontend must use this exact path.

**Alternatives considered**: N/A — this is the existing API contract.

---

## Decision 4: Amount Input Handling

**Decision**: Amount is entered as a plain `<input type="number" step="0.01" min="0.01">`.
On submit, read `parseFloat(value)` and send as a JSON number. Validate `> 0` before sending.
Never perform arithmetic on the value — just pass it through to the backend.

**Rationale**: `type="number"` gives browser-native numeric keyboard on mobile and prevents
non-numeric input. `parseFloat` is only used to validate `> 0` and to serialise to JSON — no
financial arithmetic is performed. The backend handles all precision via BigDecimal.

**Alternatives considered**:
- `type="text"` with regex: more control but more code; not needed for this scope.
- Sending as string: some backends accept string amounts, but the existing bill/income POST
  schemas expect a JSON number type.

---

## Decision 5: Income Sources — Fixed Frontend List

**Decision**: Define a `const INCOME_SOURCES` array in `AddIncomeForm.tsx` matching the backend
`IncomeSource` enum: `['SALARY', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'GIFT', 'OTHER']`. No API
call needed.

**Rationale**: The spec explicitly states this. The list is stable (defined in the Java enum) and
small enough to hardcode safely. An API endpoint for enum values would be over-engineering.

**Alternatives considered**:
- Fetch from backend: unnecessary round-trip for a static list.
- Store in `types/index.ts` as a TypeScript union: correct, but a local constant in the component
  is simpler for a 6-item list.
