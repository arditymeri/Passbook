# Feature Specification: Spending Trends & Insights

**Feature Branch**: `016-spending-trends-insights`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: "Spending trends & insights. Show category spending over time (multi-month trend, not just the current month), and surface the biggest movers month-over-month so a user can see which categories drove a change in their spending without manually comparing months. Builds on the existing single-month CategorySpend view and the monthly summary/spending-by-category data the app already computes, extending it to a multi-month history the way net worth trend (014) already does for account balances."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See How a Category's Spending Trends Over Time (Priority: P1)

A user wants to see whether their spending in a given category (groceries, dining out, subscriptions)
has been going up, going down, or staying flat over the past several months — not just what they
spent this month.

**Why this priority**: This is the core value of the feature — turning a single-month snapshot into
a trend a user can actually act on ("I've been spending more on dining out every month for three
months"). Everything else in this feature supports or refines this one view.

**Independent Test**: Can be fully tested by viewing the trend for a category with a rising,
falling, and flat spending pattern across several months and confirming each pattern is visibly
distinguishable, without comparing raw numbers by hand.

**Acceptance Scenarios**:

1. **Given** a category with steadily increasing spending over the past several months, **When**
   the user views its trend, **Then** the upward pattern is visually clear across all shown months.
2. **Given** a category with no spending at all in one of the shown months, **When** the user views
   its trend, **Then** that month shows as zero rather than being skipped or missing from the trend.
3. **Given** a category the user has never spent in during the shown window, **When** the user
   views the overall trend, **Then** that category does not clutter the view with an all-zero row.

---

### User Story 2 - See Which Categories Drove This Month's Change (Priority: P1)

A user wants to immediately see which one or two categories are responsible for their spending
being higher or lower than last month, instead of opening every category and comparing numbers by
hand.

**Why this priority**: Equal priority to User Story 1 because together they deliver the minimum
useful version of "insights" — a trend view without a call-out of what changed forces the user to
do the comparison work themselves, defeating the feature's purpose.

**Independent Test**: Can be fully tested by setting up two months where one category's spending
rose sharply and another's fell sharply, then confirming both appear clearly as "movers" versus the
prior month, ranked by size of change.

**Acceptance Scenarios**:

1. **Given** a category whose spending increased significantly from the prior month to the current
   one, **When** the user views the movers, **Then** that category is listed as a mover with the
   size of the increase shown.
2. **Given** a category whose spending decreased significantly from the prior month to the current
   one, **When** the user views the movers, **Then** that category is listed as a mover with the
   size of the decrease shown, distinguishable from an increase.
3. **Given** a category with no spending last month that has spending this month (a brand-new or
   resumed category), **When** the user views the movers, **Then** it is still recognized as a
   mover — the comparison works from zero, not omitted for lack of a prior-month baseline.

---

### User Story 3 - Adjust How Far Back the Trend Looks (Priority: P2)

A user wants to look at a longer history (a full year, to see seasonal patterns) or a shorter one
(just the last few months, to focus on recent behavior).

**Why this priority**: A useful refinement once the trend itself exists (User Stories 1-2), but the
feature already delivers its core value with one sensible default window — this is about giving the
user control over how far back to look, not a prerequisite for the trend to be meaningful.

**Independent Test**: Can be fully tested by selecting a different window option and confirming the
displayed trend recomputes to cover that many months.

**Acceptance Scenarios**:

1. **Given** the trend is showing its default window, **When** the user selects a shorter window,
   **Then** the trend updates to show only that nearer-term history.
2. **Given** the user selects a longer window, **When** they view the trend, **Then** it extends
   further back, potentially revealing a pattern not visible in the shorter window.

---

### Edge Cases

- A category with zero spending in some (but not all) shown months: shown as zero for those months,
  not omitted from that category's own trend line.
- A category with no spending at all across the entire shown window: omitted from the trend view
  entirely, to avoid cluttering it with all-zero rows (see User Story 1, acceptance scenario 3).
- A transaction whose category was later deleted: the category's historical spending remains part
  of the trend for the months it occurred in, consistent with how the rest of the app already
  treats historical transactions after their category is removed.
- A past transaction gets corrected (amount changed) or reversed: the trend and any mover
  comparison reflect the corrected value, never the original, stale amount.
- The very first month a user has any transaction history at all: there is no prior month to
  compare against for movers — that first month simply isn't eligible to be a "current month" for
  the movers comparison.
- Two categories tie for the largest change in a given comparison: both are shown; no requirement
  to arbitrarily pick one over the other.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST show, for each expense category with any activity in the selected
  window, the amount spent in each month of that window — not only the current month.
- **FR-002**: A month with no spending in a category MUST be shown as zero for that category,
  never omitted or left blank within that category's trend.
- **FR-003**: System MUST identify and highlight the categories with the largest change in
  spending between the current month and the immediately preceding month, covering both increases
  and decreases.
- **FR-004**: A category with no spending in the preceding month but spending in the current month
  (or vice versa) MUST still be correctly recognized as a mover, comparing against zero.
- **FR-005**: Users MUST be able to adjust how many months of history are shown, choosing from a
  small set of preset window lengths.
- **FR-006**: Trend and mover figures MUST reflect each transaction's current, corrected value —
  never a stale amount from before a correction or reversal.
- **FR-007**: A category with no spending anywhere in the selected window MUST NOT appear in the
  trend view.
- **FR-008**: Viewing trends and movers MUST NOT create, modify, or remove any bill, income, or
  category record — it is a read-only view.

### Key Entities

*(none — this feature only derives a new multi-month view over existing Category and transaction
history data; no new entity is introduced)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can tell whether a category's spending is trending up, down, or flat over the
  past several months without manually comparing individual months' figures themselves.
- **SC-002**: A user can identify, at a glance and without opening each category individually, the
  1-3 categories that most changed their spending compared to the prior month.
- **SC-003**: A user can change the trend window and see it recompute within a couple of seconds.
- **SC-004**: A category with zero activity in part of the window never produces an error or a
  visible gap in that category's displayed trend, in 100% of tested scenarios.

## Assumptions

- Trends cover expense (bill) categories only, consistent with the app's existing single-month
  "spending by category" view — this feature extends that existing scope across time rather than
  broadening it to include income. Income's own trend over time is already covered by the existing
  net worth trend feature at the account-balance level.
- "Movers" compares the current month against the immediately preceding month by the absolute
  currency amount of change (the size of the swing in euros), which is shown alongside the
  percentage change for context — not ranked by percentage alone, which would let a tiny category
  going from €1 to €5 outrank a category that grew by hundreds of euros.
- The trend window offers a small preset set of month counts (e.g. 3/6/12), mirroring the existing
  net worth trend feature's precedent, with the same kind of sensible mid-range default.
- Nothing about the trend or movers is stored — like every prior derived feature in this app, it is
  recomputed fresh from current category and transaction data every time it is viewed, so it always
  reflects the latest corrections.
- A deleted category's historical spending is still included in the months it actually occurred,
  consistent with how the rest of the app treats transactions after their category is removed
  (existing behavior this feature does not change).
