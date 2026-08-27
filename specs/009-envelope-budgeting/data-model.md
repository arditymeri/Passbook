# Phase 1 Data Model: Envelope Budgeting

## Entities

### Allocation (existing `Budget` entity/table — reused, unchanged schema)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | unchanged |
| `categoryId` | UUID (FK → category) | unchanged; MUST resolve to a category of type `EXPENSE` or `BOTH` (new validation, FR-009) |
| `year` | int | unchanged |
| `month` | int (1–12) | unchanged |
| `limitAmount` | BigDecimal | unchanged column; means "amount assigned to this category in this month" in this feature's vocabulary. MUST be > 0 (unchanged invariant from 002) |

One row per (categoryId, year, month) — upsert semantics (FR-003), exactly as feature 002 already
behaves.

### AllocationTransfer (new entity/table: `allocation_transfer`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | generated |
| `fromCategoryId` | UUID (FK → category) | source envelope |
| `toCategoryId` | UUID (FK → category) | destination envelope; MUST differ from `fromCategoryId` |
| `year` | int | the month the transfer is recorded in |
| `month` | int (1–12) | |
| `amount` | BigDecimal | MUST be > 0 |
| `createdAt` | OffsetDateTime (UTC) | set at write time; never updated |

Append-only — never updated or deleted after creation (FR-005's "move" is always a new row; there
is no "undo," matching how every other financial record in this system already behaves).

### Derived values (not stored)

**Unallocated Balance** — a running figure, not scoped to a single month:

```
unallocated(asOfYear, asOfMonth) =
    Σ income.amount, for every income with time ≤ end of (asOfYear, asOfMonth)
  − Σ allocation.limitAmount, for every allocation with (year, month) ≤ (asOfYear, asOfMonth)
```

Transfers do not appear in this formula — moving money between two categories' envelopes does not
change how much of total income has been assigned overall (FR-005).

**Category Envelope Balance** — a running figure per category:

```
envelopeBalance(categoryId, asOfYear, asOfMonth) =
    Σ allocation.limitAmount, for that category, (year, month) ≤ (asOfYear, asOfMonth)
  + Σ transfer.amount, where toCategoryId = categoryId, (year, month) ≤ (asOfYear, asOfMonth)
  − Σ transfer.amount, where fromCategoryId = categoryId, (year, month) ≤ (asOfYear, asOfMonth)
  − Σ bill.amount, for that category, time ≤ end of (asOfYear, asOfMonth)
```

`bill.amount` here is the raw, reversal-inclusive sum (`GetBillPersistencePort.getAll()`), so a
corrected or removed bill (008) already nets to its post-correction value with no special-casing —
see `research.md`.

## Relationships

```
Category 1───* Allocation           (categoryId FK, existing)
Category 1───* AllocationTransfer   (fromCategoryId FK, new)
Category 1───* AllocationTransfer   (toCategoryId FK, new)
Category 1───* Bill                 (categoryId FK, existing — feeds envelope "spent")
Income     ───* (unscoped)          (feeds "unallocated" — existing, no category link)
```

## Validation Rules

- `SetBudgetServiceImpl` (Allocation upsert): `limitAmount > 0`; `month` in 1–12; category exists;
  **new**: category's type MUST be `EXPENSE` or `BOTH` (FR-009).
- `MoveAllocationServiceImpl` (new): `amount > 0`; `fromCategoryId != toCategoryId`; both categories
  exist and are valid allocation targets; `amount` MUST NOT exceed the source category's current
  envelope balance as of the given (year, month) (FR-006) — rejected with a validation error naming
  the available balance.
- `RepeatAllocationsServiceImpl` (new): source month MUST have at least one allocation (US4
  Scenario 3 — "nothing to repeat" otherwise); target amount per category = existing target-month
  amount (0 if none) + source-month amount (additive top-up, FR-010).

## State Transitions

None — Allocation stays a simple upsert (no state machine). AllocationTransfer is create-only
(no transitions after creation).
