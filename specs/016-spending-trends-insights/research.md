# Phase 0 Research: Spending Trends & Insights

All items below were resolved from the existing codebase; no `NEEDS CLARIFICATION` markers
remain from the Technical Context.

## 1. Pure-frontend feasibility

**Decision**: Build entirely in the frontend, like 012/013/014 — no new backend endpoint, no new
Domain service, no new persisted data.

**Rationale**: `useDashboardData.ts` already fetches the entire bill/income history (unfiltered by
date) as `allTransactions` via `GetBillService`/`GetIncomeService.getAll()` — the same
correction-aware endpoints (reversal rows and superseded originals already excluded) that 014's
net worth trend already proved sufficient for historical derivation. Every `Transaction` of type
`BILL` already carries `categoryId`, `amount`, and `time`, which is everything needed to bucket
spend by (year, month, category) client-side.

**A backend alternative exists but is worse, not better**: `GetSpendingAnalysisServiceImpl.
getSummaryForPeriod()` (behind `GET /analysis/period`, unused by the frontend today) computes
`spendingByCategory` per month via a **raw** `billRepository.findByTimeBetween()` query — it does
**not** filter reversal rows or superseded originals the way `GetBillService.getAll()` does. Using
it would either require fixing that gap first (out of scope — a pre-existing gap unrelated to this
feature, matching this app's established pattern of not silently fixing unrelated issues while
building a new feature) or produce a trends view that quietly double-counts corrected transactions.
The frontend path avoids this gap entirely, for free, because `allTransactions` was already
correction-aware for a different feature (012).

**Alternatives considered**: Fixing `GetSpendingAnalysisServiceImpl` to be correction-aware and
building on `/analysis/period`. Rejected for this feature — it's a real gap worth fixing
independently sometime, but doing so here would silently widen this feature's scope beyond what
was asked, and the frontend-only path delivers the same user value today with zero backend risk.

## 2. Bucketing approach

**Decision**: A new pure function, `computeSpendingTrends(allTransactions, categoryNames,
monthsBack)`, walks `allTransactions` once, bucketing every `BILL` transaction with a non-null
`categoryId` into a `Map<categoryId, Map<"YYYY-M", amount>>` by the UTC year/month its `time` falls
in, then reads out only the `monthsBack` most recent calendar months (oldest to newest, same month
window construction 014's `computeNetWorthTrend` already uses via `Date.UTC`).

**Rationale**: Unlike net worth (which needs to reconstruct a past *balance* by undoing later
transactions from a current total), a month's category spend is just the sum of that month's own
transactions — no cutoff-subtraction trick is needed, since every bill's `time` already places it
in exactly one calendar month. This is simpler and more directly verifiable than 014's derivation.

**Alternatives considered**: Reusing `netWorthTrend.ts`'s backward-subtraction pattern for
consistency's sake. Rejected — it would be needless indirection for a plain per-month sum; using
the simplest correct approach for each feature's actual shape is preferable to forcing a shared
pattern where the underlying math differs.

## 3. Movers: comparison basis

**Decision**: Compare the two most recent calendar months in `allTransactions` (i.e. the current
month and the one immediately before it — always "now" and "one month before now," not affected by
whatever window size is selected), ranked by absolute €change, both directions.

**Rationale**: Matches the spec's Assumptions exactly (absolute currency change, not percentage
alone, to avoid a €1→€5 category outranking a €200 swing). Comparing the two most recent months
regardless of the selected trend window means the movers list doesn't change just because the user
picked a longer or shorter window to look at — window selection (US3) only affects how much history
the trend chart shows, not which two months movers compares.

**Alternatives considered**: Ranking by percentage change. Rejected per the spec's explicit
Assumption. Comparing the two oldest/newest points of whatever window is selected (so movers would
shift with the window). Rejected — it would make "movers" mean something different every time the
user changes the window, which is confusing; movers should always answer "what changed since last
month," a fixed, always-meaningful question independent of how far back the chart happens to look.

## 4. Category inclusion/exclusion

**Decision**: A category with at least one non-zero month within the selected window appears in
the trend (with zero shown explicitly for any month it had no spending, per FR-002); a category
with zero spending across the *entire* window is omitted entirely (FR-007). Movers, however,
consider every category present in `allTransactions` overall (not only ones already shown in the
trend list) — a category that only ever appears in the most recent month should still be able to
show up as a mover, even before the trend chart has "picked it up" for the window.

**Rationale**: Directly encodes FR-002/FR-007 and the spec's Edge Cases: a zero-everywhere category
would just be noise in the trend list, but a category with real month-over-month change must never
be silently dropped from movers just because of how the (separate) trend-list inclusion rule works.

## 5. Deleted-category handling

**Decision**: No special handling needed — reuse the exact `categoryNames.get(catId) ?? catId`
fallback `CategorySpend.tsx` already uses today for a transaction whose category no longer exists.

**Rationale**: This is already-solved, already-shipped behavior in this codebase; a category being
deleted after the fact doesn't erase the `categoryId` already stored on historical bill rows
(Constitution Principle I — transactions are immutable), so the existing fallback is sufficient
without any new logic.

## 6. "No prior month" edge case

**Decision**: Treat a nonexistent prior month identically to a prior month with zero spending in
every category — i.e., no special-case suppression logic. A brand-new user whose only transactions
are from the current month will see every spending category listed as a mover growing "from zero,"
which is itself the correct, informative answer (mirrors US2's acceptance scenario 3: a category
with no spending last month is still recognized as a mover).

**Rationale**: There's no way to distinguish "this month never existed" from "this month existed
with zero spending in every category" from transaction data alone, and there's no need to — both
produce the same, correct user-facing result. Building a separate "no history yet" suppression path
would be complexity this feature's stated Edge Cases don't actually require once the zero-comparison
behavior is understood as universal, not month-specific.

## 7. Window presets

**Decision**: `SpendingTrendRangeMonths = 3 | 6 | 12`, default 6 — identical preset set and default
to 014's `NetWorthRangeMonths`.

**Rationale**: Directly matches the spec's Assumption ("mirroring the existing net worth trend
feature's precedent"), and keeps the app's several month-windowed trend features consistent with
each other from a user's perspective.
