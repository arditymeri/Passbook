# Implementation Plan: Frontend Dashboard

**Branch**: `003-frontend-dashboard` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-frontend-dashboard/spec.md`

## Summary

Replace the default Vite scaffold in `frontend/` with a single-page financial dashboard that
consumes five existing backend REST endpoints. Four read-only sections — monthly summary, category
spending, budget status, recent transactions — each fetch data independently. Month navigation
updates all sections simultaneously. No new backend work required.

## Technical Context

**Language/Version**: TypeScript 4.6, React 18.2

**Primary Dependencies**: React 18 (already installed), Vite 3.1 (already installed). No new npm
packages required for MVP — native `fetch`, `useState`, `useEffect`, CSS progress bars.

**Storage**: N/A — frontend is stateless; all data from backend APIs

**Testing**: Manual browser testing for MVP (no automated frontend test framework in scope)

**Target Platform**: Modern desktop browser (Chrome/Firefox/Safari); mobile layout is out of scope

**Project Type**: Single-page web application consuming REST APIs

**Performance Goals**: All sections populated within 3 seconds on localhost

**Constraints**: No arithmetic on monetary values in the frontend — values are pre-computed by the
backend. Display only. Category names resolved client-side from a `GET /api/v1/categories` call
using the category ID keys in `spendingByCategory`.

**Scale/Scope**: Single user, personal use — no pagination, no authentication.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Read-only frontend — no writes, no mutations |
| II. Double-Entry Accounting | ✅ N/A | No financial entries created |
| III. Account Integrity | ✅ N/A | Balances displayed as received; no client-side derivation |
| IV. Currency Precision | ✅ Pass | All monetary values received as JSON numbers (backed by BigDecimal); formatted to 2 d.p. using `Intl.NumberFormat` — no JS float arithmetic on amounts |
| V. Audit Trail | ✅ N/A | Read-only; no state-changing operations |
| VI. Test-First | ✅ N/A | Frontend; manual browser testing is the stated approach for this MVP |
| VII. API Contract Stability | ✅ Pass | Frontend consumes existing stable v1 endpoints; no breaking changes introduced |
| VIII. Hexagonal Architecture | ✅ N/A | Applies to backend modules only |

**Gate decision**: PASS. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/003-frontend-dashboard/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 0 output (TypeScript interfaces)
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-components.md # Phase 1 output (component interface specs)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (frontend/)

```text
frontend/
├── vite.config.ts                    # modified — add /api proxy to localhost:8080
├── src/
│   ├── App.tsx                       # modified — replace scaffold with Dashboard layout
│   ├── App.css                       # modified — replace scaffold styles with dashboard styles
│   ├── api/
│   │   └── client.ts                 # new — typed fetch wrappers for each backend endpoint
│   ├── types/
│   │   └── index.ts                  # new — TypeScript interfaces for API response shapes
│   ├── hooks/
│   │   └── useDashboardData.ts       # new — custom hook orchestrating all fetches for a period
│   └── components/
│       ├── MonthNav.tsx              # new — previous/current/next month navigation bar
│       ├── SummaryCard.tsx           # new — income, expenses, net balance cards
│       ├── CategorySpend.tsx         # new — per-category spend list with CSS progress bars
│       ├── BudgetStatus.tsx          # new — budget vs actual per category with over/under badge
│       └── RecentTransactions.tsx    # new — merged bill + income list, 10 most recent
```

**Structure Decision**: Flat component structure under `src/components/`; all state managed at the
`App` level (selected month) and fetched via a single custom hook. No routing library needed.
No external state management (Redux/Zustand) — React's built-in `useState` and `useEffect` suffice
for this single-view app.
