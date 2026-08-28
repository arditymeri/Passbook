# Feature Specification: Cash Flow Forecast

**Feature Branch**: `015-cash-flow-forecast`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: "Cash flow forecast. Project each account's balance forward through the near future using the recurring bills and income the app already recognizes, so a user can see whether an account is on track to run low or negative before their next expected income — and see the day-by-day timeline that explains why, not just a yes/no warning. Builds on the existing recurring-series recognition (confirmed series with a predicted cadence and amount) and account balance derivation."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get Warned About an Upcoming Low Balance (Priority: P1)

A user wants to know, before it happens, whether one of their accounts is on track to run low or
go negative in the near future — because their rent, a subscription, or another confirmed
recurring bill is coming up before their next expected paycheck.

**Why this priority**: This is the entire reason the feature exists — a proactive warning a user
can act on, which nothing else in the app currently provides. Everything else in this feature
supports or refines this one outcome.

**Independent Test**: Can be fully tested by setting up an account with confirmed recurring bills
that, combined with its current balance, would drive it negative before the next confirmed
recurring income; viewing the forecast should show a clear warning for that account. An account
with no such risk should show no warning.

**Acceptance Scenarios**:

1. **Given** an account whose confirmed recurring bills would take its balance below zero within
   the forecast window, **When** the user views the forecast, **Then** that account is clearly
   flagged as at risk of going negative.
2. **Given** an account with confirmed recurring income and bills that keep its balance
   comfortably positive throughout the window, **When** the user views the forecast, **Then** no
   warning is shown for that account.
3. **Given** an account that is already negative today, **When** the user views the forecast,
   **Then** it is flagged just as clearly as one projected to go negative later in the window —
   the warning covers "already negative" and "about to become negative" the same way.

---

### User Story 2 - See the Day-by-Day Forecast (Priority: P1)

Beyond a yes/no warning, a user wants to see the actual projected timeline for an account — which
upcoming bills and income are expected, on which dates, and how the balance moves as a result — so
they understand exactly what's driving the projection and can decide what to do about it.

**Why this priority**: A warning with no explanation isn't actionable — a user needs to see *which*
upcoming item causes the dip to make an informed decision (move money, delay a purchase, etc.).
Equal priority to US1 because together they're the minimum viable version of "a forecast," not two
separable increments of value.

**Independent Test**: Can be fully tested by viewing an account's forecast and confirming it shows
one entry per confirmed recurring bill/income occurrence expected within the window, each with its
predicted date and amount, with the projected running balance visibly changing at each one.

**Acceptance Scenarios**:

1. **Given** an account with two confirmed recurring bills and one confirmed recurring income
   falling within the forecast window, **When** the user views its forecast, **Then** they see all
   three occurrences in date order, each with the amount that drives the balance up or down at
   that point.
2. **Given** a confirmed recurring series whose cadence means it recurs more than once within the
   forecast window (e.g. a weekly bill in a four-week window), **When** the user views the
   forecast, **Then** every occurrence expected within the window appears, not just the first one.
3. **Given** a past transaction that a confirmed series' prediction is based on gets corrected,
   **When** the user views the forecast again, **Then** the projection reflects the corrected
   amount, not the original.

---

### User Story 3 - Adjust the Forecast Window (Priority: P2)

A user wants to look further ahead (to plan around a known irregular expense two months out) or
closer in (to focus on just the next couple of weeks), depending on what they're trying to check.

**Why this priority**: A useful refinement once the forecast itself exists (US1/US2), but the
feature already delivers its core value with one sensible default window — this is about giving
the user control over how far ahead to look, not a prerequisite for the forecast to be meaningful.

**Independent Test**: Can be fully tested by selecting a different window option and confirming the
forecast recomputes to cover that many weeks, with any warning updating to match.

**Acceptance Scenarios**:

1. **Given** the forecast is showing its default window, **When** the user selects a shorter
   window, **Then** the forecast and any warning update to reflect only that nearer-term period.
2. **Given** the user selects a longer window, **When** they view the forecast, **Then** it
   extends further out, potentially revealing a risk that wasn't visible in the shorter window.

---

### Edge Cases

- An account with no confirmed recurring series at all: its forecast is flat at the current
  balance for the whole window, with no warning — not an error or an empty state.
- A confirmed series whose past occurrences were recorded against more than one account: the
  forecast attributes its predicted future occurrences to the account of its most recent
  occurrence (see Assumptions).
- A confirmed series that is already overdue (its previously predicted date has passed with no new
  occurrence recorded): its first projected occurrence in the forecast is treated as due now rather
  than at its stale past date, so it isn't silently omitted from the forecast just because it's
  late.
- A reversed/corrected transaction affecting a series' predicted amount: the forecast always
  reflects the current corrected value, consistent with how every other feature already reads
  transaction history.
- Two confirmed series that would both post on the same date: both are reflected in that date's
  projected balance change, not just one.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST project each account's balance forward through a near-future window,
  starting from that account's current balance.
- **FR-002**: The projection MUST be built from confirmed recurring bill and income series already
  recognized by the app, predicting every occurrence of each series expected to fall within the
  window — not only the next occurrence.
- **FR-003**: System MUST warn when an account's projected balance is negative at any point within
  the forecast window, including if it is already negative today.
- **FR-004**: Users MUST be able to see the day-by-day (occurrence-by-occurrence) projected
  timeline for an account — each expected bill/income, its date, its amount, and the resulting
  projected balance — not only a summary warning.
- **FR-005**: Users MUST be able to adjust how far ahead the forecast looks, choosing from a small
  set of preset windows.
- **FR-006**: An account with no confirmed recurring series MUST still show a forecast (unchanged
  from its current balance across the window), not an error.
- **FR-007**: The forecast MUST reflect each transaction's current corrected value, the same
  correction-aware transaction history every other feature already reads — never a stale
  pre-correction amount.
- **FR-008**: Generating or viewing a forecast MUST NOT create, modify, or remove any bill, income,
  account, or recurring-series record — it is a read-only projection.

### Key Entities

*(none — this feature only derives a new projection over existing Account and confirmed
RecurringSeries data; no new entity is introduced)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user with an account on track to run negative within the forecast window can see
  that warning without manually adding up their own upcoming bills and income.
- **SC-002**: The day-by-day projection includes every confirmed recurring occurrence that falls
  within the selected window, in 100% of tested scenarios, including series that recur more than
  once within the window.
- **SC-003**: A user can change the forecast window and see it recompute within a couple of
  seconds.
- **SC-004**: An account with no confirmed recurring series shows a flat forecast and no warning,
  never an error.

## Assumptions

- Only confirmed recurring series feed the forecast — proposed or dismissed series, and any future
  expense or income the user knows about but hasn't recorded or that hasn't yet been recognized as
  recurring, are not included. A user who wants something reflected in the forecast needs to have
  already confirmed it as a recurring series.
- When a confirmed series' past occurrences were recorded against more than one account, the
  forecast attributes its predicted future occurrences to the account of its most recent
  occurrence — the same account its existing single "next occurrence" prediction already
  implicitly reflects.
- An overdue series (already past its previously predicted date with nothing new recorded) is
  treated as due "now" for forecasting purposes, with later occurrences following at its normal
  cadence from that point.
- The forecast is shown per account, not as one combined total — going negative is a specific,
  actionable event for an asset-type account, unlike a credit card, which is expected to carry a
  negative balance as debt accrues. Combining every account into one number would obscure which
  specific account needs attention.
- Like every prior derived feature in this app, nothing about the forecast is stored — it's
  recomputed fresh from current account balances and confirmed recurring series every time it's
  viewed.
