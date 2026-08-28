# Phase 0 Research: Net Worth Trend

No `[NEEDS CLARIFICATION]` markers remain in the spec — every open design question was resolved
with a documented default in the spec's Assumptions section during `/speckit-specify`. This phase
records the central finding this feature's entire design rests on, plus the decisions built on it.

## Finding: the correction-aware "current value" transaction view is exact for historical derivation, not an approximation

**Found**: A bill/income correction (008) writes two rows in one transaction:
a **reversal** (`BillCorrections.reversalOf`) with the *same* `time`, category, and account as the
row it reverses, just a negated amount; and a **replacement** carrying the user's new values
(amount, description, category, account, and *its own, possibly different*, `time`). Both point
back at the original via `correctsTransactionId`. `GetBillService.getAll()`/
`GetIncomeService.getAll()` (the reads `GET /bills`/`GET /incomes` already expose, and what
`useDashboardData.ts` already fetches in full as `allTransactions`, per 012) hide every reversal
row and every row something else supersedes — returning exactly one row per logical transaction,
at its current value.

**The proof**: because a reversal always shares its target's exact `time`, the pair (original,
reversal) contributes **zero** to any time-bucketed cumulative sum, for *any* cutoff date — a
cutoff either falls after both rows (their amounts cancel: `+X` and `-X`) or before both (neither
is counted). There is no cutoff date under which only one of the pair is included. So collapsing
`{original, reversal, replacement}` down to just `{replacement}` — exactly what
`GetBillService.getAll()` already does — changes *nothing* about the value of
`Σ(amounts with time ≤ D)` for any `D`, including `D = now` (which is exactly how
`GetAccountServiceImpl.deriveBalance()` computes the live balance from the raw, reversal-inclusive
rows). The two views are provably interchangeable for this purpose, not merely "close enough."

**Worked example**: a €40 bill on Aug 1 is corrected on Aug 15 to €50, keeping the same Aug 1 date.
Raw rows: original (Aug 1, €40), reversal (Aug 1, −€40), replacement (Aug 1, €50, current value
€50). Any cutoff `D ≥ Aug 1` sums the raw rows to `−40 + 40 − 50 = −50`; the correction-aware view
(replacement only) sums to `−50`. Identical. If the correction *also* moves the date to Aug 20:
raw rows are original (Aug 1, €40), reversal (Aug 1, −€40), replacement (Aug 20, €50). For a cutoff
`D` with `Aug 1 ≤ D < Aug 20`: raw sums to `−40 + 40 = 0` (replacement excluded, its time is after
`D`); correction-aware view also sums to `0` (the single replacement row is excluded on the same
basis). For `D ≥ Aug 20`: both views sum to `−50`. Identical in every case.

**Implication**: this feature needs no new backend endpoint and no raw/reversal-inclusive data. It
computes every historical net worth figure — and re-derives the current total, as a
cross-check — entirely from `allTransactions` (already fetched in full, correction-aware) and
`accounts` (already fetched, each carrying its live derived balance), both already sitting in
`useDashboardData.ts`'s state on every dashboard load.

## Decision: Historical net worth is derived by subtracting "the future" from the current total, not by re-summing "the past" from scratch

**Decision**: `computeNetWorthTrend()` starts from `currentTotalNetWorth = Σ(account.balance)` (the
value `GET /accounts` already computed and validated server-side) and, for each historical cutoff
`D`, computes `netWorth(D) = currentTotalNetWorth − Σ(income with time > D) + Σ(bills with time > D)`
— summing only the transactions *after* the cutoff, across every account, from `allTransactions`.

**Rationale**: This reuses the server-computed current total directly (one less thing to
independently get right in the frontend) rather than re-deriving it from `allTransactions` plus
each account's `startingBalance` — which isn't even exposed via any GET endpoint (`AccountDto`'s
`balance` field is overwritten in place by `deriveBalance()` before the API ever returns it, so the
frontend has no way to recover the original starting balance separately from the current total).
Subtracting "the future" is the only formulation available.

**Alternatives considered**: Re-summing every transaction with `time ≤ D` from scratch per
account — rejected because the frontend has no access to each account's `startingBalance` in
isolation (only the current already-derived total), making a from-scratch forward sum impossible
without a new endpoint to expose it. The chosen "subtract the future" formulation sidesteps that
gap entirely.

## Decision: Trend buckets are calendar months, cutoff = end of month except the most recent bucket, which cuts off at "now"

**Decision**: For a requested range of `monthsBack` months, the trend has `monthsBack` points: the
`monthsBack − 1` earliest points use a cutoff of the last instant of that calendar month (UTC,
mirroring 012's `T23:59:59.999Z` day-boundary-expansion pattern for inclusive end dates); the most
recent point uses `now` as its cutoff, so it always matches the live current total exactly rather
than projecting to a not-yet-complete month's end.

**Rationale**: Matches the spec's Assumptions section directly ("the most recent point reflecting
the current, live total") and is the standard shape for a "last N months" trend in any finance
app — nothing about "today" should show a value from before today just because the calendar month
isn't over.

**Alternatives considered**: Every bucket cut off at its own month's end, including the current
(partial) one — rejected because it would show a stale "as of the 1st" figure for the current
month even after new transactions have been recorded today, contradicting FR-002 ("reflects the
latest transaction and account data automatically").

## Decision: A hand-rolled inline SVG line chart, not a new charting library dependency

**Decision**: `NetWorthCard`'s trend visualization is a small custom SVG component (a `<polyline>`
scaled to the data's min/max, a `<circle>` per point, month labels below, value labels above) —
no `recharts`/`@mui/x-charts`/similar dependency is added.

**Rationale**: This is the first chart this app has ever rendered, and the need is genuinely
simple — a single series, at most 12 points, no zooming/panning/multi-series requirements. Every
prior feature in this app has treated "no new dependency" as the default to beat, adding one only
when the alternative was clearly worse (this app has none of that precedent for charts yet). At
this data size, hand-rolled SVG is fully tractable and keeps the bundle and the dependency surface
unchanged.

**Alternatives considered**: `@mui/x-charts` (natural pairing with the existing `@mui/material` v5
stack) — a reasonable choice, and worth reconsidering if a later feature needs a genuinely more
complex chart (multi-series, zoom, large datasets) where hand-rolled SVG would start showing its
limits; rejected here as more dependency than this feature's actual visual need justifies.
