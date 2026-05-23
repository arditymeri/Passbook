# Implementation Plan: Frontend Data Entry

**Branch**: `004-frontend-data-entry` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-frontend-data-entry/spec.md`

## Summary

Add two modal forms to the existing React dashboard — one for recording expenses (bills) and one
for recording income. No backend changes required. The forms POST to already-existing endpoints
(`POST /api/v1/createBill` and `POST /api/v1/incomes`). On success the dashboard refreshes all
sections via a `refreshKey` counter in `App` state.

## Technical Context

**Language/Version**: TypeScript 4.6, React 18.2

**Primary Dependencies**: No new npm packages — native browser `fetch`, React `useState`/`useEffect`,
existing CSS utility classes from `App.css`.

**Storage**: N/A — frontend only; data is persisted by the backend

**Testing**: Manual browser testing (consistent with the dashboard approach)

**Target Platform**: Modern desktop browser

**Project Type**: Single-page web app — extending the existing dashboard in `frontend/`

**Performance Goals**: Form submit round-trip completes and dashboard refreshes within 2 seconds
on localhost

**Constraints**: No backend changes. Bill POST is at `/api/v1/createBill` (not `/api/v1/bills`).
Income POST is at `/api/v1/incomes`. Both endpoints already exist. Income source options are a
fixed TypeScript enum list — no API call needed. Categories are already cached in `App` state
from the dashboard load — no extra fetch on form open.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Forms only create new entries; no edits or deletions. Backend enforces immutability. |
| II. Double-Entry Accounting | ✅ N/A | Entry creation delegates to backend; the frontend does not implement accounting logic. |
| III. Account Integrity | ✅ N/A | No balance derivation in frontend. |
| IV. Currency Precision | ✅ Pass | Amount entered as text, sent as a number string to the backend. No float arithmetic in frontend. Backend uses BigDecimal. |
| V. Audit Trail | ✅ N/A | Backend records all entries; frontend is read/write UI only. |
| VI. Test-First | ✅ N/A | Frontend; manual browser testing is the stated approach. |
| VII. API Contract Stability | ✅ Pass | Consuming existing stable v1 endpoints. No new API surface. |
| VIII. Hexagonal Architecture | ✅ N/A | Applies to backend modules only. |

**Gate decision**: PASS. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/004-frontend-data-entry/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 0 output (form field types)
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-components.md # Phase 1 output (form component specs)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code Changes (frontend/)

```text
frontend/src/
├── types/
│   └── index.ts                   # modified — add CreateBillRequest, CreateIncomeRequest, IncomeSource
├── api/
│   └── client.ts                  # modified — add createBill(), createIncome()
├── hooks/
│   └── useDashboardData.ts        # modified — accept refreshKey parameter to force re-fetch
├── components/
│   ├── Modal.tsx                  # new — reusable modal overlay wrapper
│   ├── AddBillForm.tsx            # new — expense entry form (amount, date, description, category)
│   └── AddIncomeForm.tsx          # new — income entry form (amount, date, description, source)
├── App.tsx                        # modified — add refreshKey state, "Add Expense"/"Add Income" buttons,
│                                  #   wire forms, pass refreshKey to useDashboardData
└── App.css                        # modified — add modal overlay and form styles
```

**Structure Decision**: Minimal footprint — three new files, four modified. No new routes, no new
state management library, no new dependencies. The `refreshKey` pattern (increment an integer
counter in `App` to trigger a `useEffect` re-run in the hook) is the simplest correct solution
for post-submit refresh with the existing hook architecture.
