# Phase 1 Data Model: Savings Goals

## Entities

### SavingsGoal (new entity/table: `savings_goal`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | generated |
| `name` | String | required, e.g. "Vacation Fund" |
| `targetAmount` | BigDecimal | required, MUST be `> 0` |
| `targetDate` | OffsetDateTime (UTC), nullable | optional per FR-002; absence means no pace status is ever derived for this goal |
| `accountId` | UUID (stored as String, matching every other entity's account/category id convention) | the one account whose balance funds this goal; at most one active goal may reference a given account (FR-003) |
| `createdAt` | OffsetDateTime (UTC) | set once at creation; the start of the straight-line pace interpolation (research.md) |

One row per goal. `accountId` is effectively unique across all `SavingsGoal` rows — enforced in
Domain (`AddSavingsGoalServiceImpl`), not as a DB unique constraint, matching how
`RecurringSeries`' (transactionType, groupKey, description) uniqueness (010) is also
Domain-enforced rather than a DB constraint.

### Derived values (not stored)

**SavingsGoalStatusDto** — one per `SavingsGoal`, computed fresh on every read:

```
account = GetAccountService.getAccountById(goal.accountId)
savedAmount = account.balance                              // already balance-derived, per 007

percentComplete = clamp(savedAmount / goal.targetAmount, 0, 1) * 100
remainingAmount = max(goal.targetAmount - savedAmount, 0)
achieved = savedAmount >= goal.targetAmount

paceStatus =
    if goal.targetDate is null: null
    else if achieved: null                                   // achieved takes precedence, research.md
    else if now > goal.targetDate: OVERDUE
    else:
        expectedFraction = clamp((now - goal.createdAt) / (goal.targetDate - goal.createdAt), 0, 1)
        actualFraction = clamp(savedAmount / goal.targetAmount, 0, 1)
        if actualFraction >= expectedFraction: ON_PACE
        else: BEHIND_PACE
```

`SavingsGoalStatusDto` carries every `SavingsGoal` field plus `savedAmount`, `percentComplete`,
`remainingAmount`, `achieved`, and `paceStatus` — the same "stored fields + derived fields in one
response shape" pattern `BudgetStatusDto` (009) already uses for `Budget`.

## Relationships

```
Account 1───0..1 SavingsGoal   (an account funds at most one active goal; enforced in Domain at
                                 creation time, not a DB foreign-key/unique constraint — mirrors
                                 RecurringSeries' Domain-enforced natural-key uniqueness from 010)
```

`SavingsGoal` has no relationship to `Bill`, `Income`, or `Category` — its only structural
dependency is the one `Account` it's linked to; contributions are simply that account's existing
bill/income history, read indirectly through `GetAccountService`'s balance derivation, never
matched or joined against directly by this feature.

## Validation Rules

- `AddSavingsGoalServiceImpl`: `targetAmount` MUST be `> 0`; `accountId` MUST reference an existing
  account (`GetAccountService.getAccountById` — throws if absent, mapped to 404); rejects creation
  if any existing `SavingsGoal` already has this `accountId` (FR-003), mapped to 400.
- `UpdateSavingsGoalServiceImpl`: same `targetAmount > 0` rule; `accountId` is not accepted as an
  updatable field (research.md — fixed at creation); rejects updating a goal id that doesn't exist,
  mapped to 404.
- `DeleteSavingsGoalServiceImpl`: rejects deleting a goal id that doesn't exist, mapped to 404;
  succeeding never touches the linked account or its transactions (FR-010).

## State Transitions

None. Unlike `RecurringSeries` (010) or `Budget` allocations (009), a `SavingsGoal` has no stored
status field — `achieved` and `paceStatus` are purely derived booleans/enums recomputed on every
read from the current account balance and the current date, never persisted, so there is nothing
that "transitions." A goal simply exists (with editable `name`/`targetAmount`/`targetDate`) until
it is deleted.
