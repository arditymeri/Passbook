# Feature Specification: Savings Goals

**Feature Branch**: `011-savings-goals`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Savings goals. Let a user create a savings goal (e.g. \"Vacation fund\") with a target amount and optional target date, track contributions toward it over time, and see progress at a glance (amount saved, percent complete, remaining, and whether they're on pace to hit the target date). Builds on the existing Account concept (a goal can be linked to a specific account whose balance funds it) and the transaction history/dashboard patterns already established by prior features."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create a Savings Goal (Priority: P1)

A user wants to start saving toward something specific — a vacation, an emergency fund, a big purchase. They give the goal a name, a target amount, and link it to one of their accounts (the account whose balance will fund the goal). They can optionally set a target date they'd like to reach the goal by.

**Why this priority**: Without the ability to create a goal, nothing else in this feature has anything to act on. This is the entry point for every other story.

**Independent Test**: Can be fully tested by creating a goal with a name, target amount, and linked account, and confirming it appears in the user's list of goals with those exact details.

**Acceptance Scenarios**:

1. **Given** the user has at least one account, **When** they create a goal named "Vacation Fund" with a target amount and a linked account, **Then** the goal appears in their goals list showing that name, target amount, and linked account.
2. **Given** the user is creating a goal, **When** they leave the target date blank, **Then** the goal is created successfully without a target date.
3. **Given** an account is already linked to another active goal, **When** the user tries to link the same account to a new goal, **Then** the system rejects the creation and explains that the account is already funding a goal.

---

### User Story 2 - See Goal Progress at a Glance (Priority: P1)

A user wants to check, at any time, how close they are to each of their savings goals — how much is saved, what percentage of the target that represents, and how much is left to go.

**Why this priority**: Seeing progress is the core ongoing value of the feature — it's why someone keeps coming back to it after creating a goal. Equal priority to creation because a goal with no visible progress is not a usable feature.

**Independent Test**: Can be fully tested by linking a goal to an account with a known balance and confirming the goals view shows the correct saved amount, percent complete, and remaining amount — independent of any pace or lifecycle logic.

**Acceptance Scenarios**:

1. **Given** a goal linked to an account with a current balance below the target, **When** the user views their goals, **Then** they see the saved amount (the account's current balance), the percent of the target reached, and the amount remaining.
2. **Given** new transactions are recorded against a goal's linked account, **When** the user views the goal again, **Then** the saved amount, percent complete, and remaining amount reflect the account's new balance without any extra action from the user.
3. **Given** a goal's saved amount has reached or exceeded its target amount, **When** the user views their goals, **Then** the goal is clearly marked as achieved.

---

### User Story 3 - Get Warned About Pace (Priority: P2)

A user with a target date wants to know whether they're on track to hit it, or falling behind, so they can adjust their saving before it's too late.

**Why this priority**: Valuable, but only applicable to goals with a target date, and only meaningful once progress tracking (US2) already exists. It's an enhancement to the core progress view, not a prerequisite for it.

**Independent Test**: Can be fully tested by creating goals with a target date and varying current progress (ahead, on track, behind, overdue), and confirming the pace indicator matches straight-line expectations for each — independent of goal creation UX or lifecycle management.

**Acceptance Scenarios**:

1. **Given** a goal with a target date, **When** the user's current progress is at or ahead of the straight-line pace needed to reach the target by that date, **Then** the goal is shown as on pace.
2. **Given** a goal with a target date, **When** the user's current progress is behind the straight-line pace needed to reach the target by that date, **Then** the goal is shown as falling behind.
3. **Given** a goal with no target date, **When** the user views it, **Then** no pace indicator is shown (only saved amount, percent complete, and remaining).
4. **Given** a goal's target date has already passed and the target amount has not been reached, **When** the user views the goal, **Then** it is shown as overdue rather than merely "falling behind."

---

### User Story 4 - Manage a Goal (Priority: P3)

A user wants to adjust a goal's target amount or date as their plans change, or remove a goal they no longer want to track.

**Why this priority**: Useful lifecycle housekeeping, but the feature already delivers its core value (US1-US3) without it — a user can live with a slightly wrong target for a while before this becomes essential.

**Independent Test**: Can be fully tested by editing an existing goal's target amount or date and confirming the change is reflected in the goals view, and by deleting a goal and confirming it no longer appears while its linked account and transactions are untouched.

**Acceptance Scenarios**:

1. **Given** an existing goal, **When** the user edits its name, target amount, or target date, **Then** the goals view reflects the updated values immediately.
2. **Given** an existing goal, **When** the user deletes it, **Then** it no longer appears in the goals list, and the linked account and its transaction history are unaffected.

---

### Edge Cases

- A goal's linked account has a balance below zero (bills exceed income): saved amount is shown as-is (can be negative or zero), with percent complete floored at 0%.
- A goal's target date is set to today or a date in the past at creation time: the goal is created, but with little or no time remaining, so pace is evaluated as overdue as soon as the target isn't already met.
- A goal's saved amount continues to grow past its target amount: it stays marked as achieved; percent complete does not need to display beyond 100%.
- Deleting a goal never deletes or modifies its linked account or that account's bills/income.
- A user has no accounts yet: goal creation is blocked until at least one account exists, consistent with how other account-linked features already behave.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to create a savings goal with a name, a target amount, and a linked account.
- **FR-002**: Users MAY optionally set a target date when creating or editing a goal.
- **FR-003**: System MUST prevent an account from being linked to more than one active goal at the same time.
- **FR-004**: System MUST derive each goal's saved amount from its linked account's current balance, computed fresh whenever the goal is viewed — never stored or cached separately from the account's own transaction history.
- **FR-005**: System MUST derive percent complete (saved ÷ target, floored at 0%) and remaining amount (target − saved, floored at 0) from the current saved amount and the goal's target amount.
- **FR-006**: For goals with a target date, system MUST derive whether the goal is on pace by comparing actual progress to the straight-line progress expected between the goal's creation date and its target date.
- **FR-007**: System MUST mark a goal as achieved (derived, not stored) once its saved amount reaches or exceeds its target amount.
- **FR-008**: For a goal with a target date that has passed without the target being reached, system MUST show it as overdue rather than as falling behind.
- **FR-009**: Users MUST be able to edit an existing goal's name, target amount, and target date.
- **FR-010**: Users MUST be able to delete a goal; deleting it MUST NOT delete or modify its linked account or that account's transactions.
- **FR-011**: System MUST display all of a user's goals, each with its saved amount, percent complete, remaining amount, and (when a target date is set) pace status.

### Key Entities

- **Savings Goal**: A user-defined savings target with a name, a target amount, an optional target date, a creation date, and a link to exactly one account. Its saved amount, percent complete, remaining amount, achieved status, and pace status are all derived at read time from the linked account's current balance and the goal's own dates/target — none of these derived values are persisted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can create a new savings goal in under 30 seconds.
- **SC-002**: Goal progress shown to the user (saved amount, percent complete, remaining) always matches the linked account's current balance versus the goal's target, with no stale values after new transactions are recorded.
- **SC-003**: For goals with a target date, the on-pace/falling-behind/overdue status is correct in 100% of tested scenarios spanning ahead-of-pace, on-pace, behind-pace, and past-due cases.
- **SC-004**: A user can tell, within a few seconds of looking at their goals list, which goals are achieved and which are still in progress.

## Assumptions

- Each account can be linked to at most one active goal at a time, so the same balance is never double-counted toward two different targets.
- A goal's "amount saved" is simply its linked account's current derived balance; the account is assumed to be used as a dedicated savings vehicle for that goal (contributions and withdrawals are just that account's existing income/bill transactions — no separate "contribution" transaction type is introduced).
- Pace is computed via straight-line interpolation between the goal's creation date and its target date; the feature does not forecast future income or spending patterns.
- Goals without a target date show saved amount, percent complete, and remaining amount, but no pace indicator.
- Deleting a goal is a simple removal of the planning record (not a financial transaction), so it does not need the immutable correction/reversal treatment that bills and income transactions require.
