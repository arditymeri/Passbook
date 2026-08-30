# Feature Specification: Transaction Import

**Feature Branch**: `017-transaction-import`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: "Transaction import. Let a user import a batch of bills and income from a file (e.g. a CSV export from their bank) instead of entering each one by hand. The user picks a file and the account it belongs to, reviews the parsed transactions before anything is saved (date, description, amount, and a suggested category per row they can correct), and confirms to create them. Transactions that look like duplicates of ones already recorded (same account, date, amount, and description) are flagged and excluded from the import by default so re-uploading the same statement doesn't double-count. Builds on the existing bill/income creation and category assignment the app already has."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import a Statement Instead of Typing Every Transaction (Priority: P1)

A user has a file of transactions (a bank statement export) and wants to get all of them into the
app without manually filling in the add-bill/add-income form dozens of times.

**Why this priority**: This is the entire reason the feature exists — turning a tedious,
error-prone manual process into a single upload-and-confirm action. Nothing else in this feature
matters without this core flow working.

**Independent Test**: Can be fully tested by uploading a file with several valid transactions,
selecting an account, reviewing the parsed preview, confirming, and verifying every reviewed
transaction now appears in that account's history with the right date, description, and amount.

**Acceptance Scenarios**:

1. **Given** a file containing several valid transactions and an account to import them into,
   **When** the user uploads the file, selects the account, and confirms the import, **Then**
   every transaction shown in the review appears in the app's transaction history for that
   account, matching the file's date, description, and amount.
2. **Given** the user has reviewed a parsed import, **When** they cancel instead of confirming,
   **Then** no transaction from the file is created and the account's history is unchanged.
3. **Given** a file where some rows are missing a date or amount, **When** the user reviews the
   parsed import, **Then** those rows are clearly flagged as unable to be imported, while every
   other valid row in the same file can still be reviewed and imported normally.

---

### User Story 2 - Re-Upload the Same Statement Without Double-Counting (Priority: P1)

A user re-uploads a statement they already imported (e.g. downloaded an updated export covering
an overlapping date range) and wants the transactions they already have recorded to be recognized
and skipped automatically, rather than creating duplicates.

**Why this priority**: Equal priority to User Story 1 — an import feature a user can't trust to
avoid double-counting is actively harmful to the very account data the app exists to keep
accurate, undermining every other feature that reads that history.

**Independent Test**: Can be fully tested by importing a file once, then re-uploading the exact
same file, and confirming every row is flagged as a likely duplicate and excluded from the import
by default, resulting in zero new transactions on the second import.

**Acceptance Scenarios**:

1. **Given** a transaction already recorded on an account, **When** the user imports a file
   containing a row with the same account, date, amount, and description, **Then** that row is
   flagged as a likely duplicate and excluded from the import by default.
2. **Given** a row flagged as a likely duplicate, **When** the user explicitly chooses to include
   it anyway, **Then** it is imported like any other reviewed row — the flag is a default, not a
   restriction.
3. **Given** a file with no overlap with existing history, **When** the user imports it, **Then**
   no row is flagged as a duplicate.

---

### User Story 3 - Correct a Row Before It's Saved (Priority: P2)

A user reviewing the parsed preview notices a suggested category is wrong, or wants to leave a
particular row out of the import entirely, and wants to fix that before anything is saved.

**Why this priority**: A useful refinement once the core import (User Stories 1-2) works — most
imported rows will need no correction, but the ability to fix the ones that do is what makes the
review step actually trustworthy rather than a formality.

**Independent Test**: Can be fully tested by changing a row's suggested category before
confirming and verifying the imported transaction has the corrected category, and by excluding a
row before confirming and verifying it never appears in the account's history.

**Acceptance Scenarios**:

1. **Given** a row in the review with a suggested category the user disagrees with, **When** they
   change it before confirming, **Then** the imported transaction uses the corrected category, not
   the original suggestion.
2. **Given** a row the user does not want imported for any reason, **When** they exclude it before
   confirming, **Then** it is never created, regardless of whether it was flagged as a duplicate.

---

### Edge Cases

