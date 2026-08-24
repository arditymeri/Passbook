# Feature Specification: Bill/Income Correction Flow

**Feature Branch**: `008-bill-income-correction`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "Bill/Income correction flow — let users correct a mistake in a previously recorded bill or income (wrong amount, wrong category, wrong date, etc.) without silently editing or deleting the original record. Per Constitution Principle I (Transaction Immutability), corrections must be made via a compensating/reversal entry that references the original transaction, with the original remaining untouched in the system. The corrected view should show the net effect (original nets to zero, replaced by the corrected entry) everywhere transactions currently appear (recent transactions list, monthly summary, category spend, budget status, account balances)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Correct a Mistake in a Bill or Income (Priority: P1)

A user notices that a bill or income they recorded has a mistake — the wrong amount, the wrong category, the wrong date, or a typo in the description. They open a correction form pre-filled with the transaction's current values, fix the wrong field(s), and submit. The original record is preserved exactly as it was; the system automatically records a reversal of the original alongside a new entry with the corrected values, so every total in the app reflects the fix.

**Why this priority**: This is the core ask — today there is no way to fix a data-entry mistake without going around the app entirely (e.g. via Swagger), and Constitution Principle I forbids simply editing the original record. This is the single highest-value gap in daily usability.

**Independent Test**: Record a bill with a wrong amount, correct it via the new flow, and verify: the original bill's raw data is still present and unchanged in the system, the dashboard now shows the corrected amount everywhere it previously showed the wrong one, and the two numbers (original wrong amount, correction) net out to the right total.

**Acceptance Scenarios**:

1. **Given** a bill or income exists, **When** the user opens its correction form, **Then** the form is pre-filled with that transaction's current amount, date, description, category (or source), and account.
2. **Given** the correction form is open, **When** the user changes one or more fields and submits, **Then** the original transaction's stored data is unchanged, and the dashboard reflects only the corrected values going forward.
3. **Given** a correction has been submitted, **When** the user views the monthly summary, category spend, budget status, and the linked account's balance, **Then** all of them reflect the corrected amount, not the original mistaken one.
4. **Given** the correction form is open, **When** the user submits with an invalid value (e.g., amount of zero or less), **Then** the same inline validation used for creating a new bill/income applies and the correction is not submitted.
5. **Given** a transaction was corrected once already, **When** the user corrects it again, **Then** the correction applies to its most recent (already-corrected) values, not the original mistaken ones.

---

### User Story 2 - Remove a Bill or Income Recorded by Mistake (Priority: P2)

