# Data Model: Bill/Income Correction Flow

**Feature**: 008-bill-income-correction
**Date**: 2026-08-23

## Schema Changes

### `bill` table — 1 new nullable column, 1 new NOT NULL column

| Column | Type | Nullable | Description |
|--------|------|----------|--------------|
| `corrects_transaction_id` | VARCHAR | Yes | Id of the bill this row corrects/reverses. `NULL` for an original, never-corrected bill. |
| `reversal` | BOOLEAN | No (`NOT NULL DEFAULT false`) | `true` for a system-generated reversal row (negative amount, never user-created, never shown in the transaction list). |

### `income` table — 1 new nullable column, 1 new NOT NULL column

| Column | Type | Nullable | Description |
|--------|------|----------|--------------|
| `corrects_transaction_id` | VARCHAR | Yes | Same semantics as `bill.corrects_transaction_id`. |
| `reversal` | BOOLEAN | No (`NOT NULL DEFAULT false`) | Same semantics as `bill.reversal`. |

No other tables change.

**Existing rows do need a one-time backfill.** `reversal` maps to a Java primitive `boolean`, so a
`NULL` in that column makes Hibernate fail to hydrate the entity (`Null value was assigned to a
property of primitive type`) — every read of a pre-feature bill/income 500s. `ddl-auto=update` adds
the column to an existing table as nullable with no default and does not backfill it, so any
database that predates this feature must run:

```sql
UPDATE bill   SET reversal = false WHERE reversal IS NULL;
UPDATE income SET reversal = false WHERE reversal IS NULL;
ALTER TABLE bill   ALTER COLUMN reversal SET DEFAULT false;
ALTER TABLE bill   ALTER COLUMN reversal SET NOT NULL;
ALTER TABLE income ALTER COLUMN reversal SET DEFAULT false;
ALTER TABLE income ALTER COLUMN reversal SET NOT NULL;
```

Freshly created databases need nothing: the entity declares
`@Column(nullable = false) @ColumnDefault("false")`, so Hibernate creates the column as
`boolean not null default false`. `corrects_transaction_id` stays nullable and needs no migration —
`NULL` is the correct state for an original, never-corrected transaction.

## Entities

### Bill / Income (existing entities, extended)

| Field | Type | Nullable | Description |
|-------|------|----------|--------------|
| *(existing fields unchanged)* | | | amount, description, time, categoryId/source, accountId, etc. |
| `correctsTransactionId` | String | Yes | Id of the transaction (of the same type) this row corrects or reverses. |
| `reversal` | boolean | No (default `false`) | `true` only for system-generated reversal rows. |

**Validation rules** (new):
- `reversal = true` rows MUST only ever be created by the new correction/removal Domain services —
  never reachable through `AddBillService`/`AddIncomeService` (the existing public create path).
  A reversal's `amount` is the exact negation of the amount it reverses; this is enforced by the
  correction/removal service computing it, not by user input.
- A correction's replacement row MUST pass the same validation as a normal create (`amount > 0`,
  required fields present) — it is a perfectly ordinary bill/income in every respect except that
  `correctsTransactionId` is set.
- `correctsTransactionId`, when set, MUST reference an id that exists (of the same entity type).

**Derived visibility rule** (computed at read time, not stored):

A bill/income row is **visible** in the human-facing list (`GET /bills`, `GET /incomes`, and
therefore the dashboard's Recent Transactions) if and only if:
1. `reversal == false`, **and**
2. no other row of the same type has `correctsTransactionId` equal to this row's id.

This single rule hides: reversal rows (always, via condition 1), corrected-away originals (via
condition 2 — their replacement references them), and removed originals (via condition 2 — their
reversal references them). See `research.md` Decision 2.

## Relationships

```
Bill/Income (original, uncorrected)
    ← correctsTransactionId ── Bill/Income (reversal, amount negated, hidden)      [correction or removal]
    ← correctsTransactionId ── Bill/Income (replacement, positive amount, visible) [correction only]
```

A chain of corrections links multiple generations:

```
original (hidden, superseded)
    ← replacement v2 (hidden, superseded — corrected again)
        ← replacement v3 (visible — current value)
```

Each correction step also produces its own reversal row (not shown above for brevity — every arrow
that supersedes an old value is paired with a same-shaped reversal row dated the same as the row it
reverses).

## State Transitions

A bill/income has exactly two logical states, derived (never stored) from the relationships above:

- **Active** — visible per the rule above; this is the value the app reports everywhere.
- **Superseded** — hidden because something else corrects or reverses it; its data is permanently
  retained and reachable via `GET .../{id}/history` from whatever row currently supersedes it, but
  it no longer contributes to the "current value" shown to the user (it still contributes to every
  aggregation total, correctly netted against its own reversal).

There is no in-place transition — a row is created once, in one state implied by whether something
later references it, and its own stored fields never change (Constitution Principle I).
