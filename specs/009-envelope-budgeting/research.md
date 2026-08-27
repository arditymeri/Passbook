# Phase 0 Research: Envelope Budgeting

No `[NEEDS CLARIFICATION]` markers remain in the spec — the one open question (rolling carryover
vs. fresh-start-each-month) was resolved during `/speckit-specify`. This phase records the technical
decisions the plan is built on, each grounded in an existing pattern already in the codebase rather
than a new idiom.

## Decision: Reuse the existing `Budget` entity as the allocation store, unchanged

**Decision**: No rename, no schema change. `Budget` (categoryId, year, month, limitAmount) becomes,
in this feature's vocabulary, "the amount assigned to a category in a month." The upsert semantics
from feature 002 (`SetBudgetServiceImpl`) are exactly what FR-003 needs.

**Rationale**: The spec's own Key Entities section describes "Allocation" as "a reframing of the
existing Budget entity," not a new one. Renaming `Budget` → `Allocation` throughout the codebase
would touch every 002-era file for no behavioral gain and would be exactly the kind of scope creep
the project's own conventions warn against.

**Alternatives considered**: A parallel `Allocation` entity that 002's `Budget` gets migrated into —
rejected as unnecessary churn; a rename of the Java classes only (keeping the DB column names) —
rejected as inconsistent (code says "Allocation," schema and API still say "budget") for no
compensating benefit.

## Decision: Cumulative (not month-scoped) balance derivation, following the 007 account-balance pattern

**Decision**: Unallocated balance and per-category envelope balance are computed the same way
`GetAccountServiceImpl.deriveBalance` computes an account's balance: sum everything relevant up to
a point in time, in memory, at read time. No stored running total.

**Rationale**: This is a direct precedent already in the codebase for exactly this shape of problem
(a derived balance that must reflect the full transaction history, immune to a corrected or removed
underlying row). Reusing it means the new envelope-balance logic inherits 007's already-reviewed
answer to "how do we do derived balances in this codebase" instead of inventing a second one.

**Alternatives considered**: A month-scoped calculation that separately sums a "carried-in" balance
from the prior month — rejected because it would require a recursive/cached carry-forward figure
per category per month (exactly the "mutable running total" Principle III forbids) instead of one
full-history scan.

## Decision: Reversal-netting requires no new code

**Decision**: `EnvelopeBalances` (the new shared derivation helper) reads bills and incomes via
`GetBillPersistencePort.getAll()` / `GetIncomePersistencePort.getAll()` — the same raw,
reversal-inclusive read `GetAccountServiceImpl` already uses — and simply sums amounts.

**Rationale**: Feature 008's correction/removal mechanism represents a correction as a same-category,
same-account, negated-amount row (`BillCorrections.reversalOf`). Any code that sums bill amounts by
category therefore already nets corrections and removals out to zero, with no reversal-aware
branching required. `GetBudgetStatusServiceImpl`'s existing month-scoped `actual` figure already
relies on this same property (via `GetSpendingAnalysisPersistencePort.getBillsByPeriod`, which reads
the same underlying rows). The cumulative envelope-balance figure this feature adds is the same
computation over a wider (unbounded-start) window.

**Alternatives considered**: Filtering out reversal rows and separately subtracting corrected
amounts — rejected as unnecessary; the existing data model already makes summation correct.

## Decision: Model "move money between categories" as a new immutable record, not a signed delta

**Decision**: A new `AllocationTransfer` (fromCategoryId, toCategoryId, year, month, amount,
createdAt) is written per move; never mutated. Envelope balance = allocated-to-date + transfers-in-
to-date − transfers-out-to-date − spent-to-date. The overall unallocated total is unaffected by
transfers by construction (they don't touch allocation totals, only how much of the total is
attributed to which category).

**Rationale**: `SetBudgetServiceImpl` has enforced `limitAmount > 0` since feature 002. Writing a
negative delta into that same field to represent "money left this category" would either break that
invariant or require carving out a special case for it — and it would make a transfer
indistinguishable, in the stored data, from the user simply having assigned a smaller amount that
month. Feature 008 already established the codebase's answer to "how do we record an adjustment
without mutating or overloading an existing row": append a new, clearly-typed row and derive the
net effect at read time. This feature follows that precedent instead of inventing a second one.

**Alternatives considered**: Two `Budget` upserts (decrement source month, increment destination
month) — rejected per the reasoning above; a generic "ledger entry" abstraction covering both
transfers and future adjustment types — rejected as speculative generality not needed by any current
requirement (Constitution Governance: "gold-plating or speculative generality is prohibited").

## Decision: "Repeat last month" has no dedicated preview endpoint

**Decision**: FR-010's "warn before overwriting/topping up" (US4 Scenario 2) is satisfied entirely
client-side. The frontend already needs `GET /budgets?year=&month=` for both the source and target
month to render the budgeting view; it computes the preview (which categories will receive a
top-up, and the resulting total) from those two already-fetched lists before the user confirms, then
calls `POST /budgets/repeat` to apply.

**Rationale**: The two months' allocation lists are small (bounded by category count, realistically
under a few dozen rows) and already available from an existing, unmodified endpoint. A dry-run
backend endpoint would duplicate that computation server-side for no correctness benefit — the kind
of unrequested abstraction the project's guidelines call out to avoid.

**Alternatives considered**: A `POST /budgets/repeat/preview` dry-run endpoint returning the same
shape without persisting — rejected as unnecessary given the source data is already client-side.

## Decision: Category-type restriction (FR-009) is enforced in the Domain layer, not just the UI

**Decision**: `SetBudgetServiceImpl` gains a check — reject allocations targeting a category whose
type is `INCOME` (only `EXPENSE` and `BOTH` are valid allocation targets) — alongside the existing
frontend filter that keeps INCOME categories out of the allocation picker in the first place.

**Rationale**: The frontend filter alone is a UX convenience, not a guarantee — Swagger UI or any
future API consumer can still call `POST /budgets` directly, exactly how every budget has been
created up to now. Every other validation rule in `SetBudgetServiceImpl` (limit > 0, month in
1–12, category exists) is enforced server-side; this one should be too, for consistency.

**Alternatives considered**: UI-only enforcement — rejected as inconsistent with the rest of the
service's existing validation posture.
