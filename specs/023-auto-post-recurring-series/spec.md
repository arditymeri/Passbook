# Feature Specification: Auto-Post Confirmed Recurring Series

**Feature Branch**: `023-auto-post-recurring-series`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "Make confirming a recurring series mean something. Detection has existed since feature 010 and stops at detection — confirming flips a status flag and writes nothing, which the README roadmap names as an open item. A confirmed series should write its own transactions, so rent, salary and subscriptions appear without anyone typing them. Posting is scheduled daily and catches up periods missed while the instance was switched off. Each auto-posted transaction carries a deterministic identity derived from the series and the period it covers, so feature 022's uniqueness guarantee makes posting the same period twice impossible however many times the job runs. When an imported transaction matches an auto-posted one, the predicted entry is superseded — reversed with a compensating entry, never deleted, so the bank's fact stands while the ledger keeps an auditable trace of what was guessed. An operator must be able to see which transactions the app wrote on their behalf, and stop a series auto-posting without losing its detection history. Out of scope: learned auto-categorisation, CAMT.053/MT940 parsing, multi-currency, bank synchronisation, and any change to how series are detected or confirmed."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rent Appears Without Being Typed (Priority: P1)

An operator has confirmed that their monthly rent is a recurring series. On the day it is due, the
transaction is in their history — in their balance, counted against their budget, visible in their
analysis — without them having entered anything.

**Why this priority**: This is the feature. Confirming a series has meant nothing since detection
shipped, and it is the one place in the app where the operator has already told Passbook exactly
what to expect and Passbook still makes them type it in.

**Independent Test**: Confirm a series whose next occurrence is due today, let posting run, and
confirm the transaction exists with the right amount, date, account and category.

**Acceptance Scenarios**:

1. **Given** a confirmed series with an occurrence due today, **When** posting runs, **Then** a
   transaction is recorded for that occurrence and appears everywhere an ordinary transaction does.
2. **Given** posting has already run today, **When** it runs again, **Then** nothing further is
   recorded — the same period is never posted twice.
3. **Given** a series that is proposed or dismissed rather than confirmed, **When** posting runs,
   **Then** nothing is recorded for it.
4. **Given** a confirmed series with no occurrence due, **When** posting runs, **Then** nothing is
   recorded for it.

---

### User Story 2 - The Bank's Version Wins, Without Leaving Two (Priority: P1)

The app posted the operator's rent on the 1st. Later they import their bank statement, which
contains the real rent. They end up with one rent in their history — the bank's — and can still see
that the app had predicted it.

**Why this priority**: Equally blocking, and the reason this feature is harder than it looks. Now
that statement import is reliable, every auto-posted transaction for an imported account is on a
collision course with the bank's own booking. Leaving both is double-counting; deleting the
prediction is forbidden. Without this, auto-posting makes the ledger worse rather than better.

**Independent Test**: Auto-post an occurrence, then import a statement containing the matching real
transaction, and confirm the operator's balance counts it once and the history explains why.

**Acceptance Scenarios**:

1. **Given** an auto-posted transaction for a period, **When** an imported transaction matching that
   series and period arrives, **Then** the auto-posted entry is superseded and the operator's
   balance reflects the imported amount only.
2. **Given** that supersession has happened, **When** the operator looks at the history, **Then** the
   original auto-posted entry is still present along with the entry that cancels it — nothing was
   deleted.
3. **Given** an imported transaction that resembles a series but falls outside the expected timing or
   amount, **When** it is ingested, **Then** it is recorded as an ordinary new transaction and no
   auto-posted entry is superseded.
4. **Given** an auto-posted entry that has already been superseded, **When** another matching import
   arrives, **Then** it is recorded normally and the already-superseded entry is not touched again.

---

### User Story 3 - See What the App Wrote On Your Behalf (Priority: P1)

An operator can tell, at a glance, which transactions they entered, which came from their bank, and
which the app posted for them — and can find every transaction of the last kind in one place.

