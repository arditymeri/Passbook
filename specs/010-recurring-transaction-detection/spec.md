# Feature Specification: Recurring Transaction Detection

**Feature Branch**: `010-recurring-transaction-detection`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Recurring transaction detection. Detect that a bill or income repeats on a regular schedule (e.g. monthly), surface upcoming recurring charges/income before they happen, and flag when a recurring amount changes (price creep). Builds on the existing Bill/Income domain concepts (which already have a `recurring`/`recurringFrequency` flag, currently unused by any feature) and the transaction history already visible on the dashboard."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See What's Coming Up (Priority: P1)

As a user, I want to see, at a glance, which bills and income I expect in the near future based on things that have recurred before, so I can anticipate my cash flow instead of being surprised by a charge.

**Why this priority**: This is the entire value proposition — an "upcoming" list is the payoff. Without it, recognizing a transaction as recurring has no visible benefit.

**Independent Test**: With a bill that has occurred on the same schedule at least twice before, open the dashboard and verify it appears in an "upcoming" list with a predicted date and amount, before that date arrives.

**Acceptance Scenarios**:

1. **Given** a bill has recurred monthly for the last 3 months, **When** the user opens the dashboard within a week of the next expected date, **Then** it appears in an "Upcoming" list showing the category/description, predicted date, and predicted amount.
2. **Given** a recurring bill's predicted date has passed without a matching new bill being recorded, **When** the user views the upcoming list, **Then** that item is shown as overdue rather than silently disappearing.
3. **Given** no transaction has ever recurred, **When** the user opens the dashboard, **Then** the upcoming list shows a clear empty state rather than an error.
4. **Given** a recurring bill's next occurrence is recorded (a normal new bill entry matching the series), **When** the user views the upcoming list afterward, **Then** that occurrence is no longer shown as upcoming and the prediction advances to the following cycle.

---

### User Story 2 - Recognize a Recurring Series (Priority: P1)

As a user, I want the app to recognize which of my bills and income are recurring — including ones I never explicitly flagged as such when I recorded them — so I don't have to manually maintain the recurring flag on every transaction myself.

**Why this priority**: User Story 1 has nothing to display without this. Recognizing a series from history is the core mechanism the rest of the feature depends on.

**Independent Test**: Record three bills in the same category with the same description and a similar amount, one month apart, without ever marking any of them recurring. Verify the app recognizes them as one recurring series and offers to confirm it.

**Acceptance Scenarios**:

1. **Given** three bills with the same category, a matching description, and amounts within a small tolerance of each other, spaced roughly a month apart, **When** the user views their transactions, **Then** the app proposes them as a recurring series pending the user's confirmation.
2. **Given** a proposed recurring series, **When** the user confirms it, **Then** it starts appearing in the upcoming list (User Story 1) from then on.
3. **Given** a proposed recurring series, **When** the user dismisses it, **Then** it is not proposed again for those same transactions, and does not appear in the upcoming list.
4. **Given** a bill was explicitly marked recurring at creation (the existing `recurring`/`recurringFrequency` fields), **When** only one such bill exists so far, **Then** it is still eligible to be recognized as a series once a second matching occurrence is recorded, without waiting for a third.

---

### User Story 3 - Get Warned About a Price Change (Priority: P2)

As a user, I want to be told when a new occurrence of a recurring bill costs a different amount than last time, so I notice subscription price increases or billing errors instead of paying them on autopilot.

**Why this priority**: This is the feature's other half of its value (beyond "what's coming") but depends on User Story 2's series recognition already existing.

**Independent Test**: With a recognized recurring series, record a new occurrence at a different amount than the series' most recent one, and verify it is visually flagged as a change from the prior amount.

**Acceptance Scenarios**:

1. **Given** a recurring series' last occurrence was $45 and a new occurrence for the same series is recorded at $52, **When** the user views their transactions, **Then** the new occurrence is flagged with the amount change (e.g. "+$7 vs. last time").
2. **Given** a recurring series' new occurrence is within a small tolerance of the prior amount, **When** the user views it, **Then** it is not flagged as a change.
3. **Given** a flagged price change, **When** the user views the upcoming list afterward, **Then** the predicted amount for that series updates to the new value.

---

### Edge Cases

