# Data Model: Device Sync via File Export

## New persisted fields

| Entity | New column | Type | Set when |
|---|---|---|---|
| `CategoryEntity` | `updated_at` | timestamp | created; renamed/recolored/reparented |
| `AccountEntity` | `updated_at` | timestamp | created; updated (name/type/balance/institution) |
| `BudgetEntity` | `updated_at` | timestamp | created; limit changed (`SetBudgetPostgresAdapter.upsert`) |
| `RecurringSeriesEntity` | `updated_at` | timestamp | created (detected); status changed (confirm/dismiss) |
| `SavingsGoalEntity` | `updated_at` | timestamp | created; updated |
| `BillEntity` | `necessity_tag_updated_at` | nullable timestamp | necessity tag set/changed/cleared (feature 018's `UpdateBillNecessityTagPostgresAdapter`) |
| `BillEntity`, `IncomeEntity` | `recorded_at` | timestamp | any row is first written (original, correction-replacement, or reversal) — never changes afterward |

All additive, nullable-safe under `ddl-auto=update`, no backfill required: existing rows get `NULL`/absent values, which the merge logic treats as "older than anything with a real timestamp" (loses every last-modified comparison) — reasonable since it means "this device has never told anyone about a change to this row."

## Derived (non-persisted) concepts

### Sync Snapshot (`SyncSnapshotDto`)

The full export payload:

| Field | Type |
|---|---|
| `schemaVersion` | int |
| `exportedAt` | timestamp |
| `accounts` | `AccountDto[]` (each carrying `updatedAt`) |
| `categories` | `CategoryDto[]` (each carrying `updatedAt`) |
| `budgets` | `BudgetDto[]` (each carrying `updatedAt`) — the *entire* history via `GetBudgetPersistencePort.getAll()`, not one month |
| `recurringSeries` | `RecurringSeriesDto[]` (each carrying `updatedAt`) |
| `bills` | `BillDto[]` (each carrying `recordedAt`, `necessityTagUpdatedAt`; includes correction-replacement and reversal rows exactly as stored) |
| `incomes` | `IncomeDto[]` (each carrying `recordedAt`; includes correction-replacement and reversal rows exactly as stored) |
| `savingsGoals` | `SavingsGoalDto[]` (each carrying `updatedAt`) |

### Entity matching rule (per research.md R2)

For each entity type, "is this the same entity as one I already have" is decided by, in order:

1. Same `id` → same entity.
2. Different `id` but same natural key (table below) → same entity (a natural-key collision), resolved by whichever of the two has the later `updatedAt`.
3. Neither → a new entity to insert.

| Entity | Natural key |
|---|---|
| Category | `name` |
| Account | `name` |
| Budget | `(categoryId, year, month)` |
| Recurring series | `(transactionType, groupKey, normalizedDescription)` |
| Savings goal | *(id only — no fallback)* |
| Bill / Income | *(id only — no fallback; this is the point of Principle II)* |

### Correction conflict (per research.md R3)

A **correction conflict** exists when, after merging bills (or incomes), more than one non-reversal row shares the same non-null `correctsTransactionId`. Resolution: the row with the latest `recordedAt` is the bill's current value (appears in `GetBillService.getAll()`); every other sibling is retained (Principle I) but treated as superseded, visible only through `getHistory()`.

### Merge Plan (`MergePlanDto`, internal to the Domain service — never serialized to the API as such)

Per entity type, three buckets computed by `computeMergePlan`:

| Bucket | Meaning |
|---|---|
| `toInsert` | Not matched to anything locally (new id and no natural-key collision) |
| `toUpdate` | Matched locally, incoming `updatedAt`/`recordedAt` is later → local value replaced with incoming |
| `unchanged` | Matched locally, local value is already current (same or later) → nothing happens |

Plus, for bills/incomes specifically, a `correctionConflicts` list: pairs of sibling rows and which one won.

### Import Summary (`ImportSummaryDto` / API `importSummary`)

What `GET .../preview` and `POST .../apply` both return — the user-facing view of a `MergePlanDto`:

| Field | Type |
|---|---|
| `perType` | map of entity type name → `{ added: int, updated: int, unchanged: int }` |
| `correctionConflictsResolved` | int — count of sibling-correction conflicts found and resolved |
| `applied` | boolean — `false` from `/preview`, `true` from `/apply` |

## Validation rules

- `schemaVersion` in an imported file MUST match a version this build recognizes exactly, or the import is rejected outright (FR-010) — no partial application, no best-effort field-skipping in v1.
- An import MUST NOT ever remove a locally-existing row of any kind (FR-011) — `computeMergePlan` only ever produces inserts and updates, never deletions.
- Entity processing follows the fixed dependency order in research.md R6; a bill/income/savings-goal referencing an account or category id not present locally *and* not itself present in the same import is rejected as malformed (should not occur from a snapshot honestly produced by this same feature's export, but guards against a hand-edited or corrupted file).
