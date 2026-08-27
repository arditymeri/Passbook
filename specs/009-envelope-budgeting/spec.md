# Feature Specification: Envelope Budgeting

**Feature Branch**: `009-envelope-budgeting`

**Created**: 2026-08-25

**Status**: Draft

**Input**: User description: "Envelope / zero-based budgeting. Reframe the existing per-category monthly budget (feature 002) from a spending 'limit' into an 'allocation' — every dollar of a month's income gets assigned to a category up front, and the app shows how much of the month's income is still unallocated. Builds on the existing Budget domain concept and the Spending Analysis API; do not touch feature 008 (in a separate branch, not to be modified)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See What's Left to Assign (Priority: P1)

As a user planning my month, I want to open the budgeting view and immediately see my running unallocated balance — this month's income plus whatever was left unassigned before, minus everything I've assigned to categories so far — so I know whether I've given every dollar a job.

**Why this priority**: This single number — what's left to assign — is the entire premise of envelope/zero-based budgeting. Without it, allocations are just budget limits with a new name and the feature delivers no new value.

**Independent Test**: Can be fully tested by opening the budgeting view for a month with recorded income and one or more category allocations, and verifying the displayed "unallocated" figure equals cumulative income to date minus the cumulative sum of all allocations to date.

**Acceptance Scenarios**:

1. **Given** a month has $3,000 of recorded income, no unallocated carryover from prior months, and $1,800 allocated across categories this month, **When** the user opens the budgeting view for that month, **Then** the view shows total income $3,000, total allocated $1,800, and unallocated $1,200.
2. **Given** the prior month ended with $200 still unallocated, **When** the user opens the budgeting view for the new month, **Then** that $200 is included in the new month's unallocated figure before any new income or allocations are added.
3. **Given** a month has no recorded income yet, **When** the user opens the budgeting view, **Then** total income shows $0 and any existing allocations make the unallocated figure negative (unless a positive carryover from prior months offsets them).
4. **Given** cumulative allocations exceed cumulative income to date, **When** the user views the unallocated figure, **Then** it is visually distinguished (e.g. colour-coded) from a positive, still-unassigned balance.

---

### User Story 2 - Assign Income to a Category (Priority: P1)

As a user, I want to assign a specific amount of this month's income to a category, so that the money has a purpose before I spend it.

**Why this priority**: Assigning money to categories is the core write action of the feature — without it, User Story 1 has nothing to display. This directly extends the existing per-category budget from feature 002.

**Independent Test**: Can be fully tested by opening a category's allocation for the current month, entering an amount, saving, and verifying both the category's allocation and the month's unallocated total update accordingly.

**Acceptance Scenarios**:

1. **Given** a category has no new allocation yet for the selected month, **When** the user assigns $400 to it, **Then** the category's envelope balance increases by $400 and the unallocated total decreases by $400.
2. **Given** a category already has an allocation entered for the selected month, **When** the user changes that month's amount, **Then** the entry updates to the new amount (not a second entry) and the unallocated total adjusts by the difference — the category's carried-over balance from prior months is untouched.
3. **Given** a category's allocation for the current month is reduced to $0, **When** the change is saved, **Then** only that month's assigned amount returns to the unallocated total; any balance the category carried in from prior months remains in its envelope.
4. **Given** a category is of type INCOME, **When** the user looks for it in the allocation view, **Then** it is not offered as an allocation target (only EXPENSE and BOTH categories can receive an allocation).

---

### User Story 3 - Move Money Between Categories (Priority: P2)

As a user, I want to move an amount from one category's envelope balance to another category's envelope balance, so that I can correct a misallocation — or shift leftover money to where it's needed — without first sending it back to "unallocated" and reassigning it in two separate steps.

**Why this priority**: Reassigning envelopes as circumstances change is a defining, frequent action in envelope budgeting, made more useful once balances carry forward (a category can build up a surplus another category needs). It's a refinement of User Story 2's single-category edit, so it depends on that story existing first.

**Independent Test**: Can be fully tested by moving an amount from one category's envelope balance to another's and verifying the source decreases, the destination increases, and the month's total allocated and unallocated figures are unchanged.

**Acceptance Scenarios**:

1. **Given** "Dining Out" has a $200 envelope balance and "Groceries" has $300, **When** the user moves $50 from Dining Out to Groceries, **Then** Dining Out shows $150, Groceries shows $350, and the unallocated total is unchanged.
2. **Given** a category has a $50 envelope balance, **When** the user attempts to move $100 out of it, **Then** the request is rejected with a message explaining only the currently available balance can be moved.

---

### User Story 4 - Repeat Last Month's Assignments (Priority: P3)

As a user starting a new month, I want to repeat last month's category assignment amounts as new top-up allocations for the current month, so I don't have to re-enter the same recurring amounts from scratch every month.

