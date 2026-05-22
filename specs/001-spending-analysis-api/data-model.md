# Data Model: Spending Analysis API

**Feature**: 001-spending-analysis-api
**Date**: 2026-05-23

## Entities

### MonthlySummary (read model — no new table)

Represents the aggregated financial picture for one calendar month.
Derived entirely from existing `bill` and `income` rows. No new database table is created.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `year` | int | No | Calendar year (e.g., 2026) |
| `month` | int | No | Calendar month, 1–12 |
| `totalIncome` | BigDecimal | No | Sum of all income amounts in the month (0 if none) |
| `totalExpenses` | BigDecimal | No | Sum of all bill amounts in the month (0 if none) |
| `netBalance` | BigDecimal | No | `totalIncome − totalExpenses` |
| `spendingByCategory` | Map<String, BigDecimal> | No | Category ID → total spent; excludes uncategorised bills |

**Validation rules**:
- `month` must be in range [1, 12]
- `from` date must not be after `to` date for period queries
- All monetary values: `BigDecimal`, `RoundingMode.HALF_EVEN` if rounding needed

---

## Existing Tables Used (read-only)

### `bill`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `amount` | NUMERIC | Expense amount |
| `time` | TIMESTAMPTZ | Used for date-range filtering |
| `category_id` | VARCHAR | Nullable; used for `spendingByCategory` grouping |
| `account_id` | VARCHAR | Not used in analysis aggregation |

### `income`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `amount` | NUMERIC | Income amount |
| `time` | TIMESTAMPTZ | Used for date-range filtering |

---

## Repository Changes (no schema migration needed)

### BillRepository — add derived query

```java
List<BillEntity> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
```

### IncomeRepository — add derived query

```java
List<IncomeEntity> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
```

No database migrations required — only new JPQL derived queries on existing columns.
