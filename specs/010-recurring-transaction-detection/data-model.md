# Phase 1 Data Model: Recurring Transaction Detection

## Entities

### RecurringSeries (new entity/table: `recurring_series`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | generated |
| `transactionType` | enum: `BILL`, `INCOME` | which transaction stream this series belongs to; a bill series and an income series are never the same row even with identical category/description (Assumptions) |
| `categoryId` | UUID (FK → category) | part of the matching key |
| `description` | String | the matching key's description text, stored trimmed and lowercased at creation time (the normalized form used for matching, per `research.md`) |
| `frequency` | `RecurringFrequency` (existing enum: `DAILY`/`WEEKLY`/`MONTHLY`/`YEARLY`) | the cadence detected for this series |
| `status` | enum: `PROPOSED`, `CONFIRMED`, `DISMISSED` | see State Transitions below |
| `createdAt` | OffsetDateTime (UTC) | when the series was first detected |

One row per (transactionType, categoryId, description) — this triple is the natural key; detection
never creates a second row for a triple that already has one, in any status (this is what makes a
dismissed proposal stay dismissed, per FR-003).

### Derived values (not stored)

**Series member transactions** — computed, not stored:

```
membersOf(series, asOfNow) =
    { t ∈ GetBillService.getAll() (if series.transactionType = BILL, else GetIncomeService.getAll())
      : t.categoryId = series.categoryId
        AND normalize(t.description) = series.description }
```

**Upcoming Recurring Item** — one per `CONFIRMED` series with at least one member:

```
members = membersOf(series) sorted by time ascending
latest = last(members)
predictedDate = latest.time + intervalFor(series.frequency)
predictedAmount = latest.amount
overdue = predictedDate < now AND no member exists with time >= predictedDate
```

**Price Change Alert** — one per `CONFIRMED` series whose two most recent members differ by more
than the amount tolerance:

```
members = membersOf(series) sorted by time ascending
if |members| >= 2:
    latest = members[-1]; prior = members[-2]
    if |latest.amount - prior.amount| > amountTolerance(prior.amount):
        alert = { transactionId: latest.id, transactionType, categoryId, description,
                  priorAmount: prior.amount, newAmount: latest.amount,
                  delta: latest.amount - prior.amount }
```

`GetBillService.getAll()` / `GetIncomeService.getAll()` already return the reversal-aware,
correction-aware current value per logical transaction (research.md), so a corrected occurrence's
amount is what's compared — no special-casing needed here.

## Relationships

```
Category 1───* RecurringSeries          (categoryId FK, existing)
RecurringSeries ···> Bill or Income     (computed match on categoryId + normalized description,
                                          not a stored relationship — see "Series member
                                          transactions" above)
```

## Validation Rules

- `DetectRecurringSeriesServiceImpl`: a candidate group becomes a `PROPOSED` series only when a
  run of 3 consecutive matching occurrences exists (2 if at least one has the existing `recurring`
  flag set), each consecutive gap within the cadence's date tolerance and each consecutive amount
  within the amount tolerance of the previous (FR-002); skipped entirely if a `RecurringSeries`
  already exists for that (transactionType, categoryId, description) key, in any status (FR-003).
- `ConfirmRecurringSeriesServiceImpl`: only a `PROPOSED` series can be confirmed; confirming a
  series in any other status is rejected.
- `DismissRecurringSeriesServiceImpl`: a series in `PROPOSED` or `CONFIRMED` status can be
  dismissed (the same action serves both "reject a proposal" (US2 Scenario 3) and "stop tracking a
  confirmed series" (FR-011)); dismissing an already-`DISMISSED` series is rejected.

## State Transitions

```
PROPOSED ──confirm──> CONFIRMED ──dismiss──> DISMISSED
    │                                            ^
    └──────────────────dismiss──────────────────-┘
```

`DISMISSED` is terminal — detection never re-proposes a (transactionType, categoryId, description)
key that already has a row in any status, so a dismissed series cannot be resurrected by running
detection again; the user would need to record transactions with a distinguishably different
description to get a fresh proposal.