**Why this priority**: The constitution is explicit that when the pipeline writes transactions on
the operator's behalf, "why is this row here?" must be answerable, and that this matters *more*
once rows arrive automatically, not less. An automatically posted transaction the operator cannot
distinguish from their own is indistinguishable from a bug.

**Independent Test**: After auto-posting has run, identify every transaction the app created,
without inspecting the database.

**Acceptance Scenarios**:

1. **Given** a history containing hand-entered, imported and auto-posted transactions, **When** the
   operator views it, **Then** the auto-posted ones are identifiable as such.
2. **Given** an auto-posted transaction, **When** the operator inspects it, **Then** they can see
   which series produced it and which period it covers.

---

### User Story 4 - Stop a Series Posting Without Losing What Was Learned (Priority: P2)

An operator cancels their gym membership. They stop the series posting, and the app neither keeps
inventing the payment nor forgets that the series existed.

**Why this priority**: Necessary for the feature to be livable — a series that cannot be stopped is
a liability rather than a convenience. It follows US1–US3 because an operator who never stops one
still gets correct behaviour; they simply have no escape hatch yet.

**Independent Test**: Stop a confirmed series, run posting, confirm nothing new is written, and
confirm the series and its past occurrences are still visible.

**Acceptance Scenarios**:

1. **Given** a confirmed, auto-posting series, **When** the operator stops it, **Then** no further
   transactions are posted for it.
2. **Given** a stopped series, **When** the operator views their recurring series, **Then** it is
   still listed with its history, distinguishable from one that was never confirmed.
3. **Given** transactions already auto-posted before stopping, **When** the series is stopped,
   **Then** those transactions are left exactly as they are.

---

### Edge Cases

- What if the instance was switched off for three weeks? Every period missed in that time must be
  posted on the next run, each exactly once — not one, and not one per day of downtime.
- What if posting runs twice at the same moment, or the operator triggers it while the scheduled run
  is in progress? Each period must still exist exactly once.
- What if a series has never had a past occurrence recorded — can it be posted at all? Its amount,
  account and category are only knowable from its own history, so a series with no usable history
  must be skipped rather than posted with invented values.
- What if the operator changes a series' amount in reality (rent goes up) and the bank's figure
  differs from the prediction? The imported figure is the fact and must win; the prediction is
  superseded on the same terms as any other match.
- What if an imported transaction could match two auto-posted entries — say a series posted for two
  adjacent periods and one import falls between them? Exactly one may be superseded, chosen
  predictably, and the operator must not silently lose the other.
- What if an operator deletes (corrects away) an auto-posted transaction themselves? The period must
  count as handled — the app must not helpfully post it again on the next run.
- What if the day-of-month does not exist for a period, such as a monthly series due on the 31st?
  A date must still be chosen deterministically rather than the period being skipped.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A confirmed recurring series MUST record a transaction for each occurrence that falls
  due, without the operator entering it.
- **FR-002**: Posting MUST run automatically on a schedule, and MUST NOT depend on the operator
  remembering to trigger it.
- **FR-003**: On starting up, the app MUST post every occurrence that fell due while it was not
  running, each exactly once, regardless of how long it was off.
- **FR-004**: An occurrence MUST NOT be posted more than once, however many times posting runs and
  however many runs overlap in time.
- **FR-005**: Only confirmed series MUST be posted. Proposed, dismissed and stopped series MUST post
  nothing.
- **FR-006**: A posted transaction's amount, account, category and description MUST be derived from
  the series' own past occurrences; a series without enough history to derive them MUST be skipped
  rather than posted with invented values.
- **FR-007**: When an incoming transaction matches an auto-posted one for the same series and period,
  within the timing and amount tolerances the app already uses to recognise a series, the auto-posted
  entry MUST be superseded so the operator's balance counts the incoming one only.
- **FR-008**: Supersession MUST NOT delete or modify the auto-posted transaction; the original and
  the entry that cancels it MUST both remain visible.
- **FR-009**: An incoming transaction that does not match within those tolerances MUST be recorded
  normally, and MUST NOT supersede anything.
