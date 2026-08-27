# Phase 0 Research: Savings Goals

No `[NEEDS CLARIFICATION]` markers remain in the spec — every open design question was resolved
with a documented default in the spec's Assumptions section during `/speckit-specify`. This phase
turns those defaults into concrete, testable decisions.

## Decision: A goal's saved amount is its linked account's current derived balance — no separate contribution ledger

**Decision**: `SavingsGoal` stores only `name`, `targetAmount`, `targetDate` (nullable),
`accountId`, and `createdAt`. "Saved amount" is never stored; it is `GetAccountService
.getAccountById(accountId).getBalance()`, re-read on every request. No new "contribution"
transaction type or table is introduced — an account's existing bills/income already are its
contributions and withdrawals.

**Rationale**: `GetAccountServiceImpl` (007) already derives an account's balance from
`startingBalance + sum(income) - sum(bills)` at read time, per Principle III. A goal is simply a
target painted on top of one account's existing derived balance. Introducing a parallel
"contribution" concept would duplicate what an account/bill/income already model, and would need
its own reconciliation with the account's real balance — a second source of truth this feature
doesn't need.

**Alternatives considered**: A `GoalContribution` entity recording manual "I saved €50 today"
entries, independent of the account ledger — rejected as unnecessary duplication; it would let a
goal's "saved" total drift from what the linked account can actually verify, and the spec's own
framing ("linked to a specific account whose balance funds it") already points at reusing the
account, not inventing a parallel ledger.

## Decision: One active goal per account, enforced at creation

**Decision**: `AddSavingsGoalServiceImpl` rejects creating a goal whose `accountId` already backs
another goal (checked via `GetSavingsGoalPersistencePort.getAll()` filtered by `accountId`,
mirroring `DetectRecurringSeriesServiceImpl`'s "read everything, filter in Domain" style since the
scale here is a handful of goals). The check throws `IllegalStateException`, mapped to HTTP 400 by
the controller — the same pattern `ConfirmRecurringSeriesServiceImpl` (010) and
`SetBudgetServiceImpl` (009) already use for business-rule rejections.

**Rationale**: If two goals shared one account, both would independently report the account's
whole balance as "saved," so contributing €100 would appear to advance both goals' progress by
€100 simultaneously — a double-count the spec's Assumptions section explicitly calls out as
something to avoid. Rejecting at creation is simpler than any later reconciliation and gives
immediate, actionable feedback (US1 Acceptance Scenario 3).

**Alternatives considered**: Allowing multiple goals per account and splitting the balance
proportionally between them — rejected as speculative complexity with no requirement driving it,
and it would require the goal to know about every other goal on the same account just to render
its own progress, coupling that doesn't otherwise exist.

## Decision: Pace status is computed via straight-line date interpolation, three states

**Decision**: For a goal with a `targetDate`, `SavingsGoalProgress` computes:
- `expectedFraction = clamp01((now - createdAt) / (targetDate - createdAt))`
- `actualFraction = clamp01(savedAmount / targetAmount)` (0 if `targetAmount` is non-positive, not
  expected in practice since target amount is required and validated `> 0` at creation)
- If `now.isAfter(targetDate)` and the goal is not yet achieved → **OVERDUE**
- Else if `actualFraction >= expectedFraction` → **ON_PACE**
- Else → **BEHIND_PACE**

A goal with no `targetDate` has `paceStatus = null` (US3 Acceptance Scenario 3) — the progress
fields (saved, percent complete, remaining) are still returned.

**Rationale**: This directly implements the spec's Assumption ("straight-line interpolation
between the goal's creation date and its target date; no forecasting"). Using `createdAt` as the
start of the line (rather than, say, always starting from 0 at some other epoch) needs no extra
stored state — `createdAt` already exists on every goal for free. Checking "already past target
date and not achieved" before the ratio comparison directly satisfies FR-008 / US3 Acceptance
Scenario 4 (overdue is its own state, not just "very behind").