- A row with a missing or unparseable date or amount: flagged as an error and excluded, without
  blocking the rest of the file's valid rows (User Story 1, acceptance scenario 3).
- A file with no valid rows at all: the user sees a clear message that nothing can be imported,
  not an empty success or a silent failure.
- A row whose description doesn't match any existing category pattern: shown with no suggested
  category (same as any other transaction with no category), rather than a guess.
- Two genuinely identical transactions in the same statement (e.g. two identical coffee purchases
  on the same day): both are flagged as duplicates of each other/of history where applicable, but
  the user can include either or both via User Story 2's override.
- A very large file: the review step still completes and lets the user confirm within a
  reasonable time (see Success Criteria).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to upload a file containing multiple candidate transactions to
  import.
- **FR-002**: Users MUST select which account the imported transactions belong to before or as
  part of reviewing them.
- **FR-003**: System MUST parse the uploaded file into individual candidate transactions (date,
  description, amount) and MUST NOT create any transaction record until the user explicitly
  confirms the import.
- **FR-004**: System MUST determine whether each candidate is a bill (expense) or income and
  present it labeled accordingly in the review.
- **FR-005**: System MUST suggest a category for each candidate transaction where a reasonable
  match exists, and MUST let the user change or clear that suggestion before confirming.
- **FR-006**: System MUST detect when a candidate transaction matches an existing transaction on
  the selected account by date, amount, and description, flag it as a likely duplicate, and
  exclude it from the import by default.
- **FR-007**: Users MUST be able to override a flagged duplicate and include it in the import
  anyway.
- **FR-008**: Users MUST be able to exclude any individual candidate from the import before
  confirming, independent of its duplicate status.
- **FR-009**: A candidate that cannot be parsed (missing or invalid date or amount) MUST be
  flagged as an error and excluded from the import, without preventing the rest of the file's
  valid candidates from being reviewed and imported.
- **FR-010**: Before confirming, users MUST be able to see how many transactions will actually be
  created versus how many are excluded, and why (duplicate, parse error, or manually excluded).
- **FR-011**: Confirming the import MUST create exactly the reviewed, non-excluded candidates as
  normal bill/income records, indistinguishable afterward from transactions entered by hand.
- **FR-012**: Canceling an import before confirming MUST leave the account's transaction history
  completely unchanged.

### Key Entities

- **Import Candidate**: A single transaction parsed from the uploaded file, pending user review —
  date, description, amount, bill/income direction, suggested category, and its inclusion status
  (included, excluded, flagged duplicate, or parse error). Exists only transiently during review;
  it either becomes a normal bill/income record on confirm or is discarded on cancel.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can import a statement of several dozen transactions and have them all
  recorded in well under the time it would take to enter each one individually by hand.
- **SC-002**: Re-uploading a previously imported statement results in zero duplicate transactions
  being created, without the user having to manually cross-check the two files themselves.
- **SC-003**: Before confirming, a user can always tell exactly how many transactions will be
  created and the reason for every exclusion.
- **SC-004**: A file containing some invalid rows still results in every valid row being
  successfully imported, with the invalid ones clearly called out rather than blocking the batch.

## Assumptions

- The supported file format is CSV — the standard, ubiquitous bank-statement export format;
  other file formats are out of scope for this feature.
- The uploaded file is expected in a standard, single-currency CSV shape (a date column, a
  description column, and an amount column, with the amount's sign or an equivalent convention
  indicating bill vs income) rather than the system supporting every bank's unique proprietary
  export layout — a user whose bank's raw export doesn't match this shape may need to adjust it
  (e.g. reorder or rename columns) before uploading, consistent with how many personal-finance
  tools handle import today.
- Category suggestion reuses the same description-matching approach the app already uses to
  recognize recurring transactions, rather than introducing a new classification technique.
- Duplicate detection is an exact match on account, date, amount, and description — not fuzzy or
  approximate matching — keeping the behavior predictable and easy for a user to understand and
  override when needed.
- Import is a single all-at-once batch: the whole file is parsed and reviewed together, and one
  confirm action creates every non-excluded candidate; there is no partial/streaming import.
