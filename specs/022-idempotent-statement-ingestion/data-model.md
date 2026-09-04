# Phase 1 Data Model: Idempotent Statement Ingestion

**Feature**: 022-idempotent-statement-ingestion | **Date**: 2026-09-04

One persisted field, one index per table, and three transient Domain types. The spec's three Key
Entities map below.

---

## 1. External transaction identity → `bill.external_id`, `income.external_id`

The only schema change. Added by `V2__add_external_transaction_identity.sql`.

| Property | Value |
|---|---|
| Type | `varchar(255)`, nullable |
| Present when | The transaction arrived through ingestion |
| Absent (NULL) when | Typed by hand, **or** recorded before this feature existed |
| Uniqueness | Partial unique index on `(account_id, external_id) WHERE external_id IS NOT NULL` |
| Mutability | Write-once. Never updated after insert. |

**Two forms of value:**

```
<source-supplied transaction id>          when the statement carries one (preferred, FR-001)
<hash>:<occurrence>                       derived, when it does not (FR-002, FR-003)
```

The derived form hashes, in this order: **account id, calendar date, amount, description,
direction**. Direction (bill vs income) is in the hash because both tables store positive amounts —
without it a €50 refund collides with the €50 charge it reverses (research R1). The calendar date is
used rather than a timestamp, because any time-of-day is invented by the parser rather than stated
by the statement.

`<occurrence>` is how many earlier rows **in the same file** shared that exact five-tuple, counting
from zero. Assigned over the whole file **before** exclusions (research R8).

**Invariants a contributor must not break:**

- **Never backfill it.** Rows predating this feature keep NULL. Inventing identities for history that
  was never ingested asserts a provenance that does not exist (FR-008).
- **Never update it.** A transaction's identity is what makes it recognisable across imports;
  changing it makes the row permanently invisible to deduplication.
- **Never let a client supply it.** The server derives it on every request that writes (FR-010,
  research R7).

---

## 2. Statement → transient, never persisted

A file the operator uploads. Nothing about it is stored — not the filename, not a hash of it, not an
import history. This is deliberate: idempotency is a property of the *transactions*, not of a record
of which files have been seen. An operator who renames a file, or downloads the same period twice
from their bank, gets the same outcome either way.

**Parsed shape** (Domain type, transient):

| Field | Notes |
|---|---|
| `rowIndex` | Position in the file, 0-based. Stable across re-parses; how confirm names exclusions. |
| `date` | Calendar date from the statement |
| `description` | Merchant / reference text, may be empty |
| `amount` | `BigDecimal`, always positive — Principle IV |
| `direction` | `BILL` or `INCOME`, derived from the row's sign or debit/credit column |
| `sourceTransactionId` | Present only when the statement supplies one |
| `externalId` | Derived during parsing; see §1 |

An empty description is legal and must still yield a stable identity — it simply means every
same-day, same-amount, empty-description row falls in one identity group and is separated by
occurrence index, which is the correct behaviour.

---

## 3. Import result → transient, returned to the caller

Per-row outcome (FR-011) plus totals. Not persisted.

| Row status | Meaning |
|---|---|
| `RECORDED` | Newly written by this request |
| `ALREADY_RECORDED` | Its identity was already present; nothing was written |
| `REJECTED` | Unusable — carries a human-readable reason |
| `EXCLUDED` | The operator chose not to import it (confirm only) |

**Where each status comes from matters.** `RECORDED` and `ALREADY_RECORDED` are determined by
*the write itself* — the `RETURNING` clause names exactly the rows that landed, and everything
submitted-and-not-returned was already present (research R3). They are never derived from a
lookup, because a lookup can disagree with the write under concurrency.

On **preview**, the same two statuses come from a read and are explicitly **advisory** — accurate at
the moment of reading, and not what enforces anything.

**Rejection reasons** (FR-011 requires a reason, not just a flag): unparseable date, missing or
unparseable amount, amount not positive, row does not have the expected number of columns. A
rejected row must not block the rows around it — the rest of the statement still imports.

---

## 4. What is deliberately *not* modelled

| Not added | Why |
|---|---|
| An `import` / `statement_run` table | Idempotency is a property of transactions. An import-history table would be a second source of truth about what has been seen, able to disagree with the transactions themselves. |
| A `source` or `origin` column | `external_id IS NOT NULL` already distinguishes ingested from hand-entered. A separate column would be a second encoding of the same fact. |
| A tombstone for excluded rows | FR-014 requires an excluded row to be offered again as new later. Recording the exclusion would prevent exactly that. |
| Backfilled identities for existing rows | FR-008. See §1. |

---

## 5. Principle checks against this model

- **I (Immutability)**: ingestion only ever inserts. No path in this feature updates or deletes a
  transaction; a restated statement line arrives as a new transaction and the operator corrects the
  old one through the existing reversal path.
- **II (Idempotent ingestion)**: this model *is* the implementation of Principle II — the field that
  never existed, plus the constraint that makes re-ingestion a no-op.
- **IV (Currency precision)**: `amount` stays `BigDecimal` end to end, and the new column is
  `varchar`. Nothing in this feature introduces a floating-point money value.
- **V (Audit trail)**: `recorded_at` continues to be stamped at write time by the existing adapter,
  so ingested rows carry a true write-time timestamp exactly as manually entered ones do.
