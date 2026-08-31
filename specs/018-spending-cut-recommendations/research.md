# Research: Spending Cut Recommendations

## R1: Where does the necessity tag live?

**Decision**: Add a new, dedicated nullable field `necessityTag` (enum `NECESSARY | AVOIDABLE | UNNECESSARY`) to `BillDto` and a matching `necessity_tag` column on `BillEntity`.

**Rationale**: `BillDto` already declares a `tags: List<String>` field, but it is never mapped onto `BillEntity` (`BillMapper` only maps fields present on both types) and is therefore dead — the same vestigial pattern the codebase already has for `recurring`/`recurringFrequency` before feature 010 wired them up. Repurposing `tags` for a single, mutually-exclusive, fixed-vocabulary label would be a poor semantic fit (it reads as free-form multi-tagging) and would still require adding real persistence to `BillEntity` regardless. A dedicated field is simpler to validate, index, and query, and matches how other fixed-choice fields (`paymentMethod`) are already modeled.

**Alternatives considered**: Wire up the existing `tags: List<String>` field generically (rejected — wrong shape for a fixed single-value enum, and still requires the same amount of new persistence work); a separate `TransactionTag` entity with a foreign key to `bill` (rejected — no current requirement needs one bill to carry more than one necessity tag, or tags on anything other than bills; a join table is unjustified complexity per the YAGNI guidance in CLAUDE.md).

## R2: Does tagging conflict with Constitution Principle I (Transaction Immutability)?

**Decision**: No — a narrowly-scoped, in-place update path that touches only the `necessity_tag` column, separate from the existing correction/reversal mechanism used for `amount`/`time`/`categoryId`/`accountId`.

**Rationale**: Principle I's own stated rationale is that "an imported bank booking is a statement of fact... corrections are opinions layered on facts. Keeping them distinct is what makes re-importing safe." A necessity tag is not a claim about what happened (amount, date, account, category) — it is the user's own separate judgment about a fact that remains true. `UpdateCategoryServiceImpl`/`UpdateCategoryPostgresAdapter` already establish the in-place-update pattern in this codebase for non-transactional, descriptive fields; the same shape is used here, scoped to one Bill and one column. The existing correction/reversal path (`CorrectBillServiceImpl`, `BillCorrections`) remains the *only* way to change a bill's financial facts — this feature does not touch it except for one addition (see R3).

**Alternatives considered**: Route tag changes through the full correction/reversal mechanism (rejected — would create two new bill rows per tag change, multiplying row counts and corrupting every aggregation that assumes reversal pairs represent a real financial correction, for no benefit; a tag is not a financial fact being corrected).

## R3: What happens to a tag when its bill is corrected or removed?

**Decision**: `CorrectBillServiceImpl.replacement(...)` carries the current bill's `necessityTag` forward onto the replacement row. `RemoveBillServiceImpl`'s reversal has no replacement, so a removed bill's tag simply stops appearing (consistent with the bill itself disappearing from `GetBillService.getAll()`).

**Rationale**: A correction changes amount/date/category but the user's "was this worth it" judgment about the underlying purchase doesn't change just because a typo in the amount was fixed. Spec edge case: "The tag stays attached to the transaction [when corrected]. If the transaction is deleted, its tag is discarded along with it" — this one-line addition is what makes that true; without it, tags would be silently dropped on every correction.

## R4: How to compute the recurring-cost ranking and price-creep signal

**Decision**: A new Domain service reuses the existing `RecurringSeriesMembers` component (already documented as "shared by `GetUpcomingRecurringServiceImpl`... and the cash flow forecast, so both agree on exactly which transactions belong to a series" — this feature is a third, intended consumer). For each `CONFIRMED` series: `originalAmount` = first member's amount, `currentAmount` = last member's amount, monthly-equivalent = `currentAmount × occurrencesPerMonth(frequency)`. Price increase is flagged using the existing `RecurringMatching.isWithinAmountTolerance(originalAmount, currentAmount)` check (only flagged when outside tolerance *and* `currentAmount > originalAmount`), reusing the same 5%-or-€2 tolerance rule already applied elsewhere so a series isn't flagged from rounding noise.

**Rationale**: `RecurringSeriesMembers` is explicitly designed to be shared so every recurring-aware feature agrees on series membership; reusing it (rather than re-deriving groupings from raw bills in the frontend) avoids a second, drift-prone implementation of the detection/grouping rules. The ranking and creep computation are genuinely new business rules (not just a reshape of existing output) and belong in Domain per Constitution Principle VI (test-first Domain coverage for business-rule logic).