**Alternatives considered**: A tolerance band around the expected fraction (e.g., "within 5% counts
as on pace") — rejected as an untested magic number the spec doesn't ask for; an exact `>=`
comparison is simpler and matches the spec's plain-language framing ("at or ahead of the
straight-line pace").

## Decision: Achieved is `savedAmount >= targetAmount`, independent of and checked before pace

**Decision**: `SavingsGoalProgress.achieved` is `savedAmount.compareTo(targetAmount) >= 0`,
computed the same way regardless of whether a `targetDate` exists. When `achieved` is true, the
goal is never also reported as `OVERDUE` even if its target date has passed — achieved wins.

**Rationale**: Directly implements US2 Acceptance Scenario 3 and the spec's Edge Cases ("saved
amount continues to grow past its target amount: it stays marked as achieved"). Checking achieved
before overdue means a goal met right at its deadline (or even slightly late, if the user just
hasn't looked in a while) still reads as a win, not a failure — matching ordinary intuition about
what "achieved" should mean.

**Alternatives considered**: None seriously — the spec's edge case is explicit and there is no
plausible fallback amount reading it differently.

## Decision: `accountId` is fixed at creation; only name, target amount, and target date are editable

**Decision**: `UpdateSavingsGoalService` accepts `name`, `targetAmount`, `targetDate` — no
`accountId` field. Re-linking a goal to a different account is not supported in this feature.

**Rationale**: The spec's US4 acceptance scenarios only exercise editing name/target
amount/target date, never re-linking the account. Allowing account re-linking would reopen the
one-goal-per-account invariant mid-update (does the old account's slot free up? does the new
account's existing goal, if any, get bumped?) for a capability nothing in the spec asks for —
scope discipline per the Constitution's "gold-plating is prohibited" governance rule. A user who
wants to fund a goal from a different account can delete and recreate it (FR-010 already makes
that cheap and safe — deleting a goal never touches the account).

**Alternatives considered**: Supporting account re-linking with the same validation as creation —
deferred; can be added later as a pure additive change if requested, without touching this
feature's other endpoints.

## Decision: Goal deletion is a hard delete, not a reversal/archive

**Decision**: `DELETE /savings-goals/{id}` removes the `savings_goal` row outright.
`DeleteSavingsGoalServiceImpl` requires no compensating record.

**Rationale**: Per Principle I, only *financial transactions* require reversal-based correction —
a savings goal is a planning record the user authored, structurally the same category as a
`Category` or `Budget` row, both of which the app already hard-deletes
(`DeleteAccountPersistencePort`, `DeleteBudgetPersistencePort` precedent). The spec's own
Assumptions section makes this explicit: deleting a goal "does not need the immutable
correction/reversal treatment that bills and income transactions require."

**Alternatives considered**: A soft-delete/archived status, keeping historical goals visible —
rejected as unrequested scope; the spec's US4 only asks that a deleted goal stop appearing and that
its account/transactions be unaffected, both of which a hard delete already satisfies.

## Decision: Four REST endpoints under `/savings-goals`, list responses always carry derived status

**Decision**: `GET /savings-goals` (list), `GET /savings-goals/{id}` (single), `POST
/savings-goals` (create), `PUT /savings-goals/{id}` (update name/target/date), `DELETE
/savings-goals/{id}`. Both GET endpoints return the derived `SavingsGoalStatusDto` shape (goal
fields plus `savedAmount`/`percentComplete`/`remainingAmount`/`achieved`/`paceStatus`) — there is
no "raw goal without progress" response, since every read of a goal is, per US2, meant to show
progress at a glance.

**Rationale**: Mirrors `GET /recurring-series/dashboard` (010) and `GET /budgets` (009) already
returning combined stored+derived shapes rather than forcing the frontend to make a second request
to compute progress client-side. `POST`/`PUT` return the same `SavingsGoalStatusDto` on success
(so the frontend can update its list in place without a refetch), matching `RecurringGetController`
/`BudgetingPage` precedent of create/update endpoints echoing the full current-state view.

**Alternatives considered**: A separate `GET /savings-goals/{id}/status` endpoint split from the
raw CRUD resource — rejected as an unrequested extra round-trip; nothing in the spec asks for
progress and identity to be fetched independently.
