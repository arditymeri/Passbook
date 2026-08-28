# Phase 1 Data Model: Cash Flow Forecast

No new persisted entity or table is introduced (see research.md §6). This document describes the
new **derived/transient** Domain DTOs the forecast computation produces, and the extension to an
existing internal record.

## `ForecastEntryDto` (new)

One predicted occurrence within the forecast window, in date order, for a single account.

| Field | Type | Notes |
|---|---|---|
| `date` | `OffsetDateTime` | Predicted occurrence date (UTC), from `RecurringMatching.predictOccurrencesWithinWindow`. |
| `seriesId` | `String` | The confirmed `RecurringSeriesDto.id` this entry was generated from. |
| `transactionType` | `TransactionType` (`BILL`\|`INCOME`) | Determines whether `amount` decreases or increases the running balance. |
| `description` | `String` | Carried from the series. |
| `amount` | `BigDecimal` | The series' latest known amount (same "carry forward last amount" rule `GetUpcomingRecurringServiceImpl` already uses — no averaging). |
| `projectedBalance` | `BigDecimal` | The account's running balance immediately after this entry is applied. |

**Validation**: `amount` MUST be a `BigDecimal` per Constitution Principle IV — never a floating
point type at any layer of the Domain/Application boundary.

## `AccountForecastDto` (new)

One account's full forecast for the selected window.

| Field | Type | Notes |
|---|---|---|
| `accountId` | `String` | |
| `accountName` | `String` | |
| `accountType` | `AccountType` | Carried through so the frontend can, if desired, treat credit-card accounts differently (spec explicitly notes a credit card going negative is expected, not a warning-worthy event — see Assumptions; MVP still surfaces the same warning semantics for every account type, per FR-003, but the type is included for future-proofing the frontend without a contract change). |
| `currentBalance` | `BigDecimal` | The account's current derived balance (`GetAccountService.getAll()`'s already-derived `balance`), i.e. the forecast's starting point. |
| `windowWeeks` | `int` | Echoes the requested window. |
| `timeline` | `List<ForecastEntryDto>` | Every predicted occurrence within the window, in date order, across all of the account's confirmed series. |
| `atRisk` | `boolean` | `true` when `currentBalance < 0` OR any `timeline` entry's `projectedBalance < 0`. |

**Relationships**:
- Derives from `AccountDto` (via `GetAccountService`) — 1 forecast per account, all accounts
  included in a single response (research.md §7).
- Derives from `RecurringSeriesDto` where `status == CONFIRMED` (via `GetRecurringSeriesService`).
- Derives from `BillDto`/`IncomeDto` history (via `GetBillService`/`GetIncomeService.getAll()`,
  correction-aware) — used only to find each series' latest occurrence (time, amount,
  `accountId`), never written to.

**State transitions**: None — this DTO is never persisted, so it has no lifecycle. Recomputed
fresh on every request (FR-008: read-only).

## `CashFlowForecastResult` (new, top-level response DTO)

| Field | Type | Notes |
|---|---|---|
| `accounts` | `List<AccountForecastDto>` | One entry per account known to the system, regardless of whether it has any confirmed series (FR-006: accounts with none still appear, with an empty `timeline` and `atRisk` reflecting only `currentBalance`). |

## Extension: `GetUpcomingRecurringServiceImpl.MemberOccurrence`

The existing package-private record:

```java
record MemberOccurrence(String id, OffsetDateTime time, BigDecimal amount) {}
```

gains one field once `membersOf` is extracted into the shared `RecurringSeriesMembers` component
(research.md §2):

```java
record MemberOccurrence(String id, OffsetDateTime time, BigDecimal amount, String accountId) {}
```

`accountId` is already present on both `BillDto` and `IncomeDto` — this only threads an
already-available value through, no new source data.