**Why this priority**: This removes the single biggest recurring friction of zero-based budgeting (re-typing every category's monthly amount) but is a convenience on top of Stories 1–2, not a prerequisite for them. With balances now carrying forward automatically (User Story 1), this action tops up envelopes rather than resetting them.

**Independent Test**: Can be fully tested by repeating a prior month that has allocations into a new month, and verifying each category receives a new allocation entry matching the source month's amount, added on top of whatever balance it already carried in.

**Acceptance Scenarios**:

1. **Given** May had allocations of $400 to Groceries and $150 to Dining Out, and June's Groceries envelope already carries forward $60 unspent from May, **When** the user repeats May's assignments into June, **Then** June's Groceries allocation entry adds $400 (bringing its envelope to $460) and Dining Out adds $150.
2. **Given** the user repeats a month's assignments into a month that already has new allocation entries of its own, **When** the action completes, **Then** the user is shown which categories will receive an additional top-up before it proceeds.
3. **Given** the source month has no allocations at all, **When** the user attempts to repeat it, **Then** a clear message states there is nothing to repeat.

---

### Edge Cases

- What happens when a user opens the budgeting view for the very first month, with no prior carryover? Carryover is treated as $0; unallocated equals that month's income minus that month's allocations only.
- What happens when a user opens the budgeting view for a month with zero recorded income and no positive carryover? Unallocated is 0 (or negative) minus any new allocations, so any allocated amount immediately reads as over-allocated.
- How does a move between categories behave if the source doesn't have enough available balance to cover the requested amount? It is rejected with a validation message (see US3 Scenario 2).
- What happens to an allocation tied to a category that is later renamed? The allocation follows the category by its identity; only the displayed name changes.
- What happens when cumulative allocations across all categories exceed cumulative income to date? Allowed, but flagged — the app never blocks the user from over-allocating, consistent with the existing budget feature not blocking overspending.
- What happens to a category's carried-forward balance if a past month's bill or income entry is later added or changed? The envelope balance is derived, not stored, so it recalculates from the full history — the same "no drifting stored total" approach the account balances already use (feature 007).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST let a user assign (allocate) a specific monetary amount, for a specific month, to a specific category.
- **FR-002**: System MUST calculate a running unallocated balance as cumulative recorded income to date minus the cumulative sum of all category allocations entered to date (across all months, not reset at month boundaries).
- **FR-003**: System MUST let a user update the amount they assigned to a category in a given month; updating replaces that month's entry rather than adding a second one (upsert), consistent with the existing budget behavior in feature 002 — carryover from prior months in that category's envelope is unaffected.
- **FR-004**: System MUST calculate each category's envelope balance as the cumulative amount ever allocated to that category minus the cumulative amount actually spent in that category, to date.
- **FR-005**: System MUST let a user move a specified amount from one category's envelope balance to another category's envelope balance, leaving the overall unallocated total unchanged.
- **FR-006**: System MUST reject an attempted move that exceeds the source category's currently available envelope balance.
- **FR-007**: System MUST visually distinguish a negative unallocated balance (cumulative allocations exceeding cumulative income) from a positive one.
- **FR-008**: System MUST display, per category and month, that month's assigned amount and actual spending, alongside the category's current running envelope balance.
- **FR-009**: System MUST only offer categories of type EXPENSE or BOTH as allocation targets; INCOME-only categories are excluded.
- **FR-010**: System MUST let a user repeat one month's assignment amounts as new allocation entries in another month (topping up each category's envelope rather than resetting it), warning before adding a top-up to any category that already has a new entry of its own in the target month.
- **FR-011**: A category's unspent envelope balance MUST carry forward automatically into the next month with no action required from the user — nothing resets to zero at a month boundary; each month's view simply reflects the running totals to date.

### Key Entities *(include if feature involves data)*

- **Allocation**: A reframing of the existing Budget entity (feature 002) — a monetary amount assigned to a specific category in a specific year and month. One allocation entry exists per category per month (upsert semantics); the entry itself is still scoped to the month it was made in, even though the balances derived from it are cumulative.
- **Unallocated Balance**: A derived, running figure — cumulative recorded income to date minus the cumulative sum of all allocation entries to date. Not reset per month.
- **Category Envelope Balance**: A derived, running figure per category — the cumulative amount ever allocated to that category minus the cumulative amount actually spent in it. Carries forward automatically.
- **Category** *(existing, unchanged)*: Only categories of type EXPENSE or BOTH are valid allocation targets.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can see how much of the current month's income is still unassigned within one view, with no additional navigation or calculation required.
- **SC-002**: A user can assign their full month's income across a typical 8–10 category budget in under 3 minutes.
- **SC-003**: Moving money from one category's allocation to another takes no more than two user actions (no need to zero one category and separately re-create the other).
- **SC-004**: Repeating a prior month's recurring assignment amounts into a new month takes a single action, cutting a typical month's re-entry work by at least 80% compared to re-typing every category from scratch.
- **SC-005**: Every point where cumulative allocations exceed cumulative income is visually flagged before the user navigates away from the budgeting view — 100% of over-allocated states are visibly marked, not silently allowed.

## Assumptions

- "Income to date" reuses the existing Spending Analysis API's recorded income; it is not a separately entered income target or forecast.
- Allocations are informational, like the existing budget limits in feature 002 — over-allocating, or overspending a category's envelope, is permitted and visually flagged, never blocked.
- Multi-currency conversion is out of scope; allocations are tracked in the same single-currency model the existing budget and analysis features already use.
- No authentication or multi-user support is introduced — this remains a single-user application, consistent with the rest of the product.
- Feature 008, developed on a separate, unmerged branch, is out of scope and is not read from or modified by this feature.
- Rolling carryover applies going forward from whenever this feature ships; it does not retroactively reinterpret budget data recorded under feature 002 before that point.
