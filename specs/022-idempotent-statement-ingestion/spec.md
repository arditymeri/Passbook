# Feature Specification: Idempotent Statement Ingestion

**Feature Branch**: `022-idempotent-statement-ingestion`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "Move statement import to the backend and give Passbook the idempotent ingestion the constitution requires. Principle II ('Ingestion Is Idempotent', NON-NEGOTIABLE) says any transaction arriving from outside the UI MUST carry a stable external identity and that re-ingesting an already-seen identity MUST be a no-op. Today nothing implements it: no bill or income row has any external-identity field, the only import is feature 017's browser-side CSV dialog whose duplicate detection is a heuristic comparison against whatever transactions the frontend happens to have loaded, and the Kafka consumer the README calls the seam for transaction ingestion is a stub that logs the message and returns. Add external identity to recorded transactions; a server-side ingestion endpoint that parses an uploaded statement, derives identity per row, and reports per row whether it was ingested or skipped as already-seen, enforced by a database constraint rather than an application-level lookup; the first migration on top of feature 021's Flyway baseline; and retire feature 017's client-side parsing and duplicate detection so exactly one place decides what a transaction's identity is. Out of scope: CAMT.053 and MT940 parsing, learned auto-categorisation, wiring the Kafka consumer, and bank synchronisation."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Re-import an Overlapping Statement Without Double-Counting (Priority: P1)

An operator downloads a statement from their bank covering the last 30 days and imports it. Two weeks
later they download another, which overlaps the first by two weeks, and import that. Every
transaction they already had stays exactly as it was; only the genuinely new ones are added. Nothing
is double-counted, and they were not asked to figure out which rows overlapped.

**Why this priority**: This is the whole feature, and it is the invariant the constitution calls
load-bearing. A pipeline that fills itself is worthless if it also duplicates — the operator would be
back to manual reconciliation, which is the problem being solved. Overlapping date ranges are the
normal case, not an error.

**Independent Test**: Import a statement, note every balance and transaction count, import the same
file again, and confirm nothing changed. Then import a partially overlapping file and confirm only
the new rows appear.

**Acceptance Scenarios**:

1. **Given** a statement that has already been imported in full, **When** the operator imports the
   identical file again, **Then** no new transaction is recorded, every existing transaction is
   unchanged, and the result reports every row as already-seen.
2. **Given** a statement overlapping a previous import by some rows, **When** the operator imports
   it, **Then** exactly the rows not previously seen are recorded, and the result distinguishes the
   newly recorded rows from the skipped ones.
3. **Given** two imports of overlapping statements happening at the same moment, **When** both
   complete, **Then** each shared transaction exists exactly once — the outcome does not depend on
   which finished first.
4. **Given** a statement whose source provides its own transaction identifiers, **When** it is
   imported twice, **Then** identity is taken from the source rather than inferred, and the second
   import records nothing.

---

### User Story 2 - Two Identical Purchases on the Same Day Both Survive (Priority: P1)

An operator bought the same coffee twice on the same day. Both purchases appear on the statement as
identical rows. After import, both are in their history — and re-importing that statement still adds
nothing.

**Why this priority**: Equally blocking, and the reason this cannot be a naive "have I seen this
before" check. Collapsing genuinely repeated transactions is silent financial data loss on completely
ordinary input, and it is the failure mode most likely to go unnoticed — the operator sees a
plausible-looking history that is quietly wrong.

**Independent Test**: Import a statement containing two byte-identical rows, confirm two
transactions exist, re-import the same file, and confirm there are still exactly two.

**Acceptance Scenarios**:

1. **Given** a statement containing two identical rows, **When** it is imported, **Then** two
   separate transactions are recorded.
2. **Given** that statement has already been imported, **When** it is imported again, **Then** the
   count remains two and both rows report as already-seen.
3. **Given** a first statement containing one such row and a second containing two, **When** both are
   imported in order, **Then** the operator ends with two transactions — the previously seen one and
   exactly one new one.

---

### User Story 3 - Upgrade With Existing History Intact (Priority: P1)

An operator with months of manually entered transactions upgrades to this version. Their existing
history is untouched and still works everywhere in the app. Transactions they continue to enter by
hand behave exactly as before.

