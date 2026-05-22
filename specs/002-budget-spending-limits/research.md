# Research: Budget / Spending Limits

**Feature**: 002-budget-spending-limits
**Date**: 2026-05-23

## Decision 1: Upsert Strategy for Budget (category + month uniqueness)

**Decision**: Enforce a unique constraint at the application layer via a domain check: before
persisting, query `GetBudgetPersistencePort.findByCategoryIdAndYearAndMonth()`. If a record
exists, update its `limitAmount`; otherwise insert a new record. The repository derives this via
`findByCategoryIdAndYearAndMonth(String categoryId, int year, int month)`.

**Rationale**: Spring Data JPA `save()` on a managed entity performs an update; on a new entity
it inserts. Checking existence first in the domain service keeps the upsert logic in the domain
layer, framework-free, and testable. A unique DB constraint on `(category_id, year, month)` is
added as a safety net but is not the primary enforcement mechanism.

**Alternatives considered**:
- `@Query` with native `INSERT ... ON CONFLICT DO UPDATE`: works but puts business logic in SQL.
- Separate PUT endpoint for update: more RESTful but complicates the client — upsert via POST is
  simpler and matches the spec's single-action requirement.

---

## Decision 2: Actual Spend Source for Budget Status

**Decision**: Reuse `GetSpendingAnalysisPersistencePort.getBillsByPeriod(from, to)` to fetch
bills for the month. `GetBudgetStatusServiceImpl` depends on both `GetBudgetPersistencePort` and
`GetSpendingAnalysisPersistencePort`. No new persistence port needed for actual spend.

**Rationale**: The analysis port already returns `List<BillDto>` filtered by date range. Reusing
it avoids duplicating bill-query logic. The domain service aggregates per-category totals using
the same streaming pattern as `GetSpendingAnalysisServiceImpl`.

**Alternatives considered**:
- New `GetActualSpendPersistencePort`: clean separation but redundant — identical query to the
  existing analysis port.
- Calling `GetSpendingAnalysisService.getMonthlySummary()` directly: creates a service-to-service
  domain dependency; port-based access is preferred per Principle VIII.

---

## Decision 3: Category Existence Validation

**Decision**: `SetBudgetServiceImpl` calls `GetCategoryPersistencePort.getCategoryById(categoryId)`
before persisting. If the category does not exist, it throws a `NoSuchElementException` which the
controller maps to `404 Not Found`.

**Rationale**: The spec requires that budgets for non-existent categories are rejected. Validating
in the domain service keeps this rule in the business layer. The existing `GetCategoryPersistencePort`
already has `getCategoryById()` — no new infrastructure needed.

**Alternatives considered**:
- DB foreign key constraint: catches the error but produces a generic DB error, not a meaningful
  domain validation message.
- Skip validation: violates spec FR-003 and leads to orphaned budgets.

---

## Decision 4: BudgetStatus Computation

**Decision**: `GetBudgetStatusServiceImpl` builds a unified `Map<String, BudgetStatusDto>` by:
1. Loading all budgets for the month → populates `budgeted` per categoryId.
2. Loading all bills for the month via `getBillsByPeriod` → aggregates actual spend per categoryId.
3. Merging both maps: categories in either set appear in the result.
4. `remaining = budgeted - actual`; `status = OVER_BUDGET` if actual > budgeted (or budgeted = 0
   with any actual spend), else `UNDER_BUDGET`.

**Rationale**: Pure in-memory merge in the domain service; no complex SQL JOIN needed. On
personal-scale data (hundreds of bills/month) this is well within performance budget.

**Alternatives considered**:
- Single SQL JOIN query across budget and bill tables: more efficient at scale but breaks
  hexagonal boundary by requiring cross-table infrastructure knowledge in one adapter.

---

## Decision 5: API Endpoint Design

**Decision**:
- `POST /api/v1/budgets` — create or update budget (upsert by categoryId + year + month)
- `GET /api/v1/budgets?year={year}&month={month}` — list raw budget entries for a month
- `GET /api/v1/budgets/status?year={year}&month={month}` — budget vs actual status view
- `DELETE /api/v1/budgets/{id}` — delete a budget by ID

**Rationale**: Separating raw budget list from status view keeps concerns clean — the list is
for management (edit/delete), the status view is for analysis. Both are GET endpoints with
the same parameters but different response shapes.

**Alternatives considered**:
- Embedding status in the list endpoint: conflates management and analysis; harder to paginate
  or cache independently in the future.
- PUT /budgets/{id} for updates: requires a prior GET to find the ID; the upsert POST is simpler.
