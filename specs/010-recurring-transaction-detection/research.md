# Phase 0 Research: Recurring Transaction Detection

No `[NEEDS CLARIFICATION]` markers remain in the spec — the one open question (auto-detect from
history vs. scoped to the existing `recurring` flag) was resolved during `/speckit-specify`. This
phase also fixed one internal inconsistency found while grounding the plan in the spec (below),
and records the technical decisions the plan is built on.

## Spec consistency fix: occurrence threshold

**Found**: FR-002 said "at least two matching occurrences," but US2 Scenario 1's example uses
three, SC-002 says "at most 3 occurrences," and US2 Scenario 4 explicitly contrasts a
flag-assisted series ("eligible... with a second occurrence... without waiting for a third")
against an implied unassisted default of three.

**Fix**: FR-002 now reads "three matching occurrences — or two, if at least one of the matching
transactions was already marked with the existing `recurring` flag." This makes FR-002, US2
Scenario 1, US2 Scenario 4, and SC-002 all agree.

## Decision: Series membership is computed by re-matching, never stored on the transaction

**Decision**: A `RecurringSeries` row stores *what identifies* a series (transaction type,
category, description, cadence) and its confirm/dismiss decision — not a list of member bill/income
IDs. Every read (detection, upcoming prediction, price-change comparison) re-queries
`GetBillService.getAll()` / `GetIncomeService.getAll()` and matches transactions against the
series' stored criteria live.

**Rationale**: This is the same "derive, don't store" answer Principle III already gave account
balances (007) and envelope balances (009) — and it sidesteps a schema change to `bill`/`income`
entirely (no new foreign key column, no migration risk to the two tables every other feature also
touches). A stored membership list would also need updating every time a transaction is corrected
(008) or a new occurrence lands; re-matching by criteria needs no such bookkeeping.

**Alternatives considered**: A `recurring_series_id` FK column added to `bill`/`income` — rejected
as unnecessary schema coupling for a purely additive feature, and as another thing 008's
correction/removal paths would need to know to carry forward on the replacement row.

## Decision: Detection reads through `GetBillService`/`GetIncomeService`, not the raw SPI ports

**Decision**: Grouping and matching operate on `GetBillService.getAll()` / `GetIncomeService.getAll()`
— the Domain API services that already hide reversal rows and superseded originals, returning
exactly one row per logical transaction at its current (possibly corrected) value. Detection,
prediction, and price-change comparison never call `GetBillPersistencePort.getAll()` /
`GetIncomePersistencePort.getAll()` (the raw, reversal-inclusive SPI reads that `EnvelopeBalances`
and `GetAccountServiceImpl` use for *summing* amounts).

**Rationale**: Summing wants every row, including reversals, so corrections net out to zero.
Counting *occurrences* of a recurring charge wants the opposite: exactly one row per logical bill,
already carrying its corrected value, with the original and its reversal invisible. Using the raw
SPI read here would double- or triple-count a corrected bill as multiple "occurrences" and would
match against a stale pre-correction amount. `GetBillService.getAll()` already solves exactly
this, for exactly this reason, per its own doc comment ("Hides reversal rows and any row something
else supersedes, so a corrected bill shows as a single row carrying its current value").

**Alternatives considered**: Re-implementing reversal/supersession filtering inside this feature —
rejected as duplicating logic 008 already published as a reusable Domain service.

## Decision: Description matching is exact (case-insensitive, trimmed), not fuzzy

**Decision**: Two transactions are considered the same candidate series when they share a category
and their `description` fields are equal after trimming whitespace and lowercasing. Transactions
with a blank/null description are not matched into any series (excluded from detection, not
proposed as a series of one).

**Rationale**: This is a single-user personal-finance app where a recurring charge's description
is typically typed the same way each time (the same merchant name, the same bill label) — exact
normalized matching handles the realistic case with no dependency and no tunable false-positive
rate to get wrong. Fuzzy/similarity matching (edit distance, tokenization) is speculative
generality the spec doesn't ask for and the Constitution's Governance section warns against
("gold-plating ... is prohibited").

**Alternatives considered**: Fuzzy string similarity (e.g., Levenshtein-based) — rejected as
unnecessary complexity with no current requirement driving it; can be revisited if real usage
shows exact matching missing genuine recurring series.

## Decision: Cadence tolerance and amount tolerance, concretely

**Decision**: For a candidate run to match a cadence, each consecutive gap between occurrence
dates must fall within a per-cadence window: DAILY ±1 day (so effectively 0–2 days), WEEKLY ±2 days,
MONTHLY ±3 days, YEARLY ±10 days — scaling the ±3-day/month figure from the spec's Assumptions
roughly proportionally to interval length. For amount, two consecutive occurrences are "the same
charge" when the absolute difference is within whichever is larger: 5% of the prior amount, or
€2.00 (a fixed floor so tiny percentage tolerances don't make near-zero amounts meaninglessly
strict) — directly implementing the spec's Assumptions section. The same amount-tolerance check is
reused for detection's amount step and for US3's price-change flag; a difference within tolerance
is "the same," outside it is flagged.

**Rationale**: Concrete numbers were needed to write tests; these are conservative, easily
adjustable constants isolated in one place (`RecurringMatching`), not spread through the detection
algorithm.

**Alternatives considered**: A single flat day-tolerance regardless of cadence — rejected because
±3 days is generous for a daily cadence (300% of the interval) and stingy for a yearly one.

## Decision: Detection runs as an explicit action (`POST /recurring-series/detect`), not as a side effect of a GET

**Decision**: Scanning history and persisting newly found `PROPOSED` series happens in a dedicated
`POST /recurring-series/detect` endpoint, which the frontend calls once when the relevant dashboard
section mounts. `GET /recurring-series` and `GET /recurring-series/dashboard` are pure reads with
no side effects — they only ever reflect series that already exist in the `recurring_series` table.

**Rationale**: A GET that silently persists new rows is a REST-semantics smell (not safe/cacheable
in the way GETs are expected to be) and harder to reason about in tests. Making detection an
explicit POST keeps the read endpoints pure and gives the frontend (and tests) an obvious, single
point where "run detection now" happens — the same shape as 009's `/budgets/transfer` and
`/budgets/repeat`, which are actions, not queries.

**Alternatives considered**: A scheduled background job — rejected; this codebase has no job
scheduler today (Kafka is used for a different bounded-context event stream, not cron-style work),
and adding one is far more infrastructure than this feature's value justifies. Detecting on every
GET — rejected per the REST-semantics reasoning above.

## Decision: Price-change alerts are a standalone list, not an inline annotation on existing transaction rows

**Decision**: `GET /recurring-series/dashboard` returns `{ upcoming, recentPriceChanges }`.
`recentPriceChanges` is rendered as its own small list (a new `UpcomingRecurring` dashboard
section), not woven into `RecentTransactions.tsx`'s existing rows.

**Rationale**: `RecentTransactions.tsx` is already a fairly involved component (correct/remove/
history actions per row); threading a new recurring-price-change badge through it for a feature
that only applies to a subset of rows adds coupling for a marginal presentation win. A standalone
"price changed" list still satisfies US3 ("the new occurrence is flagged") — the transaction is
visibly called out to the user — without touching a component this feature doesn't otherwise need.

**Alternatives considered**: Inline badges on `RecentTransactions` rows — rejected as unnecessary
coupling; can be revisited later if a standalone list proves hard to notice in practice.