**Why this priority**: The same obligation feature 021 established — an upgrade must never damage
data that already exists. Manual entry remains a first-class path, not a second-class one, and
existing rows predate any notion of external identity, so they must remain valid rather than being
retrofitted with invented identities.

**Independent Test**: On an instance with existing manually entered transactions, upgrade, and
confirm every transaction is still present, still appears in analysis and balances, and that adding a
new one by hand still works.

**Acceptance Scenarios**:

1. **Given** an instance with existing transactions recorded before this version, **When** the
   operator upgrades, **Then** every transaction is preserved and behaves as before.
2. **Given** an upgraded instance, **When** the operator records a transaction by hand, **Then** it is
   recorded without any external identity and is never treated as a duplicate of anything.
3. **Given** an upgraded instance with pre-existing transactions, **When** the operator imports a
   statement covering the same period, **Then** rows matching those hand-entered transactions are
   recorded as new — the system does not silently claim to recognise history it never ingested.

---

### User Story 4 - See What an Import Will Do Before Committing (Priority: P2)

Before anything is saved, the operator sees what the file contains: which rows are new, which are
already recorded, and what each will look like. They can correct a category or exclude a row, then
confirm.

**Why this priority**: Preserves the review step operators already have from the earlier import
dialog, and keeps trust in an automated pipeline: the first few imports are when someone decides
whether to believe it. But the safety guarantee of Stories 1–3 does not depend on anyone looking,
which is why this follows rather than blocks.

**Independent Test**: Choose a file containing a mix of new and already-recorded rows, and confirm
the preview marks each correctly before anything is saved.

**Acceptance Scenarios**:

1. **Given** a chosen statement file, **When** the operator reviews it, **Then** each row is shown
   with its date, description and amount, and marked as either new or already recorded — and nothing
   has been saved yet.
2. **Given** a preview is shown, **When** the operator cancels, **Then** no transaction is recorded.
3. **Given** a preview is shown, **When** the operator excludes a row and confirms, **Then** the
   excluded row is not recorded, and re-importing the same file later offers it again as new.
4. **Given** a file the system cannot read, **When** the operator chooses it, **Then** they are told
   what is wrong with it, and nothing is recorded.

---

### Edge Cases

- What if the same statement is imported twice at the same instant, from two browser tabs? Each
  transaction must still exist exactly once. Deciding "have I seen this?" by looking before writing
  is not sufficient — both would look, both would see nothing, and both would write.
- What if a bank reissues a corrected statement where a previously seen row now has a different
  amount? The amount is part of what identifies a row without a source identifier, so it arrives as a
  new transaction. Correcting the old one remains the operator's decision, through the existing
  correction path — ingestion never edits an existing row.
- What if a row is missing a date, an amount, or is otherwise unusable? It must be reported as
  rejected with a reason, and must not silently vanish or block the rows around it.
- What if the file is enormous, or is not a statement at all? The operator gets a clear failure rather
  than a partial, half-ingested history.
- What if an operator imports the same file into two different accounts? Those are different
  transactions — identity is scoped to the account, so both are recorded.
- What if a row's description is empty? Identity must still be derivable and stable, rather than
  colliding with every other empty-description row on that day for that amount.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every transaction arriving from outside the manual-entry UI MUST carry a stable external
  identity, taken from the source when the source provides one.
- **FR-002**: When the source provides no identifier, identity MUST be derived deterministically from
  the transaction's own content — the account it belongs to, its date, its amount and its description
  — so the same row always yields the same identity.
- **FR-003**: Derived identity MUST additionally distinguish otherwise-identical rows within the same
  statement, so that repeated genuine transactions are each recorded while a re-import of the same
  statement remains a no-op.
- **FR-004**: Re-ingesting an already-seen identity MUST record nothing and MUST NOT modify the
  existing transaction.
- **FR-005**: Uniqueness MUST be enforced by the store itself, not by checking before writing, so that
  simultaneous imports of overlapping statements cannot both record the same transaction.
- **FR-006**: Identity MUST be scoped per account: the same row imported into two different accounts
  is two transactions.
- **FR-007**: Transactions recorded by hand through the UI MUST have no external identity, MUST remain
  fully valid, and MUST never be treated as duplicates.
