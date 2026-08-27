# Phase 1 Data Model: Transaction Search & Filtering

No new persisted entity, table, or Domain DTO — this feature queries the existing `Bill`/`Income`
transaction data through the frontend's existing `Transaction` type (`frontend/src/types/index.ts`,
already unified across bill and income). What follows is the new client-side-only filter shape and
the pure matching logic that operates on it.

## Client-side types (new)

### `TransactionTypeFilter` (new type)

```ts
type TransactionTypeFilter = 'ALL' | 'BILL' | 'INCOME';
```

### `TransactionFilters` (new interface)

| Field | Type | Notes |
|---|---|---|
| `searchText` | `string` | Case-insensitive substring match against `Transaction.description` (FR-001). Empty string means "no text filter." |
| `categoryId` | `string \| undefined` | Matches `Transaction.categoryId` exactly (FR-003). Only ever set together with `source` being unset, per the UI decision in research.md — but the matching logic itself treats the two as independent, AND-combined predicates regardless. |
| `source` | `IncomeSource \| undefined` (existing enum, reused) | Matches `Transaction.source` exactly (FR-004). |
| `accountId` | `string \| undefined` | Matches `Transaction.accountId` exactly (FR-005). |
| `startDate` | `string \| undefined` (ISO date) | Transaction's `time` MUST be on or after this date (FR-006). |
| `endDate` | `string \| undefined` (ISO date) | Transaction's `time` MUST be on or before this date (FR-006). |
| `minAmount` | `number \| undefined` | Transaction's `amount` MUST be `>=` this value (FR-007). |
| `maxAmount` | `number \| undefined` | Transaction's `amount` MUST be `<=` this value (FR-007). |
| `type` | `TransactionTypeFilter` | `'ALL'` (default), `'BILL'`, or `'INCOME'` (FR-008). |

An "all filters cleared" state is `{ searchText: '', categoryId: undefined, source: undefined,
accountId: undefined, startDate: undefined, endDate: undefined, minAmount: undefined,
maxAmount: undefined, type: 'ALL' }` — the same shape a "Clear filters" action resets to (FR-011).

## Derived value (not stored): filtered transaction list

```
filterTransactions(transactions: Transaction[], filters: TransactionFilters): Transaction[] =
    transactions.filter(t =>
        (filters.searchText === '' OR normalize(t.description).includes(normalize(filters.searchText)))
        AND (filters.categoryId is undefined OR t.categoryId === filters.categoryId)
        AND (filters.source is undefined OR t.source === filters.source)
        AND (filters.accountId is undefined OR t.accountId === filters.accountId)
        AND (filters.startDate is undefined OR t.time >= filters.startDate)
        AND (filters.endDate is undefined OR t.time <= filters.endDate)
        AND (filters.minAmount is undefined OR t.amount >= filters.minAmount)
        AND (filters.maxAmount is undefined OR t.amount <= filters.maxAmount)
        AND (filters.type === 'ALL' OR t.type === filters.type)
    )
    // already sorted newest-first by the caller (allTransactions from useDashboardData)
```

`normalize(s)` is `(s ?? '').trim().toLowerCase()` — reused conceptually from 010's
`RecurringMatching.normalizeDescription`, though this is a *substring* `includes()` check, not an
*exact*-match comparison (010's grouping needs exact matches; this feature's search is intentionally
partial-match, per FR-001 and the spec's Assumptions section).

`transactions` here is always `allTransactions` from `useDashboardData.ts` (the full,
unsliced, all-months, already correction-aware merged bill+income list) — never the existing
month-scoped `transactions` value, so search and filters are never accidentally limited to the
currently selected month (FR-002).

## Relationships

```
Transaction (existing, unified bill+income view) ──filtered by──> TransactionFilters (new, client-side only, never persisted)
```

No relationship to any new entity exists, because none is introduced.

## Validation Rules

- An invalid range (`startDate` after `endDate`, or `minAmount` greater than `maxAmount`) is not
  rejected — per the spec's Edge Cases, it simply produces zero matches, since every transaction's
  `time`/`amount` fails at least one of the two range conditions. No client-side validation error
  is shown for this case.
- `searchText`, when non-empty, matches on `description` only — never on category name, account
  name, or any other field (FR-001 scopes it to description text specifically).

## State Transitions

None — `TransactionFilters` is transient UI state (a `useState` value in `App.tsx`), never
persisted, with no lifecycle beyond "the current filter bar values."
