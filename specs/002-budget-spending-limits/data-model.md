# Data Model: Budget / Spending Limits

**Feature**: 002-budget-spending-limits
**Date**: 2026-05-23

## New Entity: Budget

Represents a user-defined spending limit for one category in one calendar month.

| Field | Type | Nullable | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `id` | UUID | No | PK, generated | Unique budget identifier |
| `categoryId` | String (UUID) | No | Must reference existing category | Category this budget applies to |
| `year` | int | No | > 0 | Calendar year |
| `month` | int | No | 1–12 | Calendar month |
| `limitAmount` | BigDecimal | No | > 0 | Monthly spending limit |

**Unique constraint**: `(categoryId, year, month)` — one budget per category per month.

**Validation rules**:
- `limitAmount` MUST be > 0 (enforced in domain service)
- `categoryId` MUST reference an existing category (enforced in domain service)
- `month` MUST be in [1, 12]

---

## Read Model: BudgetStatus (no new table)

Derived in-memory by `GetBudgetStatusServiceImpl`. Not persisted.

| Field | Type | Description |
|-------|------|-------------|
| `categoryId` | String | Category identifier |
| `budgeted` | BigDecimal | Limit from the Budget record (0 if no budget set) |
| `actual` | BigDecimal | Sum of bills for this category in the month |
| `remaining` | BigDecimal | `budgeted − actual` (may be negative) |
| `status` | BudgetStatus enum | `UNDER_BUDGET` if actual ≤ budgeted, else `OVER_BUDGET` |

**Status rules**:
- `OVER_BUDGET` if `actual > budgeted` OR (`budgeted == 0` AND `actual > 0`)
- `UNDER_BUDGET` otherwise

---

## New Database Table: `budget`

```sql
CREATE TABLE budget (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id VARCHAR(36) NOT NULL,
    year        INTEGER NOT NULL,
    month       INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    limit_amount NUMERIC NOT NULL CHECK (limit_amount > 0),
    CONSTRAINT uq_budget_category_month UNIQUE (category_id, year, month)
);
```

Hibernate DDL auto (`update`) will create this table on first startup. No manual migration needed.

---

## New Enum: BudgetStatus

```
UNDER_BUDGET   — actual spend is at or below the budget limit
OVER_BUDGET    — actual spend exceeds the budget limit (or no limit was set)
```

---

## Repository Queries Needed

### BudgetRepository

```java
List<BudgetEntity> findByYearAndMonth(int year, int month);
Optional<BudgetEntity> findByCategoryIdAndYearAndMonth(String categoryId, int year, int month);
```

Standard Spring Data JPA derived queries — no custom SQL required.

---

## Relationships to Existing Data

- `Budget.categoryId` references `CategoryEntity.id` (validated in domain, not enforced as a FK)
- `BudgetStatus.actual` is derived from `BillEntity.amount` where `bill.time` falls in the month
  and `bill.categoryId` matches — sourced via the existing `GetSpendingAnalysisPersistencePort`