- **FR-008**: Transactions recorded before this version MUST remain valid and unchanged, without being
  retrospectively assigned an identity.
- **FR-009**: The schema change MUST be an explicit, ordered migration consistent with how every
  subsequent schema change is applied, and MUST NOT drop, recreate or empty any table.
- **FR-010**: Parsing a statement and deciding identity MUST happen in exactly one place, server-side.
  No client may decide what a transaction's identity is.
- **FR-011**: An import MUST report, per row, whether it was recorded, skipped as already-seen, or
  rejected as unusable — and rejected rows MUST carry a reason.
- **FR-012**: Ingestion MUST only ever create transactions. It MUST NOT modify or delete an existing
  one; corrections remain the existing compensating-entry path.
- **FR-013**: The operator MUST be able to review what an import will do — per row, marked new or
  already recorded — and confirm or cancel, before anything is recorded.
- **FR-014**: A row excluded by the operator MUST NOT be recorded, and MUST be offered again as new on
  a later import of the same statement.
- **FR-015**: A file that cannot be read MUST fail with an explanation and MUST record nothing —
  never a partial import.
- **FR-016**: The ingestion capability MUST be usable by a caller other than the file-upload path
  without redesign, so a future automated source can reuse it.

### Key Entities

- **External transaction identity**: The stable identifier that makes a transaction recognisable
  across imports — supplied by the source, or derived from the transaction's own content plus which
  occurrence it is among identical rows. Absent for manually entered transactions. Unique per account.
- **Statement**: A file of transaction rows an operator obtained from their bank, covering a date
  range that routinely overlaps a previous one.
- **Import result**: The per-row outcome of an ingestion — recorded, already seen, or rejected with a
  reason — and the totals an operator reads to know what just happened.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Importing the same statement any number of times produces exactly the same set of
  transactions as importing it once — verifiable by comparing transaction counts and balances before
  and after a repeat import.
- **SC-002**: Importing a series of overlapping statements covering a period yields exactly one
  transaction per real transaction in that period, with zero duplicates and zero omissions.
- **SC-003**: A statement containing N identical rows records N transactions, and re-importing it
  records none.
- **SC-004**: Two simultaneous imports of overlapping statements result in each shared transaction
  existing exactly once, in 100% of attempts.
- **SC-005**: 100% of transactions that existed before the upgrade are still present and unchanged
  afterwards.
- **SC-006**: The operator can tell, before confirming, how many rows an import will add and how many
  it will skip — and those numbers match what actually happens on confirmation.
- **SC-007**: An operator importing a month of bank activity spends no time identifying which rows
  they already have.

## Assumptions

- CSV is the only statement format in this feature. CAMT.053 and MT940 are the natural next step and
  are deliberately deferred: the identity design is fully testable without them, and they attach to
  the same server-side seam later without a rewrite.
- Identity is derived from account, date, amount and description because those are the fields
  universally present in consumer bank exports. A source-provided transaction identifier always wins
  when present.
- "Which occurrence" is determined within a single statement's identical rows, in the order they
  appear. Consumer statements list transactions in a stable order, so the same file yields the same
  assignment every time.
- Feature 017's client-side parsing, duplicate detection and category suggestion are retired rather
  than kept alongside the server-side path. Two implementations of "is this a duplicate" that can
  disagree is worse than either alone, and only the server's answer can be authoritative.
- The category suggestion the earlier dialog offered is preserved as behaviour, moved server-side. It
  remains the same simple reuse-a-previous-category rule; learning from corrections is a separate
  feature.
- Existing transactions keep no identity, and the uniqueness rule tolerates that. Inventing identities
  for history that was never ingested would claim a provenance that does not exist.
- Deployment remains a single instance against a single database, consistent with feature 021.
- Out of scope, deliberately: CAMT.053/MT940 parsing, learned auto-categorisation from operator
  corrections, connecting the existing Kafka consumer to this ingestion path (nothing publishes to
  that topic yet, so wiring it now would be speculative generality — but the capability must be
  shaped so that consumer can call it later), automatic posting of confirmed recurring series, and
  bank synchronisation via an aggregator.
