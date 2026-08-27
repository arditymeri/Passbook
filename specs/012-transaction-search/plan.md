# Implementation Plan: Transaction Search & Filtering

**Branch**: `claude/project-status-s0au7m` (spec directory `012-transaction-search`) | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-transaction-search/spec.md`

## Summary

Let a user search their full transaction history by description text and narrow it by category
(bills) or income source, account, date range, amount range, and transaction type, all combined
with AND logic. This is a **pure frontend feature**: `GET /bills` and `GET /incomes` already return
the complete, correction-aware history (`GetBillService.getAll()`/`GetIncomeService.getAll()`
already hide reversal rows and superseded originals, per 008), and the dashboard already fetches
both in full on every load — it just slices them down to the current month's ten most recent for
display. Search/filter reuses that same already-fetched data; nothing new is persisted, no new
endpoint is added, and no Domain/Infrastructure/Application code changes.

## Technical Context

**Language/Version**: TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler). No backend changes.

**Primary Dependencies**: Existing `@mui/material` v5 component set (no new frontend dependencies); no new backend dependencies.

**Storage**: N/A — no new persisted state. Reads exclusively through the existing `GET /bills` / `GET /incomes` endpoints, already wired into `useDashboardData.ts`.

**Testing**: TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — the same frontend-testing convention every prior feature's plan.md has documented (this codebase has no frontend test runner: no vitest/jest/testing-library dependency, no `*.test.ts` file anywhere). No backend tests are relevant since no backend code changes.

**Target Platform**: Web browser, desktop-primary (unchanged).

**Project Type**: Web application — this feature touches only the `frontend/` half.

**Performance Goals**: Filtering runs in memory over the already-fetched full bill/income arrays (a few hundred rows at this app's personal scale, per 007/009/010/011's precedent) on every keystroke/filter change — no debounce, no pagination, no server round-trip per filter change.

**Constraints**:
- No new REST endpoint, no new Domain service, no new SPI port, no new persisted table (Principle VIII stays trivially satisfied — there is no new backend surface to violate it)
- `GET /bills`/`GET /incomes` are not modified; their existing (unused) `date` query parameter is left as-is
- Filtering must reflect each transaction's current corrected value and show exactly one row per logical transaction — automatically true, since it reuses `GetBillService.getAll()`/`GetIncomeService.getAll()`'s existing reversal/supersession-hiding behavior (FR-012)
- Amount comparisons operate on the numbers the existing API responses already serialize (no new monetary computation, so Principle IV isn't newly at risk)

**Scale/Scope**: 0 new backend files. ~4 frontend files touched: one new pure filter-logic module, one new filter-bar UI component, and two small extensions to existing files (`useDashboardData.ts`, `App.tsx`), plus a minor prop addition to `RecentTransactions.tsx`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Read-only feature — no bill/income row is ever created, corrected, or removed. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ N/A | Doesn't touch account balances; reads the same already-correction-aware transaction lists every other feature reads. |
| IV. Currency Precision | ✅ Pass | No new monetary computation — amount filtering is a numeric comparison over values the existing API already serializes; no new `double`/`float` money field is introduced anywhere. |
| V. Audit Trail | ✅ N/A | No state-changing operation exists in this feature — nothing to audit-log. |
| VI. Test-First Development | ⚠ Pre-existing gap, not worsened | This principle's mandatory-testing text is scoped to Domain financial/business-rule logic; this feature adds none. The new `filterTransactions()` pure function is real client-side logic with no automated test, but the frontend has *never* had a test runner in this codebase (confirmed: no vitest/jest/testing-library dependency exists) — every prior feature's plan.md already documents "TypeScript type-check + manual smoke-test" as the frontend testing convention. This feature follows that same existing convention rather than introducing new scope-creeping test infrastructure. |
| VII. API Contract Stability | ✅ N/A | No endpoint is added, removed, or changed. |
| VIII. Hexagonal Architecture Compliance | ✅ N/A | No Domain/Infrastructure/Application code is touched — there is no backend surface for this principle to apply to. |

**Gate decision**: PASS. This is a presentation-layer-only feature; the only flagged item (VI) is an accurate description of this app's pre-existing frontend-testing posture, not a new violation introduced here.

## Project Structure

### Documentation (this feature)

```text
specs/012-transaction-search/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output (client-side filter shape only — no persisted entity)
└── quickstart.md                # Phase 1 output
```

No `contracts/` directory: this feature adds no REST endpoint and changes no existing one, so there
is no new interface contract to document (plan-template's "skip if project is purely internal" —
here, purely presentation-layer over an already-published contract).

### Source Code (repository root)

```text
frontend/src/
├── types/index.ts                          # MODIFY — add TransactionFilters, TransactionTypeFilter
├── utils/
│   └── transactionFilters.ts               # NEW — pure filterTransactions(transactions, filters)
├── hooks/
│   └── useDashboardData.ts                 # MODIFY — also return allTransactions: Transaction[]
│                                            #          (full merged bill+income history, unsliced,
│                                            #          not month-filtered) alongside the existing
│                                            #          month-scoped, top-10 `transactions`
├── components/
│   ├── TransactionFilterBar.tsx             # NEW — search text, category/source, account, date
│   │                                        #        range, amount range, type, clear-all
│   └── RecentTransactions.tsx               # MODIFY — accept an `emptyMessage` prop so the caller
│                                            #          can say "No transactions found" instead of
│                                            #          "No transactions for this month" while a
│                                            #          filter is active
└── App.tsx                                 # MODIFY — hold filter state, compute the displayed
                                             #          transaction list (filtered allTransactions
                                             #          when any filter/search is active, else the
                                             #          existing month-scoped transactions), mount
                                             #          TransactionFilterBar above RecentTransactions
```

No backend directories (`Domain/`, `Application/`, `Infrastructure/`, `integration-tests/`) are
touched by this feature.

**Structure Decision**: Frontend-only change to the existing web application. No new modules, no
new backend files, no new full page — the filter bar slots into the existing dashboard directly
above `RecentTransactions`, consistent with the spec's framing ("narrow down the recent-transactions
view").

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
