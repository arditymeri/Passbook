# Data Model: Spending Cut Recommendations

## New persisted field

### Bill.necessityTag

- **Type**: nullable enum `NecessityTag { NECESSARY, AVOIDABLE, UNNECESSARY }`
- **Where**: `Domain/.../domain/data/bill/BillDto.java` (new field) → `Infrastructure/.../entity/BillEntity.java` (new `necessity_tag` nullable `varchar` column, no default) → `BillMapper` picks it up automatically (MapStruct maps same-named fields; both sides gain the field, so no mapper changes needed beyond adding it to both classes).
- **Default**: `null` (untagged) for every existing and newly created bill. Hibernate `ddl-auto=update` adds the column as nullable, so no backfill is needed and no existing row is affected.
- **Mutability**: the one field on `Bill` that is updated in place, outside the correction/reversal mechanism (see research.md R2). All other fields remain governed by Constitution Principle I exactly as today.
- **Propagation on correction**: `CorrectBillServiceImpl.replacement(current, correctedValues)` copies `current.getNecessityTag()` onto the replacement row, so a tag survives a correction of amount/date/category/account (research.md R3). It is intentionally *not* copied onto `BillCorrections.reversalOf(...)` — a reversal is a system-generated bookkeeping row, not a user-facing transaction, and never appears in `GetBillService.getAll()`.
- **Scope**: bills (expenses) only. `IncomeDto`/`IncomeEntity` are untouched — tagging is not offered for income.

### Validation rules

- Value MUST be one of `NECESSARY`, `AVOIDABLE`, `UNNECESSARY`, or absent/`null` (untagged). No free text.
- Setting a tag on a bill that does not exist, or that is a reversal row, or that has been superseded by a correction (i.e. is not the current visible value) → `404 Not Found`, mirroring `CorrectBillServiceImpl`'s existing not-found handling for `id`.

## Derived (non-persisted) view models

These are computed at read time on every request/render — nothing here is cached or stored, consistent with Constitution Principle III's read-time-derivation posture applied to derived recommendation data generally.

### RecurringCostSummaryItem (backend, new)

One entry per `CONFIRMED` recurring series, computed by the new Domain service from `RecurringSeriesMembers.membersOf(series)`:

| Field | Type | Source |
|---|---|---|
| `seriesId` | UUID | `series.getId()` |
| `description` | string | `series.getDescription()` |
| `monthlyEquivalentAmount` | decimal | `lastMember.amount() × occurrencesPerMonth(series.getFrequency())` |
| `originalAmount` | decimal | `firstMember.amount()` |
| `priceIncreased` | boolean | `!isWithinAmountTolerance(originalAmount, lastMember.amount()) && lastMember.amount() > originalAmount` |
| `increaseAmount` | decimal, only meaningful when `priceIncreased` | `lastMember.amount() - originalAmount` |

`occurrencesPerMonth`: `DAILY → 30.44` (365.25/12), `WEEKLY → 4.348` (52.18/12), `MONTHLY → 1`, `YEARLY → 1/12`. A series with fewer than 1 member is skipped (can't happen for a `CONFIRMED` series in practice, since confirmation requires the detection threshold to have been met, but guarded defensively the same way `GetUpcomingRecurringServiceImpl` already guards `members.isEmpty()`).

### CategorySpendingOpportunity (frontend, new, assembled from existing data)

One entry per expense category that is over-budget and/or trending up, built from the reused signals (research.md R5):

| Field | Type | Source |
|---|---|---|
| `categoryId` | string | budget-status entry or trend mover |
| `categoryName` | string | `categoryNames` map |
| `excessAmount` | number | `actual - budgeted` (over-budget) or `change` (trending); when a category qualifies both ways, the larger of the two, counted once |
| `reason` | `'OVER_BUDGET' \| 'TRENDING_UP' \| 'BOTH'` | which signal(s) qualified it |

### TaggedTransactionOpportunity (frontend, new, assembled from existing data)

One entry per bill in `allTransactions` whose `necessityTag` is `AVOIDABLE` or `UNNECESSARY` and whose `time` falls within the recent window shared with the trending-up comparison (research.md R5/R6):

| Field | Type | Source |
|---|---|---|
| `transactionId` | string | `bill.id` |
| `description` | string | `bill.description` |
| `amount` | number | `bill.amount` |
| `tag` | `'AVOIDABLE' \| 'UNNECESSARY'` | `bill.necessityTag` |

### SpendingCutRecommendations (frontend, new, top-level result of the combining utility)

| Field | Type | Composition |
|---|---|---|
| `recurringItems` | `RecurringCostSummaryItem[]`, sorted by `monthlyEquivalentAmount` desc | direct pass-through of the new endpoint's response, sorted client-side |
| `totalMonthlyRecurringSpend` | number | sum of `recurringItems[].monthlyEquivalentAmount` |
| `taggedTransactions` | `TaggedTransactionOpportunity[]` | see above |
| `categoryOpportunities` | `CategorySpendingOpportunity[]` | see above, deduplicated |
| `potentialMonthlySavings` | number | sum of every `recurringItems[].monthlyEquivalentAmount` + every `taggedTransactions[].amount` + every `categoryOpportunities[].excessAmount` |

No new backend response models this — `SpendingCutRecommendations` exists only as a frontend TypeScript type produced by `computeSpendingCutRecommendations`, never serialized or stored (mirrors `CategorySpendingTrend`/`SpendingMover` from feature 016).