- What happens when two unrelated bills happen to share a category, description, and similar amount but are not actually meant to recur (e.g. two unrelated one-off purchases at the same store)? The user can dismiss the proposed series (US2 Scenario 3), and a dismissed pairing is not re-proposed.
- What happens when a recurring series' cadence isn't perfectly regular (e.g. a bill lands on the 3rd one month and the 5th the next)? Predictions tolerate a reasonable date window rather than requiring an exact day match.
- What happens when a recurring bill is corrected or removed (feature 008)? The series recognition and upcoming prediction use the same reversal-aware read path every other aggregation in this app already uses, so a corrected occurrence's post-correction amount is what counts.
- What happens when the user deletes/dismisses a recurring series after several occurrences have already been recorded? Past occurrences remain in transaction history unchanged; only future predictions and price-change flagging stop.
- What happens with irregular but still-recurring expenses (e.g. an annual renewal)? Covered as long as the frequency is one of the supported cadences (see Assumptions) and at least two matching occurrences exist.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST identify a "recurring series" by matching bills (or, separately, incomes) that share a category and a sufficiently similar description/payee, recur at roughly the same interval, and have amounts within a reasonable tolerance of each other — from transaction history generally, regardless of whether any of the matched transactions were ever marked with the existing `recurring` flag. A transaction already marked `recurring` MAY be used as a hint that shortens how many matching occurrences are needed (see US2 Scenario 4), but the flag is never required for a series to be proposed.
- **FR-002**: System MUST require at least three matching occurrences before proposing a series — or two, if at least one of the matching transactions was already marked with the existing `recurring` flag (US2 Scenario 4) — and MUST require explicit user confirmation before a proposed series starts producing predictions (US2).
- **FR-003**: Users MUST be able to dismiss a proposed series, after which the same set of transactions is not re-proposed.
- **FR-004**: System MUST predict the next expected date and amount for each confirmed recurring series, based on its most recent occurrences and its cadence.
- **FR-005**: System MUST display confirmed recurring series expected within a near-term window (see Assumptions) in an "Upcoming" view, showing category/description, predicted date, and predicted amount.
- **FR-006**: System MUST mark a predicted occurrence as overdue if its predicted date has passed with no matching new transaction recorded, rather than removing it silently.
- **FR-007**: System MUST advance a series' prediction to the next cycle once a matching new occurrence is recorded.
- **FR-008**: System MUST flag a newly recorded occurrence of a confirmed recurring series when its amount differs from the series' most recent occurrence by more than a small tolerance, showing the prior amount, the new amount, and the difference.
- **FR-009**: System MUST NOT flag an amount as changed when the difference from the prior occurrence is within that tolerance.
- **FR-010**: Series recognition and prediction MUST use the same reversal-inclusive read path the rest of the app already uses (Principle: corrected/removed bills and incomes net out to their post-correction value automatically, per feature 008).
- **FR-011**: Users MUST be able to see the list of confirmed recurring series and stop tracking one, after which it no longer produces upcoming predictions or price-change flags.

### Key Entities *(include if feature involves data)*

- **Recurring Series**: A recognized (proposed or confirmed) group of bills, or of incomes, believed to represent the same repeating obligation — category, description/payee pattern, cadence, and the amount and date of its most recent occurrence. Not itself a financial transaction; it references the bills/incomes that belong to it.
- **Bill / Income** *(existing, unchanged)*: The individual transaction rows a series is built from; the existing `recurring`/`recurringFrequency` fields remain as-is.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can see every bill and income they can reasonably expect in the next two weeks in one place, without reviewing their full transaction history.
- **SC-002**: A recurring obligation is recognized and proposed to the user after at most 3 occurrences, with no manual setup required beyond confirming it.
- **SC-003**: 100% of newly recorded occurrences whose amount changes from a confirmed series' prior amount by more than the tolerance are flagged — none pass through unnoticed.
- **SC-004**: A user can dismiss an incorrect series proposal in a single action, and it does not reappear.
- **SC-005**: Predicted dates fall within the tolerance window of the actual next occurrence at least 90% of the time for a series with a consistent history.

## Assumptions

- Supported cadences are the same set the existing `recurringFrequency` field already defines: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`. This feature does not introduce new cadence types.
- "Near-term" for the Upcoming view defaults to the next 14 days; "reasonable date tolerance" for a match or a prediction defaults to ±3 days for a monthly cadence (scaled proportionally for other cadences).
- Amount tolerance for "still the same charge" vs. "price changed" defaults to whichever is larger: 5% of the prior amount, or a fixed small absolute amount — consistent with how subscription price changes are typically small percentage increases rather than wholesale differences.
- Series recognition is scoped per bill and per income independently; a bill series and an income series are never merged even if coincidentally similar.
- No authentication or multi-user support is introduced — this remains a single-user application, consistent with the rest of the product.
- This feature does not send external notifications (email/push) for upcoming or overdue items — visibility is limited to the in-app Upcoming view, consistent with the app having no notification infrastructure today.