- **FR-010**: An auto-posted entry that has already been superseded MUST NOT be superseded again.
- **FR-011**: At most one auto-posted entry may be superseded by any one incoming transaction, chosen
  by a rule that gives the same answer every time.
- **FR-012**: Auto-posted transactions MUST be distinguishable from hand-entered and imported ones.
- **FR-013**: An operator MUST be able to see, for an auto-posted transaction, which series produced
  it and which period it covers.
- **FR-014**: An operator MUST be able to stop a confirmed series from posting further transactions.
- **FR-015**: Stopping a series MUST leave its detection history and its already-posted transactions
  intact, and MUST leave it distinguishable from a series that was never confirmed.
- **FR-016**: An occurrence whose auto-posted transaction the operator has since corrected away MUST
  NOT be posted again.
- **FR-017**: Every occurrence MUST resolve to a definite date, including where the series' nominal
  day does not exist in a given period.

### Key Entities

- **Occurrence**: One period of a confirmed series — the unit that is posted exactly once. Identified
  by its series and the period it covers, which is what makes repeated posting runs safe.
- **Auto-posted transaction**: An ordinary transaction that the app wrote rather than the operator,
  carrying enough provenance to answer "why is this row here?" and to be matched against the bank's
  eventual version of the same event.
- **Supersession**: The record that an auto-posted prediction was replaced by an incoming fact —
  itself a transaction, so the ledger nets correctly and nothing is lost.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator with confirmed series stops entering those transactions entirely — 100% of
  due occurrences are recorded without manual entry.
- **SC-002**: Running posting any number of times, in any overlap, produces exactly one transaction
  per due occurrence — verifiable by comparing counts after repeated runs.
- **SC-003**: An instance switched off for an arbitrary period posts exactly the occurrences that
  fell due in that window on its next run — no more, no fewer.
- **SC-004**: After importing a statement covering a period the app already posted, the operator's
  balance for that period matches the bank's figure exactly, with no double count.
- **SC-005**: 100% of auto-posted transactions can be identified as such, and traced to their series
  and period, from the running app.
- **SC-006**: Stopping a series ends its posting within one scheduled run, and loses none of its
  history.

## Assumptions

- The values an auto-post uses come from the series' most recent past occurrence: its amount,
  account, category and description. A series carries none of these itself — only its grouping key,
  description, cadence, direction and status — so its own history is the only honest source. The
  most recent occurrence is preferred over an average because it tracks a rent increase immediately
  rather than lagging it.
- Matching an incoming transaction to an auto-posted one reuses the tolerances the app already
  applies when recognising a series in the first place (a proportional amount tolerance with an
  absolute floor, and a cadence-scaled timing window). Introducing a second, different notion of
  "close enough" would let the app recognise a series it then refuses to reconcile.
- Where an incoming transaction could match more than one auto-posted entry, the closest by date
  wins, and ties break toward the earlier period. The rule matters more than which rule it is: it has
  to be the same answer every time.
- Supersession uses the existing correction path — a compensating entry referencing the original —
  rather than a new mechanism, so auto-posted transactions obey the same immutability rules as every
  other transaction and existing history views already explain them.
- Stopping a series is a distinct state from dismissing one at detection time: dismissed means "this
  was never a real series", stopped means "it was, and has ended". Conflating them would lose the
  distinction between a bad detection and a cancelled subscription.
- Scheduled posting runs daily. The cadence only needs to be finer than the shortest supported
  series interval; daily satisfies that with a wide margin and keeps catch-up cheap.
- Deployment remains a single instance against a single database, consistent with features 021 and
  022. Two instances posting simultaneously is nonetheless covered by FR-004, since the same
  guarantee that makes catch-up safe covers it for free.
- Out of scope, deliberately: learned auto-categorisation from operator corrections, CAMT.053 and
  MT940 statement parsing, multi-currency transactions, bank synchronisation via an aggregator, and
  any change to how series are detected, proposed or confirmed.
