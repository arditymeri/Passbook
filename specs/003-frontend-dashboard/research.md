# Research: Frontend Dashboard

**Feature**: 003-frontend-dashboard
**Date**: 2026-05-23

## Decision 1: No New npm Dependencies for MVP

**Decision**: Use only what is already installed (React 18, TypeScript 4.6, Vite 3.1). Native
browser `fetch` for HTTP, CSS custom properties and flexbox for layout, `Intl.NumberFormat` for
currency display, CSS progress bars for spending/budget visualisation.

**Rationale**: The spec explicitly states no charting library is required for MVP and that CSS
progress bars are sufficient. Adding dependencies (Axios, React Query, Chart.js) increases
bundle size and maintenance surface without adding MVP-required capability. They can be added in
a follow-up sprint.

**Alternatives considered**:
- Axios: ergonomic but unnecessary — native `fetch` with a thin wrapper is sufficient.
- React Query / SWR: excellent for caching but overkill for a single-view app with 5 endpoints.
- Chart.js / Recharts: needed for proper charts but out of scope for CSS-bar MVP.

---

## Decision 2: Vite Dev Proxy for CORS

**Decision**: Add a `server.proxy` entry to `vite.config.ts` that forwards all `/api` requests
to `http://localhost:8080`. The frontend makes all API calls to `/api/v1/...` (relative), and
Vite rewrites them to the backend at development time.

**Rationale**: This is the standard Vite pattern for local full-stack development. It avoids CORS
issues without requiring backend CORS configuration changes. The spec's assumption section
explicitly lists this approach.

**Alternatives considered**:
- Backend CORS headers: works but couples frontend URL to backend config.
- Absolute URLs in frontend code: breaks when deployed or ports change.

---

## Decision 3: State Architecture — Single Month State at App Level

**Decision**: `App.tsx` holds a single `{ year: number, month: number }` state for the selected
period. All child components receive this as props and do not hold period state themselves. A
custom hook `useDashboardData(year, month)` fires all five fetch calls in parallel whenever the
period changes and returns the aggregated loading/error/data state.

**Rationale**: Simple, predictable, easy to debug. No context or global store needed for a
single controlled value. Parallel fetches (using `Promise.allSettled`) ensure one slow endpoint
doesn't block the others, satisfying FR-006 (independent section failure handling).

**Alternatives considered**:
- Each component fetches its own data: causes multiple re-renders, harder to coordinate loading.
- React Context for period: over-engineering for one shared value.

---

## Decision 4: Category Name Resolution

**Decision**: Fetch `GET /api/v1/categories` once when the dashboard loads (not per month
change). Store results in a `Map<string, string>` (id → name). Pass this map to `CategorySpend`
and `BudgetStatus`. Display category ID as fallback if the name is not found.

**Rationale**: Categories change rarely; fetching once avoids unnecessary repeat calls. The
`spendingByCategory` and budget status responses use category IDs as keys, so client-side
resolution is necessary. The spec explicitly documents this as an assumption.

**Alternatives considered**:
- Fetch categories on every month change: wasteful, unnecessary network traffic.
- Backend resolves names in analysis/budget responses: would require backend changes — out of scope.

---

## Decision 5: Transaction List Assembly (US4)

**Decision**: Fetch `GET /api/v1/bills` and `GET /api/v1/incomes` in parallel. Filter each list
client-side to entries whose `time` field falls within the selected month. Merge into a single
array tagged with `type: 'BILL' | 'INCOME'`. Sort by `time` descending. Slice to first 10.

**Rationale**: The existing bill/income GET endpoints return all records (no date filter
parameter available in v1). Client-side filtering is acceptable at personal-data scale (hundreds
of entries). No backend changes needed.

**Alternatives considered**:
- Add date-filter query params to bill/income endpoints: would require backend changes outside
  this feature's scope.
- Dedicated "recent transactions" backend endpoint: better long-term but out of scope.

---

## Decision 6: Monetary Display Format

**Decision**: Use `Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' })` for all
monetary display. This matches the Austrian locale context of the project (user email domain
suffix `.at`, PostgreSQL user `diti`, project name `myfinance`). All values are received as
JSON numbers and formatted only at the display layer — no arithmetic performed in the frontend.

**Rationale**: `Intl.NumberFormat` is built into all modern browsers, requires no dependency,
and correctly handles decimal separators and currency symbols. Formatting at display time only
preserves backend precision (Principle IV).

**Alternatives considered**:
- `toFixed(2)`: no currency symbol, locale-unaware.
- External currency library: unnecessary dependency.
