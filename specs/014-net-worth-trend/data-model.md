# Phase 1 Data Model: Net Worth Trend

No new persisted entity, table, or Domain DTO — per `research.md`, every value this feature shows
is derived from data `useDashboardData.ts` already fetches (`accounts`, `allTransactions`). What
follows is the new client-side-only shape and the derivation logic.

## Client-side types (new)

### `NetWorthRangeMonths` (new type)

```ts
type NetWorthRangeMonths = 3 | 6 | 12;
```

### `NetWorthTrendPoint` (new interface)

| Field | Type | Notes |
|---|---|---|
| `label` | `string` | Short month label for the axis, e.g. `"Mar"`, `"Aug"`. |
| `cutoff` | `string` (ISO datetime) | The instant this point's net worth is "as of" — end of that calendar month (UTC) for every point except the most recent, which uses "now". |
| `netWorth` | `number` | Total net worth as of `cutoff`. |

## Derived value (not stored): the trend

```
computeNetWorthTrend(accounts: Account[], allTransactions: Transaction[], monthsBack: NetWorthRangeMonths): NetWorthTrendPoint[] =
    currentTotal = Σ account.balance for account in accounts

    cutoffs = for i in (monthsBack - 1) down to 0:
        monthStart = firstDayOfMonth(now, monthsAgo = i)
        if i == 0: cutoff = now                                    // most recent point: live
        else:      cutoff = lastInstantOfMonth(monthStart)         // "<month>-<lastDay>T23:59:59.999Z"

    for each cutoff (oldest first):
        futureIncome = Σ t.amount for t in allTransactions
                       where t.type == 'INCOME' and t.time > cutoff
        futureBills  = Σ t.amount for t in allTransactions
                       where t.type == 'BILL' and t.time > cutoff
        netWorth(cutoff) = currentTotal - futureIncome + futureBills

    return [{ label: monthLabel(cutoff), cutoff, netWorth(cutoff) } for each cutoff]
```

This mirrors 012's `filterTransactions()` date-bound handling: a plain `t.time > cutoff` string
comparison is safe here because every `cutoff` this function produces is already a full ISO
datetime string (either `now` from `new Date().toISOString()`, or an explicit
`...T23:59:59.999Z` end-of-month instant) — never a bare `YYYY-MM-DD` date needing expansion, so
no extra boundary handling is needed beyond constructing the cutoffs correctly in the first place.

### Worked example (verifying the formula against a known scenario)

Given: two accounts, Checking (`balance: 1200`) and Savings (`balance: 5000`) →
`currentTotal = 6200`. One income transaction: €2000 on May 15. One bill: €300 on Jul 10. Today is
Aug 27. Range = 3 months → buckets are Jun, Jul, Aug(now).

| Cutoff | Future income (`time > cutoff`) | Future bills (`time > cutoff`) | `netWorth = 6200 − futureIncome + futureBills` |
|---|---|---|---|
| Jun 30 23:59:59.999 | €0 (May 15 income is *before* cutoff) | €300 (Jul 10 bill is *after* cutoff) | `6200 − 0 + 300 = 6500` |
| Jul 31 23:59:59.999 | €0 | €0 (Jul 10 bill is *before* this cutoff) | `6200 − 0 + 0 = 6200` |
| Aug 27 (now) | €0 | €0 | `6200 − 0 + 0 = 6200` |

Sanity check by reasoning forward instead of backward: starting from `currentTotal = 6200`, undo
the Jul 10 bill (add its €300 back, since it hasn't happened yet as of Jun 30) to get `6500`, then
undo the May 15 income too (subtract €2000) to get `4500` for any cutoff before May 15. The Jun 30
row above (between the income and the bill) is `6500` — matching the forward reasoning exactly and
confirming the "subtract the future" formula agrees with computing history forward from first
principles.

## Relationships

```
Account[] ──sums to──> currentTotal (number, not stored)
Transaction[] (allTransactions) ──partitioned by cutoff──> NetWorthTrendPoint[] (not stored)
```

No relationship to any new persisted entity exists, because none is introduced.

## Validation Rules

- If `accounts` is empty, `currentTotal = 0` and every trend point is `0` — the empty/zero state
  required by FR-007, not a computation error (summing an empty list is well-defined as `0`).
- `monthsBack` is constrained to the type `NetWorthRangeMonths` (`3 | 6 | 12`) — no arbitrary range
  is accepted, matching the spec's Assumptions ("a small set of presets... not an arbitrary custom
  date-range picker").

## State Transitions

None — `NetWorthTrendPoint[]` is a value recomputed fresh on every render from already-fetched
data, never persisted or evolving on its own between renders.
