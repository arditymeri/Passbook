# Phase 0 Research: Transaction Search & Filtering

No `[NEEDS CLARIFICATION]` markers remain in the spec — every open design question was resolved
with a documented default in the spec's Assumptions section during `/speckit-specify`. This phase
turns those defaults into concrete, testable decisions, and records one significant finding made
while grounding the plan in the actual codebase.

## Finding: the full, correction-aware transaction history is already fetched on every dashboard load

**Found**: `useDashboardData.ts` already calls `fetchBills()` and `fetchIncomes()` with no
parameters on every load — both already return the *complete* history (`GetBillService.getAll()`
and `GetIncomeService.getAll()` are unfiltered by date; the `GET /bills?date=` query parameter
exists in the OpenAPI contract but `BillGetController.listBills()` never reads it). The hook then
merges bills+income into `Transaction[]`, filters to the currently selected month, sorts, and
takes only the ten most recent — for display on the dashboard's `RecentTransactions` table.
Critically, `GetBillService.getAll()`/`GetIncomeService.getAll()` already hide reversal rows and
any row a correction supersedes (confirmed in `GetBillServiceImpl.getAll()`'s doc comment and
implementation), so the data already sitting in the browser after every dashboard load is exactly
"one row per logical transaction, at its current corrected value" — precisely what FR-012 requires.

**Implication**: this feature needs no new backend endpoint, no new Domain service, and no new
persisted state. It only needs the *unsliced, unfiltered-by-month* version of what
`useDashboardData` already fetches, plus client-side matching logic and a filter-bar UI.

## Decision: No new backend endpoint — filter entirely client-side over already-fetched data

**Decision**: `useDashboardData.ts` is extended to also return `allTransactions: Transaction[]` —
the full merged bill+income list, sorted newest-first, with no month filter and no `.slice(0, 10)`
cap. A new pure function, `filterTransactions(transactions, filters)`, applies the search text and
every active filter to that array. No `GET /transactions/search` (or similar) endpoint is added.

**Rationale**: The data this feature needs is already loaded into the browser on every dashboard
visit; a backend search endpoint would re-implement `GetBillService.getAll()` /
`GetIncomeService.getAll()`'s existing filtering-free reads for no benefit, and at this app's
personal scale (hundreds of rows, the same assumption 007/009/010/011 already rely on) there is no
performance reason to move matching server-side. Introducing one would be the kind of speculative
generality the Constitution's Governance section prohibits.

**Alternatives considered**: A new `GET /transactions/search` endpoint accepting query parameters
for each filter — rejected as redundant backend surface for data the frontend already holds in
full; would also require a new combined-Bill+Income Domain-level read service that doesn't
otherwise exist, for zero net capability gain over filtering the array that's already in memory.

## Decision: `allTransactions` is derived from the same fetch `useDashboardData` already performs

**Decision**: The hook's existing `fetchBills()`/`fetchIncomes()` calls are reused — `allTransactions`
is computed from the same `bills`/`incomes` arrays the hook already has in scope, just without the
`inMonth(...)` filter and `.slice(0, 10)` cap applied to the existing `transactions` value. No
second network round trip is introduced.

**Rationale**: Avoids doubling the number of requests the dashboard makes on every load and every
month navigation; the full data was already one filter-step away from what the hook already
computes.

**Alternatives considered**: A separate hook with its own `fetchBills()`/`fetchIncomes()` calls —
rejected as a wasteful duplicate fetch of data that's already being retrieved.

## Decision: Category and income-source are one combined filter control, backed by two independent filter fields

**Decision**: `TransactionFilters` carries `categoryId?: string` and `source?: IncomeSource`
as independent, AND-combined fields (per FR-003/FR-004 and the spec's edge case, which treats
"both set at once" as a valid-but-empty-result combination, not an error). The UI, however, exposes
them through a single "Category / Source" dropdown listing bill categories and income sources
together — selecting one clears the other, since a user has no reason to ever want both set
simultaneously (it always yields zero results, per the spec's own edge case).

**Rationale**: Matches the functional requirement exactly while not building a UI that invites a
combination that can only ever be unhelpful. The underlying `filterTransactions()` logic still
treats them as two independent predicates (so the edge case's documented behavior — "valid but
unhelpful, not an error" — holds even if the two fields were ever set together some other way),
keeping the implementation simple and the UI focused.

**Alternatives considered**: Two fully independent dropdowns (one for category, one for income
source) — rejected as needless UI surface for a combination with no legitimate use.

## Decision: Live filtering, no debounce, no pagination

**Decision**: Every filter/search-text change immediately re-runs `filterTransactions()` over
`allTransactions`. No debounce timer, no result-count cap beyond what's already implied by the
data's personal scale.

**Rationale**: `Array.prototype.filter` over a few hundred rows is computationally trivial —
debouncing would add complexity to solve a performance problem that doesn't exist at this app's
scale. Matches the spec's Assumptions section ("results update as the user types... no separate
search button required").

**Alternatives considered**: Debounced search input — rejected as unnecessary; can be revisited if
real usage ever shows this app's transaction volume growing far beyond "hundreds of rows."

## Decision: No new automated tests for `filterTransactions()`

**Decision**: Verification is `tsc --noEmit` (type safety) plus the manual `quickstart.md`
walkthrough — no unit test file is added for the new filter-logic module.

**Rationale**: This codebase's `frontend/` has never had a test runner — no `vitest`/`jest`/
`@testing-library` dependency exists in `package.json`, and no `*.test.ts`/`*.spec.ts` file exists
anywhere in `frontend/src/`. Every prior feature's plan.md already documents "TypeScript type-check
+ manual browser smoke-test" as this app's frontend-testing convention; this feature follows that
existing, already-established convention rather than introducing test infrastructure as an
incidental side effect of one filter function. The Constitution's Test-First principle (VI) is
explicitly scoped to "financial calculation and business-rule logic in the Domain module" — this
feature adds no Domain code.

**Alternatives considered**: Introducing `vitest` for this one function — rejected as
disproportionate, out-of-scope infrastructure work for a single-feature PR; worth doing as its own
dedicated cross-cutting initiative if the team wants frontend unit tests going forward, not as a
byproduct of this feature.
