# Phase 0 Research: Cash Flow Forecast

All items below were resolved from the existing codebase; no `NEEDS CLARIFICATION` markers
remain from the Technical Context.

## 1. Multi-occurrence prediction

**Decision**: Add a new pure function to `RecurringMatching`,
`predictOccurrencesWithinWindow(OffsetDateTime asOf, OffsetDateTime latestOccurrence, boolean
overdue, RecurringFrequency frequency, OffsetDateTime windowEnd)`, that walks
`predictNextDate` forward — starting from `asOf` when the series is overdue (per spec Edge
Case: an overdue series is due "now"), otherwise from `latestOccurrence` — collecting every
predicted date up to `windowEnd`, then continuing to call `predictNextDate` on each result
until the next date exceeds `windowEnd`.

**Rationale**: `GetUpcomingRecurringServiceImpl` already predicts exactly one occurrence via
`RecurringMatching.predictNextDate`. Reusing that single-step primitive in a loop — rather than
reimplementing cadence math in the new forecast service — guarantees the recurring dashboard's
"next occurrence" date and the forecast's first occurrence date can never drift apart.

**Alternatives considered**: Duplicating the date-stepping logic directly inside the new
forecast service. Rejected — it would fork the cadence arithmetic (`plusDays`/`plusWeeks`/etc.)
into two places, and any future fix to `predictNextDate` (e.g. handling month-end edge cases)
would silently not apply to the forecast.

## 2. Series membership resolution (reuse `membersOf`)

**Decision**: Extract `GetUpcomingRecurringServiceImpl.membersOf(RecurringSeriesDto)` — currently
package-private — into a small shared Domain component
(`RecurringSeriesMembers.membersOf(series, getBillService, getIncomeService)` or an injectable
`RecurringSeriesMemberResolver` bean) usable by both `GetUpcomingRecurringServiceImpl` and the
new `GetCashFlowForecastServiceImpl`.

**Rationale**: The groupKey + normalized-description matching logic that resolves "which
transactions belong to this confirmed series" already exists and is exercised in production.
The forecast needs the exact same membership set (correction-aware, via `GetBillService`/
`GetIncomeService.getAll()`) to find each series' latest occurrence, its amount, and — new for
this feature — its `accountId`. Duplicating the filter would risk the two services disagreeing
about series membership.

**Alternatives considered**: Leaving `membersOf` where it is and having the forecast service
re-derive membership independently. Rejected — direct duplication of matching logic the
constitution's Test-First principle implies should have one source of truth.

## 3. Account attribution per series

**Decision**: For each series' members (sorted oldest-first, as `membersOf` already returns),
take the `accountId` of the *latest* member as the account the series' predicted future
occurrences are attributed to.

**Rationale**: `BillDto`/`IncomeDto` already carry `accountId` per row; `membersOf`'s mapping
just needs to carry it through (today's `MemberOccurrence` record only carries id/time/amount —
it will gain an `accountId` field). This is the same account the existing "next occurrence"
prediction implicitly reflects, since both derive from the same latest member. Matches the
spec's documented Assumption.

**Alternatives considered**: Attributing to whichever account most of a series' historical
occurrences hit. Rejected as unnecessarily complex for a case the spec explicitly scopes as
an edge case, and it would make forecasted attribution unpredictable to the user (why did this
series suddenly move accounts?) versus "most recent wins," which is easy to explain and matches
existing behavior.

## 4. Forecast window presets

**Decision**: A `weeks` request parameter accepting one of `{2, 4, 8, 12}`, default `4`.

**Rationale**: The feature's stated purpose ("before their next expected income") is a
near-term horizon; weekly granularity keeps every supported `RecurringFrequency`
(DAILY/WEEKLY/MONTHLY/YEARLY) meaningful within the smallest preset while still letting a user
look two-plus months out. Mirrors 014's precedent of a small fixed preset set
(`NetWorthRangeMonths = 3 | 6 | 12`) rather than a free-form date picker.

**Alternatives considered**: Reusing month-denominated presets like 014. Rejected — a
cash-flow-forecast horizon is inherently shorter and finer-grained than a net-worth trend, and
whole-month presets would make the shortest preset (1 month) too coarse for "did I miss my next
paycheck" checks.

## 5. Why this can't be a pure-frontend feature (unlike 012/013/014)

**Decision**: A new Domain service and a new REST endpoint are required.

**Rationale**: No existing endpoint predicts more than one future occurrence per series, and no
endpoint projects a running balance over a sequence of dated events. Both are genuinely new
business logic that belongs in the Domain module per Hexagonal Architecture (Principle VIII) —
computing a financial projection is domain logic, not a presentation concern the frontend
should own. This is the first feature since 012 that cannot be built as frontend-only
composition of existing reads.

## 6. Persistence

**Decision**: Nothing new is persisted. The forecast is computed fully at read time from
`GetAccountService` (current derived balances), `GetRecurringSeriesService` (confirmed series),
and `GetBillService`/`GetIncomeService` (correction-aware occurrence history) on every request.

**Rationale**: Consistent with Constitution Principle III (balance derivation) and every prior
derived feature (007/009/010/011/012/013/014) — "derive, don't store."

## 7. Response shape: one endpoint for all accounts

**Decision**: `GET /cash-flow-forecast?weeks=N` returns forecasts for *every* account in one
response (`{ accounts: [...] }`), not one call per account.

**Rationale**: The frontend needs to render every account's forecast card on the same dashboard
view at once. A single aggregate endpoint avoids an N+1 call pattern; the personal-finance scale
of this app (a handful of accounts) makes a single combined computation trivially fast (SC-003:
recompute within a couple of seconds).

**Alternatives considered**: `GET /accounts/{id}/forecast` per account. Rejected — would require
the frontend to fan out one request per account on every window change, with no benefit given
the expected account count.
