# Research: Bill/Income Correction Flow

**Feature**: 008-bill-income-correction
**Date**: 2026-08-23

No `[NEEDS CLARIFICATION]` markers remained in the Technical Context. This document records the
key design decisions — the reversal representation is the crux of the whole feature, so it gets
the most scrutiny.

## Decision 1: Represent a reversal as a negative-amount row of the same type

**Decision**: A reversal is an ordinary `Bill` or `Income` row — same `categoryId`/`accountId`,
same currency, same `time` as the transaction it reverses — except its `amount` is the **negation**
of the original amount, and it carries `reversal = true` plus `correctsTransactionId = <id being
reversed>`. A correction posts two new rows (a reversal of the old value, and a normal positive-
amount replacement with the fixed values, dated per the user's corrected date). A removal posts
only the reversal row.

**Rationale**: Every existing aggregation service in this codebase already sums bill/income amounts
with a plain `BigDecimal::add` reduction:
- `GetSpendingAnalysisServiceImpl.getMonthlySummary` — `totalExpenses`/`totalIncome` and
  `spendingByCategory` are all `reduce(BigDecimal.ZERO, BigDecimal::add)` over amounts, grouped by
  `categoryId` for the per-category map.
- `GetBudgetStatusServiceImpl.getBudgetStatus` — `actualMap` is the same
  `groupingBy(categoryId, reducing(ZERO, amount, add))` pattern.
- `GetAccountServiceImpl.deriveBalance` (from 007) — `startingBalance.add(totalIncome).subtract(totalBills)`,
  where `totalIncome`/`totalBills` are themselves plain `reduce(ZERO, add)` over amounts filtered by
  `accountId`.

Because a reversal keeps the same `categoryId`/`accountId` as the original, and its amount is
negative, **all three of these services net the reversal to zero automatically, with zero code
changes**, as long as the reversal is dated the same as the original (so it lands in the same
month/period as what it's cancelling out) and the replacement is dated per its own corrected date
(so it lands wherever the corrected date falls — this is exactly the cross-month edge case
described in spec.md, and it falls out of the design for free).

**Alternatives considered**:
- **A `reversal`-aware sign flip inside each aggregation service** (i.e., keep reversal amounts
  positive but have `GetSpendingAnalysisServiceImpl`/`GetBudgetStatusServiceImpl`/
  `GetAccountServiceImpl` each special-case `reversal == true` to subtract instead of add):
  rejected — this means touching three existing, already-tested Domain services and their tests for
  no benefit over the negative-amount approach, which achieves identical correctness by construction
  with a one-line convention (negate the amount) instead of a systemic special case repeated three
  times.
- **A full double-entry journal-line model** (separate debit/credit rows per transaction): rejected
  as a major architectural change with no other driver in this app — Principle II is already treated
  as N/A for this codebase's bill/income model (see 001/002/007's Constitution Checks), and
  introducing it here for one feature would be exactly the kind of gold-plating the Constitution's
  Governance section prohibits.

## Decision 2: Filter reversal/superseded rows only at the display layer

**Decision**: `GetBillPersistencePort.getAll()` / `GetIncomePersistencePort.getAll()` (the SPI ports
used internally by `GetSpendingAnalysisServiceImpl`, `GetBudgetStatusServiceImpl`, and
`GetAccountServiceImpl` for aggregation) are **untouched** and keep returning every row, reversals
included — that's what makes Decision 1's automatic netting work. Only `GetBillServiceImpl.getAll()`
/ `GetIncomeServiceImpl.getAll()` (the Domain services backing the human-facing `GET /bills` /
`GET /incomes` list endpoints, which feed the dashboard's Recent Transactions) filter their result:
a row is hidden if `reversal == true`, or if any other row's `correctsTransactionId` equals its id
(meaning something has superseded it — either a correction's replacement, or a removal's reversal).

**Rationale**: This single "hidden if referenced by anything, or if it's a reversal" rule uniformly
covers both User Story 1 (a corrected original is superseded by its replacement, so the original
hides and the replacement shows) and User Story 2 (a removed original is superseded by its
reversal with no replacement, so the original hides and nothing takes its place) — no separate
"why was this superseded" branch is needed. It also naturally covers correction chains: correcting
an already-corrected row supersedes that row the same way, so only the newest replacement is ever
visible.

**Alternatives considered**:
- **A `visible`/`status` column updated in place on the superseded row**: rejected — this is exactly
  the kind of "silently modified after persisted" operation Principle I forbids. Deriving visibility
  by querying "is anyone referencing me" at read time keeps every row's own data permanently
  unchanged from the moment it's written.

## Decision 3: New endpoints reuse the app's existing Account CRUD verb conventions

**Decision**: `PUT /bills/{id}` and `PUT /incomes/{id}` mean "replace the current value" (a
correction); `DELETE /bills/{id}` and `DELETE /incomes/{id}` mean "remove" (internally: reversal
only, no replacement); `GET /bills/{id}/history` and `GET /incomes/{id}/history` return the prior
value chain.

**Rationale**: The app already has `PUT /accounts/{id}` (update) and `DELETE /accounts/{id}`
(delete) — reusing the same verbs for bill/income keeps the API surface predictable and consistent
across resource types, rather than inventing custom action sub-resources
(e.g. `POST /bills/{id}/correct`). The fact that these verbs are implemented via reversal internally
instead of an in-place mutation is a Domain/Infrastructure detail the API consumer doesn't need a
different verb to know about — from the client's perspective, `PUT` still means "here is this
resource's new value" and `DELETE` still means "this resource should no longer show up."

**Alternatives considered**:
- **Custom action sub-resources** (`POST /bills/{id}/correct`, `POST /bills/{id}/remove`):
  considered, since it makes the reversal-based implementation more visible in the contract itself.
  Rejected in favor of verb reuse for consistency with the existing Account endpoints and because
  the spec's user-facing behavior ("replace this transaction's value" / "remove this transaction")
  maps directly onto standard REST semantics without needing a custom verb to express it.

## Decision 4: History reconstruction walks `correctsTransactionId` backward from the current row

**Decision**: `GetBillService.getHistory(id)` / `GetIncomeService.getHistory(id)` take the id of the
**currently visible** row, then repeatedly follow `correctsTransactionId` to the row it replaced,
building a list until `correctsTransactionId` is `null` (the true original). Reversal rows are never
part of this chain — a replacement's `correctsTransactionId` always points to the prior *value* row
it replaced, never to a reversal row, so no filtering is needed inside the walk itself.

**Rationale**: This directly satisfies User Story 3 with no new persisted "history" table — the
chain is fully reconstructable from data the correction flow already writes. Since removal never
produces a replacement, a removed transaction has no visible current row to call `getHistory` on in
the first place (consistent with FR-011's visibility rule making it disappear entirely).

**Alternatives considered**:
- **A dedicated `bill_correction_history` audit table**: rejected as unnecessary duplication — the
  `bill`/`income` tables themselves already are the audit trail once `correctsTransactionId` exists;
  adding a parallel table would just be two sources of truth to keep in sync.
