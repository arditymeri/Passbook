# Phase 1 Data Model: Auto-Post Confirmed Recurring Series

**Feature**: 023-auto-post-recurring-series | **Date**: 2026-09-04

One persisted column, one new series state, and three transient Domain concepts. The spec's three Key
Entities map below.

---

## 1. Occurrence → transient, identified by `recurring:<seriesId>:<date>`

The unit that is posted exactly once. **Not a table.** There is no `occurrence` or `posting_run`
row anywhere, and that is the design rather than an omission (research R1): an occurrence's existence
is recorded by the transaction it produced, so there is no second source of truth that can disagree
with the ledger.

| Property | Value |
|---|---|
| Identity | `recurring:<seriesId>:<ISO occurrence date>` stored in `external_id` |
| Uniqueness | Feature 022's partial unique index on `(account_id, external_id)` |
| Posted when | Its date is after the latest *real* occurrence, on/after the series' confirmation, and not in the future |

**Why the identity looks like that**: it is deterministic from things that cannot drift — the series
and the calendar date of the period. Re-deriving it on a later run produces the same string, which is
what makes catch-up idempotent rather than merely careful.

**The date is the period.** An auto-posted transaction is dated at its own occurrence, so the period
is recoverable from the row without storing it twice.

---

## 2. Auto-posted transaction → `bill.recurring_series_id`, `income.recurring_series_id`

The only schema change. Added by `V3__add_recurring_series_origin.sql`.

| Property | Value |
|---|---|
| Type | `varchar(255)`, nullable |
| Set when | The transaction was written by this feature |
| Null when | Entered by hand, or imported from a statement |
| Indexed | Yes — reconciliation looks up a series' auto-posted rows on every import |

**Three origins, two nullable columns, no third encoding** (research R6):

| Origin | `external_id` | `recurring_series_id` |
|---|---|---|
| Hand-entered | null | null |
| Imported | set | null |
| Auto-posted | set (`recurring:…`) | set |

An `origin` enum was rejected for the same reason feature 022 rejected one: it would be derivable
from the facts above, and a second encoding of the same truth is a second thing that can be wrong.

**Values a posted transaction carries** (FR-006):

| Field | Where it comes from |
|---|---|
| `amount` | Latest **real** occurrence of the series |
| `accountId` | Latest **real** occurrence of the series |
| `categoryId` | The series' group key — for a bill series that *is* the category (research R3) |
| `description` | The series' description |
| `time` | The occurrence date, at start of day UTC |
| Direction | The series' `transactionType` |

**"Real" excludes this feature's own output.** An occurrence that is itself auto-posted, or is a
reversal, is not evidence (research R2). Without that exclusion the app derives next month's figures
from last month's guess and never learns that the rent went up.

---

## 3. Supersession → an ordinary reversal, via the existing correction path

Not a new mechanism and not a new table. When an imported transaction matches an auto-posted one, the
prediction is corrected away exactly as a manual correction would do it: a compensating entry
referencing the original, with the original left untouched.

| Consequence | Why it matters |
|---|---|
| The ledger nets to the imported amount | FR-007 — the operator's balance counts the bank's figure once |
| The prediction and its reversal both remain | FR-008 — nothing is deleted, Principle I holds |
| Existing history views already explain it | Feature 008 built the vocabulary; this reuses it |
| The reversed row keeps its `recurring_series_id` | The period stays "handled", so FR-016 falls out — a corrected-away occurrence is not re-posted |

**Eligibility** (FR-010): an auto-posted transaction that already has a reversal referencing it is not
a candidate for supersession again.

---

## 4. Series state: `CONFIRMED` → `STOPPED`

`RecurringSeriesStatus` gains a fourth value alongside `PROPOSED`, `CONFIRMED`, `DISMISSED`.

| State | Meaning | Posts? |
|---|---|---|
| `PROPOSED` | Detected, awaiting the operator | No |
| `CONFIRMED` | The operator expects this to continue | **Yes** |
| `DISMISSED` | "This was never a real series" | No |
| `STOPPED` | "It was real, and it has ended" | No |

**Why `STOPPED` is not just `DISMISSED`** (FR-015): dismissing is a judgement about the *detection*;
stopping is a fact about the *world*. Collapsing them would lose the difference between a bad guess
and a cancelled gym membership, and would make a stopped series look like something the detector got
wrong.

Transitions this feature adds: `CONFIRMED → STOPPED`. Everything already posted stays exactly as it
is; the series keeps its history and stays listed.

---

## 5. What is deliberately *not* modelled

| Not added | Why |
|---|---|
| An `occurrence` / `posting_run` table | The transaction *is* the record that a period was posted. A second store could disagree with the ledger, and would have to survive a restore from backup to be trusted. |
| A `lastPostedThrough` timestamp on the series | State that can be written-but-not-committed, or reset by a restore — each silently skipping or repeating an operator's rent (research R1). |
| An `origin` enum column | Derivable; see §2. |
| A stored occurrence period | Recoverable from the transaction's own date; see §1. |
| A `confirmedAt` column | The existing `updatedAt` is used, erring toward posting less (research R4). A dedicated column can be added if the approximation ever proves too loose. |

---

## 6. Principle checks

- **I (Immutability)**: supersession writes a compensating entry and never mutates or deletes the
  prediction. Auto-posting only ever inserts.
- **II (Idempotent ingestion)**: auto-posted transactions arrive from outside the UI and carry a
  stable external identity, exactly as the principle requires. This feature is the second producer to
  honour it, using the machinery 022 built.
- **III (Balance derivation)**: balances stay computed from history. A superseded prediction and its
  reversal net to zero, so the balance reflects the bank's figure without anything being stored.
- **IV (Currency precision)**: amounts are copied `BigDecimal`-to-`BigDecimal` from a past
  occurrence. No arithmetic is performed on them at all, so there is nothing to round.
- **V (Audit trail)**: `recurring_series_id` is the provenance the principle demands — it is what
  makes "why is this row here?" answerable for a row nobody typed.