**Why not reuse the existing `GET /recurring-series/dashboard` (`upcoming`/`recentPriceChanges`) as-is**: `upcoming[].predictedAmount` is the latest occurrence's *raw* amount, not normalized to a monthly-equivalent — fine for "when is the next charge," misleading for "rank by monthly cost" across mixed DAILY/WEEKLY/MONTHLY/YEARLY frequencies. `recentPriceChanges` compares only the two most recent occurrences (a single-step "did the last charge change" alert) — a different, still-useful signal that this feature leaves untouched, not the cumulative "has this crept up since it started" signal the spec calls for. Both existing computations stay as they are; this feature adds one new, separately-scoped Domain service rather than overloading either.

**Alternatives considered**: Extend `GetUpcomingRecurringServiceImpl` in place (rejected — mixes two distinct concerns, next-occurrence forecasting vs. cost ranking/cumulative creep, in one class, and its existing `recentPriceChanges` semantics are deliberately different from what's needed here); recompute series groupings from raw bills in the frontend (rejected — duplicates `RecurringMatching`'s grouping/tolerance rules in TypeScript, which would drift from the backend's actual detection logic over time).

## R5: How to compute the over-budget and trending-up category signals

**Decision**: Reuse existing, already-exposed computations rather than adding new backend logic.

- **Over-budget**: call the existing `GetBudgetStatusService.getBudgetStatus(year, month)` (`GET /budgets/status?year&month`) for the most recently *completed* calendar month, filter entries where `status == OVER_BUDGET`, and use `actual - budgeted` (i.e. `-remaining`) as the excess.
- **Trending-up**: reuse the existing frontend `computeSpendingTrends(allTransactions, categoryNames, monthsBack).movers` (feature 016), filtering to `change > 0` (current month higher than previous), using `change` as the excess-vs-typical amount.

**Rationale**: Both signals already exist and are already unit/hand-verified (`GetBudgetStatusServiceImpl`, `spendingTrends.ts`). Recomputing either would be pure duplication with no new value, directly against the CLAUDE.md guidance not to introduce abstractions beyond what the task requires. This also matches the original feature description's own framing: "analyzing data the app already computes."

**Alternatives considered**: A new unified backend "category opportunity" endpoint that internally calls budget-status and re-implements the trend comparison (rejected — the trend comparison already lives correctly in the frontend for 016; moving it to the backend just to keep this one feature "backend-only" would fork the trend logic into two places that must be kept in sync).

## R6: Where the combined view and "potential monthly savings" total are assembled

**Decision**: A new frontend utility (`computeSpendingCutRecommendations`, mirroring the shape of `computeSpendingTrends`) takes: the new recurring-cost-summary response, `allTransactions` (for tagged-transaction filtering and reuse by `computeSpendingTrends`), `categoryNames`, and the most-recently-completed-month `BudgetStatusEntry[]`. It performs the P1 ranking/sort, P2 tagged-transaction filtering, P3 price-creep pass-through, P4 category dedup (a category appearing in both over-budget and trending-up is emitted once, preferring the larger of the two excess amounts as its displayed figure, summed only once), and the final combined total. This mirrors 015/016/017's established pattern of a pure, independently-verifiable TS function feeding a presentational component, keeping the new page a thin display layer.

**Rationale**: Every input this function needs is already available client-side once R4's new endpoint exists — no reason to also do the merge/dedup/total server-side, which would require a second, harder-to-independently-verify combination of three distinct data sources.

## R7: New REST surface summary

Two additions, both additive (no breaking changes, per Constitution Principle VII):

1. `PUT /bills/{id}/necessity-tag` — sets or clears (nullable body field) a bill's necessity tag. New `bill-necessity-tag-controller.yaml`; `necessityTag` added to the shared `bill` schema in `bill-model.yaml` so it round-trips through the existing `GET /bills` list the frontend already fetches — no new "list tagged bills" endpoint needed.
2. `GET /recurring-series/cost-summary` — one entry per `CONFIRMED` series: `seriesId`, `description`, `monthlyEquivalentAmount`, `originalAmount`, `priceIncreased`, `increaseAmount`. New `recurring-cost-summary-controller.yaml`; new schemas added to the existing `recurring-model.yaml`.

No other new backend endpoints. Category signals and the combined total are assembled client-side from these two additions plus the existing `GET /budgets/status` and `GET /bills`/`GET /incomes`.
