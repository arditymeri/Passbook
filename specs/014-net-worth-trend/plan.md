# Implementation Plan: Net Worth Trend

**Branch**: `claude/project-status-s0au7m` (spec directory `014-net-worth-trend`) | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-net-worth-trend/spec.md`

## Summary

Show the user's current total net worth (sum of every account's current balance) on the dashboard,
plus a trend of that total across the last 3/6/12 months. Like 012/013, this is a **pure frontend
feature**: `GET /accounts` already returns each account's current derived balance
(`GetAccountServiceImpl.deriveBalance()`), and `useDashboardData.ts` already fetches the complete,
correction-aware transaction history as `allTransactions` (added in 012). A key finding (below)
proves that history — one row per logical transaction at its *current* value, exactly as
`GetBillService`/`GetIncomeService` already expose it — is mathematically sufficient to reconstruct
net worth as of *any* past date, without needing the raw reversal-inclusive rows the backend uses
internally to derive the live balance. No new backend endpoint, no new persisted history, no
snapshot table.

## Technical Context

**Language/Version**: TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler). No backend changes.

**Primary Dependencies**: Existing `@mui/material` v5 component set. The trend chart is a small
hand-rolled inline SVG component — no new charting library dependency (see research.md; this app
has never rendered a chart before, and a single line with at most 12 points doesn't need one).

**Storage**: N/A — no new persisted state. Reads exclusively through data `useDashboardData.ts`
already fetches (`GET /accounts`, and the `allTransactions` value added in 012 from `GET /bills`/
`GET /incomes`).

**Testing**: TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — the same
frontend-testing convention every prior feature's plan.md has documented (no frontend test runner
exists in this codebase).

**Target Platform**: Web browser, desktop-primary (unchanged).

**Project Type**: Web application — this feature touches only the `frontend/` half.

**Performance Goals**: Computing net worth at up to 12 historical cutoffs is a handful of linear
passes over `allTransactions` (already fetched, already in memory, hundreds of rows at this app's
personal scale per 007/009/010/011/012's precedent) — negligible cost, recomputed synchronously on
every render with no debounce needed.

**Constraints**:
- No new REST endpoint, no new Domain service, no new SPI port, no new persisted table (mirrors
  012/013's Constitution Check — there is no new backend surface)
- `GET /accounts`/`GET /bills`/`GET /incomes` are not modified
- Net worth at a past cutoff MUST be derived from the same account/transaction data as the current
  total — automatically true, since both come from the one already-fetched dataset (FR-004)
- A corrected or reversed transaction MUST never be double-counted or shown at its stale value —
  automatically true, since the derivation is proven (research.md) to be safe to compute from the
  same correction-aware, reversal-hiding view (`GetBillService`/`GetIncomeService`, already
  surfaced as `allTransactions`) that every other feature already reads for exactly this reason

**Scale/Scope**: 0 new backend files. ~4 frontend files: one new pure trend-computation function,
one new dashboard card component (built incrementally across the three stories, the same pattern
012's `TransactionFilterBar` and 013's `SetupTemplateDialog` used), and a small mount point in
`App.tsx`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Read-only feature — no bill/income/account row is ever created, corrected, or removed. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass (central to this feature) | This feature *is* balance derivation, taken one step further: net worth today is the sum of already-derived account balances, and net worth at a past date is derived the same way (research.md proves the derivation is exact) — nothing about a historical point is ever cached, stored, or snapshotted. |
| IV. Currency Precision | ✅ Pass | Frontend-side summation of already-serialized amounts for display, the same pattern every prior dashboard/status component already uses (`SummaryCard`, `BudgetStatus`, `SavingsGoalsPage`) — not a new deviation. All backend computation remains `BigDecimal`, unchanged. |
| V. Audit Trail | ✅ N/A | No state-changing operation exists in this feature. |
| VI. Test-First Development | ⚠ Pre-existing gap, not worsened | Scoped to Domain financial/business-rule logic; this feature adds none. The new trend-computation function has no automated test, for the same documented reason as 012/013 (no frontend test runner in this codebase) — verified instead by `tsc --noEmit`, manual `quickstart.md` walkthrough, and (given the derivation's correctness is the whole point of this feature) careful worked-example verification in data-model.md. |
| VII. API Contract Stability | ✅ N/A | No endpoint is added, removed, or changed. |
| VIII. Hexagonal Architecture Compliance | ✅ N/A | No Domain/Infrastructure/Application code is touched. |

**Gate decision**: PASS. Like 012/013, this is presentation-layer-only; every value it derives
comes from data already fetched through already-published, already-correction-aware endpoints.

## Project Structure

### Documentation (this feature)

```text
specs/014-net-worth-trend/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output (derivation formulas + worked example only —
│                                 #                 no persisted entity)
└── quickstart.md                # Phase 1 output
```

No `contracts/` directory: no REST endpoint is added or changed — the same reasoning 012/013's
plan.md already used.

### Source Code (repository root)

```text
frontend/src/
├── types/index.ts                          # MODIFY — add NetWorthTrendPoint, NetWorthRangeMonths
├── utils/
│   └── netWorthTrend.ts                    # NEW — pure computeNetWorthTrend(accounts,
│                                            #        allTransactions, monthsBack) function
├── components/
│   └── NetWorthCard.tsx                    # NEW — current total (US1), trend chart (US2), range
│                                            #        selector (US3), built incrementally
└── App.tsx                                 # MODIFY — mount NetWorthCard on the dashboard,
                                             #          passing the accounts/allTransactions
                                             #          useDashboardData already fetches
```

No backend directories (`Domain/`, `Application/`, `Infrastructure/`, `integration-tests/`) are
touched by this feature.

**Structure Decision**: Frontend-only change to the existing web application. `NetWorthCard` mounts
near `SummaryCard` at the top of the dashboard (not inside the month-scoped row alongside
`CategorySpend`/`BudgetStatus`/`UpcomingRecurring`), since net worth is deliberately independent of
the month currently selected via `MonthNav` — it always shows "now" and the trailing months before
it, not whichever month the user happens to be browsing.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
