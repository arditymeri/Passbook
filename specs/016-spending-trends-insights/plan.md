# Implementation Plan: Spending Trends & Insights

**Branch**: `claude/project-status-s0au7m` (spec directory `016-spending-trends-insights`) | **Date**: 2026-08-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-spending-trends-insights/spec.md`

## Summary

Extend the existing single-month "spending by category" view into a multi-month trend, and
surface the categories with the biggest month-over-month change so a user sees what drove a
change in their spending without comparing months by hand. Like 012/013/014, this is a **pure
frontend feature**: `useDashboardData.ts` already fetches the complete, correction-aware bill
history as `allTransactions` (added in 012, proven sufficient for historical derivation by 014),
and every BILL transaction already carries `categoryId`, `amount`, and `time` — everything needed
to bucket spend by month and category client-side. No new backend endpoint, no new Domain service,
no new persisted history. (The one existing multi-month backend endpoint, `/analysis/period`, is
deliberately *not* used — research.md §1 found it isn't correction-aware and would need its own
fix first, which is out of scope for this feature.)

## Technical Context

**Language/Version**: TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler). No backend changes.

**Primary Dependencies**: Existing `@mui/material` v5 component set only — the trend is rendered
as a compact per-category list with sparkline-style bars, not a new chart, so no charting library
is added (consistent with 014's "no new dependency unless clearly justified" precedent, and this
feature's data is naturally list-shaped like the existing `CategorySpend` component it extends,
rather than a single continuous line like net worth).

**Storage**: N/A — no new persisted state. Reads exclusively through data `useDashboardData.ts`
already fetches (`allTransactions`, `categories`/`categoryNames`).

**Testing**: TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — the same
frontend-testing convention every prior feature's plan.md has documented (no frontend test runner
exists in this codebase).

**Target Platform**: Web browser, desktop-primary (unchanged).

**Project Type**: Web application — this feature touches only the `frontend/` half.

**Performance Goals**: Computing trends and movers is a single linear pass over `allTransactions`
(already fetched, already in memory, hundreds of rows at this app's personal scale per
007/009/010/011/012/014's precedent) plus a small aggregation — negligible cost, recomputed
synchronously on every render/window change with no debounce needed (SC-003: within a couple of
seconds, trivially satisfied).

**Constraints**:
- No new REST endpoint, no new Domain service, no new SPI port, no new persisted table (mirrors
  012/013/014's Constitution Check — there is no new backend surface)
- `GET /bills`/`GET /incomes`/`GET /categories` are not modified
- Trend and mover figures MUST reflect each transaction's current corrected value (FR-006) —
  automatically true, since the derivation reads from the same correction-aware `allTransactions`
  every other feature already reads for exactly this reason (research.md §1)
- A category with zero spending in part of a window MUST show as zero, never omitted from that
  category's own trend line (FR-002) — a bucketing/fill-in rule, not a display heuristic
  (data-model.md)
- Movers MUST recognize a category with zero spending last month as a valid mover when it has
  spending this month (FR-004) — implemented as an always-zero-filled comparison, not a
  baseline-required one (research.md §6)

**Scale/Scope**: 0 new backend files. ~4 frontend files: one new pure trend/movers-computation
function, one new dashboard card component (built incrementally across the three stories, the
same pattern 012's `TransactionFilterBar`, 013's `SetupTemplateDialog`, and 014's `NetWorthCard`
used), and a small mount point in `App.tsx`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Read-only feature — no bill/income/category row is ever created, corrected, or removed (FR-008). |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ N/A | This feature aggregates spend by category and month, not account balances — no derivation of a balance is involved, but the same "derive, don't store" spirit applies: nothing is cached or snapshotted, recomputed fresh from `allTransactions` on every view. |
| IV. Currency Precision | ✅ Pass | Frontend-side summation of already-serialized amounts for display, the same pattern every prior dashboard/status component already uses (`SummaryCard`, `CategorySpend`, `NetWorthCard`) — not a new deviation. All backend computation remains `BigDecimal`, unchanged. |
| V. Audit Trail | ✅ N/A | No state-changing operation exists in this feature. |
| VI. Test-First Development | ⚠ Pre-existing gap, not worsened | Scoped to Domain financial/business-rule logic; this feature adds none. The new trend/movers-computation function has no automated test, for the same documented reason as 012/013/014 (no frontend test runner in this codebase) — verified instead by `tsc --noEmit`, manual `quickstart.md` walkthrough, and the worked example in data-model.md. |
| VII. API Contract Stability | ✅ N/A | No endpoint is added, removed, or changed. |
| VIII. Hexagonal Architecture Compliance | ✅ N/A | No Domain/Infrastructure/Application code is touched. |

**Gate decision**: PASS. Like 012/013/014, this is presentation-layer-only; every value it derives
comes from data already fetched through already-published, already-correction-aware endpoints.

## Project Structure

### Documentation (this feature)

```text
specs/016-spending-trends-insights/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output (types + algorithm + worked example — no
│                                 #                 persisted entity)
└── quickstart.md                # Phase 1 output
```

No `contracts/` directory: no REST endpoint is added or changed — the same reasoning 012/013/014's
plan.md already used.

### Source Code (repository root)

```text
frontend/src/
├── types/index.ts                          # MODIFY — add SpendingTrendRangeMonths,
│                                            #          CategoryTrendPoint, CategorySpendingTrend,
│                                            #          SpendingMover
├── utils/
│   └── spendingTrends.ts                   # NEW — pure computeSpendingTrends(allTransactions,
│                                            #        categoryNames, monthsBack) function
├── components/
│   └── SpendingTrendsCard.tsx              # NEW — per-category trend list (US1), movers
│                                            #        section (US2), range selector (US3), built
│                                            #        incrementally
└── App.tsx                                 # MODIFY — mount SpendingTrendsCard on the dashboard,
                                             #          passing allTransactions/categoryNames
                                             #          useDashboardData already fetches
```

No backend directories (`Domain/`, `Application/`, `Infrastructure/`, `integration-tests/`) are
touched by this feature.

**Structure Decision**: Frontend-only change to the existing web application.
`SpendingTrendsCard` mounts in the month-scoped row alongside `CategorySpend` (which it directly
extends conceptually — same category-spend data, now shown across months instead of one), rather
than near `SummaryCard`/`NetWorthCard` at the top: unlike net worth, spending trends are naturally
grouped with the app's other category-spending views.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
