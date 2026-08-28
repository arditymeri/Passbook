# Feature Specification: Net Worth Trend

**Feature Branch**: `014-net-worth-trend`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Net worth trend. Show the user's current total net worth (sum of all account balances) prominently on the dashboard, and a trend chart of how it has changed over recent months, so they can tell at a glance whether their overall finances are trending up or down. Let the user adjust how far back the trend shows (e.g. 3/6/12 months). Builds on the existing account balance derivation (GetAccountService already derives each account's current balance from its transaction history) and the dashboard patterns already established by prior features."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See Current Total Net Worth (Priority: P1)

A user opens their dashboard and wants to know, at a glance, what their overall financial
position is right now — the sum of everything across all their accounts, in one number, without
adding it up themselves.

**Why this priority**: This is the simplest, most immediately useful form of the feature — a
single live total. Every other part of the feature (the trend, the adjustable range) builds on
top of a number that already has to exist and already has to be correct.

**Independent Test**: Can be fully tested by checking that the total net worth shown on the
dashboard matches the sum of every account's current balance, and that it updates after a new
transaction is recorded, corrected, or removed, or after an account is added.

**Acceptance Scenarios**:

1. **Given** a user with several accounts of varying balances, **When** they view the dashboard,
   **Then** they see a single total net worth figure equal to the sum of every account's current
   balance.
2. **Given** the user records a new bill or income afterward, **When** they return to the
   dashboard, **Then** the total net worth reflects that new transaction without any extra action.
3. **Given** the user has no accounts yet, **When** they view the dashboard, **Then** the net worth
   is shown as zero with a clear indication there's nothing to total yet, not as an error.

---

### User Story 2 - See the Trend Over Recent Months (Priority: P1)

Beyond just today's number, a user wants to see how their net worth has moved over the past
several months — whether they're steadily building it up, holding roughly steady, or losing
ground — so they can tell at a glance if they're heading in the right direction.

**Why this priority**: This is the actual point of the feature as requested — a trend, not just a
snapshot. Equal priority to US1 because a total with no history behind it doesn't answer "am I
doing better or worse than before," which is the question this feature exists to answer.

**Independent Test**: Can be fully tested by viewing the trend and confirming each point in it
matches the total net worth as of that period, computed independently from account and transaction
history — including a period before some of the user's current transactions existed.

**Acceptance Scenarios**:

1. **Given** a user with transaction history spanning several months, **When** they view the net
   worth trend, **Then** they see one point per recent month, each showing the total net worth as
   of that month.
2. **Given** the user's net worth has grown over the shown period, **When** they view the trend,
   **Then** the shape of the trend visibly reflects that growth (the values rise from earlier to
   later points).
3. **Given** a transaction from a past month is later corrected, **When** the user views the trend
   again, **Then** the affected period(s) reflect the corrected value, not the original.

---

### User Story 3 - Adjust How Far Back the Trend Shows (Priority: P2)

A user wants to see just the last few months for a short-term view, or a full year to see the
bigger picture, depending on what they're trying to understand.

**Why this priority**: A useful refinement once the trend itself exists (US2), but the feature
already delivers its core value with one sensible default range — this is about giving the user
control over the window, not a prerequisite for the trend to be meaningful.

**Independent Test**: Can be fully tested by selecting a different time-range option and
confirming the trend updates to show that many months' worth of points, computed the same way as
the default range.

**Acceptance Scenarios**:

1. **Given** the trend is showing its default range, **When** the user selects a shorter range,
   **Then** the trend updates to show only that many recent months.
2. **Given** the user selects a longer range, **When** they view the trend, **Then** it extends
   further back while every already-shown point stays the same.

---

### Edge Cases

- A user with exactly one account: the trend still renders as a single-series total — a second
  account is never required for the feature to be meaningful.
- An account didn't exist yet at the start of the selected time range: periods before the account
  had any transactions still include it, using its balance as of that point (there being no
  transactions yet is itself a valid, computable state — see Assumptions) rather than treating the
  account as absent or producing a gap in the trend.
- A period with no transactions at all across every account: that point in the trend simply shows
  the same net worth as the period before it (nothing changed), not a zero or a gap.
- Reversed/corrected transactions (008): the trend always reflects each transaction's current
  corrected value, and a reversed transaction's original amount never double-counts alongside its
  reversal, consistent with how every other feature already reads transaction history.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display the user's current total net worth — the sum of every account's
  current balance — on the dashboard.
- **FR-002**: The displayed net worth MUST reflect the latest transaction and account data
  automatically, without requiring the user to take any extra action to refresh it.
- **FR-003**: System MUST show a trend of total net worth across a series of recent past months, so
  the user can see whether it is increasing, decreasing, or flat over time.
- **FR-004**: Each point in the trend MUST reflect total net worth as of the end of that month,
  computed from the same account and transaction data as the current total — never a separately
  maintained, cached, or manually entered figure.
- **FR-005**: Users MUST be able to change how far back the trend shows, choosing from a small set
  of preset ranges (e.g. 3, 6, and 12 months).
- **FR-006**: The trend MUST include every account that currently exists, the same set the current
  total net worth includes.
- **FR-007**: If the user has no accounts yet, the feature MUST show a clear zero/empty state
  rather than an error or a blank chart.
- **FR-008**: A corrected transaction MUST be reflected in the trend using its corrected value; a
  reversed transaction MUST NOT be double-counted alongside its reversal.

### Key Entities

*(none — this feature only derives new views over existing Account, Bill, and Income data; no new
entity is introduced)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can see their current total net worth on the dashboard without navigating
  away or performing any calculation themselves.
- **SC-002**: The net worth trend accurately reflects account and transaction history in 100% of
  tested scenarios, including corrected and reversed transactions.
- **SC-003**: A user can switch between time-range options and see the trend update to match
  within a couple of seconds.
- **SC-004**: A user with zero accounts sees a clear empty state instead of an error or a blank
  chart.

## Assumptions

- Net worth is the simple sum of every account's current derived balance. A credit card (or any
  account type that can run negative) already contributes negatively once its balance goes below
  zero, so no separate "liability" handling is needed beyond the balance derivation every account
  already uses.
- Trend periods are calendar months; each point shows net worth as of the end of that month, with
  the most recent point reflecting the current, live total.
- Accounts don't currently record a creation date. A period before an account has any transactions
  is therefore computed the same way as any other period — using that account's balance as of that
  point in time — rather than excluding the account, since there's no reliable signal to say the
  account "didn't exist yet" versus "existed with no activity yet."
- The adjustable range offers a small set of presets (3/6/12 months) rather than an arbitrary
  custom date-range picker, matching the low-friction, preset-based filters already used elsewhere
  in this app.
- No new persisted entity or stored history is introduced — net worth at any past point is derived
  fresh from existing account and transaction data, the same "derive, don't store" approach every
  prior balance/progress feature in this app already uses.
