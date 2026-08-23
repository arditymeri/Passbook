# Research: Accounts Page

**Feature**: 007-accounts-page
**Date**: 2026-08-23

No `[NEEDS CLARIFICATION]` markers remained in the Technical Context — the codebase already has
enough established precedent (001, 002, 005, 006) to resolve every open question by pattern-matching.
This document records the decisions and why the existing pattern was reused instead of inventing a
new approach.

## Decision 1: Where and how to compute the derived account balance

**Decision**: Add `GetBillPersistencePort` and `GetIncomePersistencePort` as constructor
dependencies of `GetAccountServiceImpl`. For every `AccountDto` returned by `getAll()`,
`getByType()`, and `getAccountById()`, compute:

```
derivedBalance = (startingBalance ?? 0)
                 + sum(income.amount where income.accountId == account.id)
                 − sum(bill.amount where bill.accountId == account.id)
```

using `GetBillPersistencePort.getAll()` / `GetIncomePersistencePort.getAll()`, filtered in-memory
by `accountId`, then reduced with `BigDecimal::add` — and overwrite `AccountDto.balance` with the
result before returning it to the caller. The underlying `account.balance` column is untouched;
it continues to represent the starting balance set at account creation.

**Rationale**: This mirrors the exact pattern already used by `GetBudgetStatusServiceImpl` (sums
`BillDto` amounts grouped by `categoryId`, sourced via a persistence port's `getAll`-style method)
and `GetSpendingAnalysisServiceImpl` (sums bill/income amounts via `BigDecimal::add` reduction).
Both are existing, tested Domain services doing in-memory aggregation over full or period-filtered
result sets — there is no precedent in this codebase for pushing aggregation into a repository
query, and introducing one here would be an unjustified architectural divergence for a
personal-scale dataset (hundreds, not millions, of rows). It also satisfies Constitution Principle
III directly: the balance is computed by summing transaction lines, never trusted as a stored
mutable total.

**Alternatives considered**:
- **New JPQL aggregate query** (`@Query("SELECT SUM(b.amount) FROM BillEntity b WHERE b.accountId = :id")`
  on `BillRepository`/`IncomeRepository`): more efficient at scale, but no existing Domain service
  in this codebase delegates aggregation to SQL — `GetBudgetStatusServiceImpl` and
  `GetSpendingAnalysisServiceImpl` both aggregate in Java. Introducing a new pattern here for one
  feature would be inconsistent gold-plating; can be revisited later if account lists grow large
  enough to matter (out of scope per Constitution Governance: complexity must be justified).
- **Materialized/cached balance column updated on every bill/income write**: rejected outright —
  this is precisely the "mutable running total" Constitution Principle III forbids unless
  invalidated atomically with the write, which would require wrapping bill/income creation in a
  cross-aggregate transaction that touches the `account` table. Read-time derivation avoids that
  entirely and is simpler and safer for a first version.

---

## Decision 2: Exposing `accountId` on the Bill API contract

**Decision**: Add an optional `accountId` (string) property to the shared `bill` schema in
`Application/src/main/resources/swagger/bill/bill-model.yaml`. No new schema, no new endpoint.

**Rationale**: `BillDto` (Domain) and `BillEntity`/`bill.account_id` (Infrastructure) already
carry `accountId` — it simply was never surfaced through the OpenAPI contract. `IncomeDto` and the
`income` OpenAPI schema already expose `accountId` end-to-end, confirming this is an established,
intentional field, not a special case being invented here. Because `bill` is used as both the
create-request and response schema, one field addition covers both directions. MapStruct's
`BillMapper` (`Bill map(BillDto)` / `BillDto map(Bill)`) auto-maps by field name with no manual
`@Mapping` needed, since both sides will have a matching `accountId` property once the OpenAPI
model is regenerated.

**Alternatives considered**:
- **Separate `createBillRequest` schema distinct from the response `bill` schema** (as `income`
  does with `createIncomeRequest`/`incomeResponse`): would be more consistent with the income
  pattern, but changing `bill`'s existing single-schema shape is out of scope for this feature —
  it would touch every existing bill consumer for no benefit tied to this spec's requirements.
  Adding one field to the existing shared schema is the minimal, additive change.

---

## Decision 3: Frontend structure — mirror 005 exactly

**Decision**: Build `AccountsPage.tsx` / `AccountList.tsx` / `AddAccountForm.tsx` as structural
mirrors of `CategoriesPage.tsx` / `CategoryList.tsx` / `AddCategoryForm.tsx`, and a `useAccounts.ts`
hook mirroring `useCategories.ts`. Reuse the existing `Modal` (MUI `Dialog` wrapper) for the create
form, the existing empty-state pattern (`Typography` + CTA `Button`), the existing `Skeleton`
loading and `Alert` error states, and the existing `ToggleButtonGroup` filter pattern for the
type filter (User Story 4).

**Rationale**: This was an explicit instruction in the feature description ("mirroring the
categories page pattern from 005"), and 005 is the most recent, fully-MUI-styled precedent for a
list+create page with a type filter — exactly this feature's shape. Reusing the pattern keeps the
new page visually and behaviorally consistent with the rest of the app (satisfying the spirit of
006's "consistent Material Design look" goal) without any new design decisions.

**Alternatives considered**: None seriously considered — the user's own instruction plus the
006 migration's stated goal of visual consistency make this the only reasonable choice.

---

## Decision 4: Currency formatting for account balances

**Decision**: Format each account's balance with `new Intl.NumberFormat('de-AT', { style:
'currency', currency: account.defaultCurrency })`, i.e. per-account formatting using that
account's own `defaultCurrency`, rather than a single app-wide formatter.

**Rationale**: The existing dashboard formatter in `SummaryCard.tsx` hardcodes `currency: 'EUR'`
because every dashboard figure is already in the app's implicit single currency. Accounts,
however, can each declare a different `defaultCurrency` (the account model supports multi-currency
accounts), so a single hardcoded formatter would mislabel non-EUR account balances. Per-account
formatting is a one-line deviation from the `SummaryCard` pattern, not a new abstraction.

**Alternatives considered**:
- **Reuse the single hardcoded EUR formatter for all accounts**: rejected — would silently
  mislabel a USD or CHF account's balance as EUR, which is a correctness issue, not a style one.

---

## Decision 5: Negative-balance visual treatment

**Decision**: Reuse the existing `error.main` / default text color convention already used in
`SummaryCard.tsx` and `RecentTransactions.tsx` (bill amounts in `error.main`) — negative account
balances render with `sx={{ color: 'error.main' }}`, zero/positive balances use the default
`Typography` color.

**Rationale**: FR-011 requires negative balances to be visually distinguished; the app already has
an established red-for-negative / default-for-positive convention from the 006 MUI migration.
Reusing it avoids introducing a second color convention (e.g., a `Chip` like `BudgetStatus` uses)
for what is a simpler, single-value case than budget over/under status.

**Alternatives considered**:
- **`Chip color="error"/"success"`** (the `BudgetStatus` pattern): considered, but that pattern
  exists for a discrete two-state badge (UNDER/OVER budget) sitting next to a progress bar — an
  account balance is a continuous currency figure, not a discrete state, so a colored `Typography`
  value (matching `SummaryCard`'s income/expense treatment) is the closer fit.