A user realizes they recorded a bill or income that should never have existed at all (e.g., a duplicate entry, or an expense that didn't actually happen). They remove it. As with a correction, the original record is never deleted or edited — the system records a reversal that cancels out its effect everywhere, with no replacement entry.

**Why this priority**: This is the natural counterpart to correcting a wrong value — Constitution Principle I explicitly calls out that even a "delete" must preserve the original row and post a reversal. It shares the same underlying mechanism as User Story 1, so it is a smaller increment once correction exists, but it is not the primary ask, hence P2.

**Independent Test**: Record a bill, remove it via the new flow, and verify: the original bill's raw data is still present in the system, but no dashboard total (summary, category spend, budget status, account balance) reflects it any longer.

**Acceptance Scenarios**:

1. **Given** a bill or income exists, **When** the user chooses to remove it, **Then** the system asks for confirmation before proceeding (removal has no corrected replacement, unlike a correction).
2. **Given** the user confirms removal, **When** the removal completes, **Then** the original transaction's stored data is unchanged, but it no longer contributes to any total.
3. **Given** a transaction was already corrected one or more times, **When** the user removes it, **Then** the removal cancels out its current (most recently corrected) value.

---

### User Story 3 - See That a Transaction Was Corrected (Priority: P3)

A user looking at a bill or income that was previously fixed can see that it was corrected and what its value used to be, so they trust the number they're looking at and understand why it might differ from what they remember entering.

**Why this priority**: This is a transparency/trust nice-to-have that supports the audit trail principle, but the app is fully usable without it — the corrected totals are already right without this view.

**Independent Test**: Correct a transaction, then open its detail/history view and verify the prior value and the fact that it was corrected are both visible.

**Acceptance Scenarios**:

1. **Given** a transaction has been corrected at least once, **When** the user views it, **Then** they can see it was corrected and see its previous value(s).
2. **Given** a transaction has never been corrected, **When** the user views it, **Then** no correction history is shown.

---

### Edge Cases

- Correcting a transaction that was already corrected must act on its latest version, not the original mistaken one (chained corrections).
- If the corrected date moves a transaction into a different month, the original month's totals must drop back to what they'd be without it, and the new month's totals must pick it up — both are a direct consequence of the reversal being dated the same as the original and the new entry being dated per its own (possibly new) date.
- Correcting or removing a transaction linked to an account must update that account's balance to reflect only the corrected/removed value.
- Attempting to correct or remove a transaction that no longer exists (e.g., already removed) must show a clear error rather than silently failing.
- Submitting a correction with the exact same values as before (no actual change) is allowed and has no effect beyond creating a no-op reversal/replacement pair — this is not blocked, since detecting "no real change" adds complexity for no user benefit.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to initiate a correction on any bill or income currently visible in the dashboard's Recent Transactions list (the most recent entries of the selected month). Correcting a transaction that has scrolled out of that list is out of scope for this feature (still possible via the existing API, as it is today).
- **FR-002**: The correction form MUST be pre-filled with the transaction's current amount, date, description, category (bills) or source (incomes), and linked account.
- **FR-003**: Submitting a correction MUST NOT modify or delete the original transaction's stored data.
- **FR-004**: Submitting a correction MUST produce a reversal of the original transaction's effect plus a new entry carrying the corrected values.
- **FR-005**: The reversal and the new corrected entry MUST both reference the transaction being corrected, so the correction is traceable back to it.
- **FR-006**: After a correction, the monthly summary, category spend, budget status, recent transactions list, and any linked account's balance MUST reflect only the corrected values — the original and its reversal MUST net to zero everywhere totals are computed.
- **FR-007**: Users MUST be able to remove a bill or income entirely (no corrected replacement) via the same non-destructive reversal mechanism as a correction.
- **FR-008**: Removing a transaction MUST require an explicit confirmation step before it takes effect.
- **FR-009**: Corrections and removals MUST enforce the same field validation rules as creating a new bill or income (e.g., amount must be greater than zero).
- **FR-010**: Users MUST be able to correct or remove a transaction that has already been corrected one or more times; the action MUST apply to its most recent values.
- **FR-011**: The transaction list MUST show a corrected transaction as a single row reflecting only its current (most recently corrected) value — the reversal and replacement entries created by a correction MUST NOT appear as separate rows cluttering the main list.
- **FR-012**: Users MUST be able to view the correction history (prior value(s)) of a transaction that has been corrected, via a detail view separate from the main transaction list.
- **FR-013**: Removing a transaction entirely (User Story 2) IS in scope for this feature, using the same reversal mechanism as a correction but with no replacement entry.

### Key Entities

- **Bill / Income**: Existing transaction entities. Gain a reference to the transaction they correct or remove (if any), and a way to distinguish a normal entry from a reversal entry, so aggregation can net them to zero without showing a reversal as if it were an ordinary user-facing transaction.
- **Correction**: The conceptual link between an original transaction, its reversal, and (for a correction, not a removal) the new replacement transaction that carries the fixed values.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can correct a mistaken bill or income (open form → fix field → submit) in under 60 seconds.
- **SC-002**: After a correction or removal, every dependent view (recent transactions, monthly summary, category spend, budget status, linked account balance) reflects the change within 1 second, with no manual page refresh.
- **SC-003**: 100% of corrected or removed transactions retain their original, unmodified data — verifiable by inspecting the underlying record after any correction or removal.
- **SC-004**: For every correction or removal, the net financial effect of the original transaction plus its reversal is exactly zero.
- **SC-005**: 0% of removals take effect without the user explicitly confirming the removal step.

## Assumptions

- Correction and removal are only offered for transactions currently visible in the dashboard's Recent Transactions list; no new transaction browser/search UI is introduced by this feature.
- A correction only adjusts fields within the same transaction type — a bill stays a bill and an income stays an income; converting a bill into an income (or vice versa) because the user picked the wrong type entirely is out of scope and would require removing the wrong entry and creating a new one of the correct type manually.
- There is no time limit on how old a transaction can be before it's eligible for correction or removal (this is a single-user personal finance app with no compliance-driven correction window).
- The correction and removal forms reuse the same field set, labels, and validation as the existing Add Bill / Add Income forms — they are not a new design language.
- No user authentication/authorization distinctions apply — any user of the app may correct or remove any transaction, consistent with the app's existing single-user model.
- Removing a transaction does not release the account or category it referenced: because the original row is retained permanently (Principle I), that account/category must remain deletable-blocked so the retained record never points at a deleted entity (Principle V). A user who removes the only bill using an account will still be unable to delete that account — this is intentional, not a defect.
