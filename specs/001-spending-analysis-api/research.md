# Research: Spending Analysis API

**Feature**: 001-spending-analysis-api
**Date**: 2026-05-23

## Decision 1: JPA Query Strategy for Date Range on OffsetDateTime

**Decision**: Use Spring Data JPA derived queries (`findByTimeBetween`) on the existing `time`
field (`OffsetDateTime`) in `BillEntity` and `IncomeEntity`. The start date is the first instant
of the first day of the month; the end date is the last instant of the last day of the month,
both expressed as `OffsetDateTime` with UTC offset.

**Rationale**: The existing repositories already extend `JpaRepository`. A derived method
`findByTimeBetween(OffsetDateTime start, OffsetDateTime end)` requires no custom JPQL or native
SQL and stays within the established pattern. The domain service already computes start/end
`LocalDate`; the adapter converts them to `OffsetDateTime` at UTC midnight / end-of-day.

**Alternatives considered**:
- `@Query` with JPQL `BETWEEN`: more explicit but unnecessary for a simple range on one field.
- Native SQL: overkill, breaks database portability.

---

## Decision 2: OpenAPI Model for Monetary Amounts

**Decision**: Use `type: number` (no `format` qualifier) for all monetary fields in the analysis
OpenAPI model. The Application mapper converts between the generated `BigDecimal`-compatible number
type and the domain's `BigDecimal`.

**Rationale**: The existing bill/income models use `format: double` which risks floating-point
precision loss at the boundary. For the new analysis model, omitting `format` lets the OpenAPI
generator produce a type that the mapper can handle cleanly. The domain and persistence layers
always use `BigDecimal` (Principle IV).

**Alternatives considered**:
- `type: string` with regex pattern: more precise but breaks standard JSON number semantics
  and complicates frontend parsing.
- `format: double`: already used in existing models but violates Principle IV at the wire level.

---

## Decision 3: spendingByCategory Representation in OpenAPI

**Decision**: Represent `spendingByCategory` as a free-form object with `additionalProperties`
of type `number`:

```yaml
spendingByCategory:
  type: object
  additionalProperties:
    type: number
  description: "Map of category ID (string) to total spent amount"
```

**Rationale**: OpenAPI 3.0 supports dictionary/map types via `additionalProperties`. Keys are
category UUIDs (strings); values are decimal amounts. This maps naturally to `Map<String, BigDecimal>`
in the domain.

**Alternatives considered**:
- Array of `{categoryId, amount}` objects: more verbose, harder to look up by category.
- Inline enum of known categories: not feasible — categories are user-defined.

---

## Decision 4: Endpoint URL Design

**Decision**:
- `GET /api/v1/analysis/monthly?year={year}&month={month}` — single month summary
- `GET /api/v1/analysis/period?from={date}&to={date}` — multi-month summary list

**Rationale**: Query parameters (not path segments) for date inputs keep the URL readable and
avoid ambiguity with REST resource hierarchies. `from`/`to` as ISO 8601 dates (`YYYY-MM-DD`)
are standard and parseable by `LocalDate` in Spring controllers.

**Alternatives considered**:
- Path segments `/analysis/2026/05`: RESTful but requires a separate resource model per month;
  less natural for arbitrary ranges.
- POST with body: unnecessary complexity for a read operation.

---

## Decision 5: No New Domain Logic

**Decision**: `GetSpendingAnalysisServiceImpl` is fully implemented and requires no changes.
`GetSpendingAnalysisPersistencePort` is already defined. This feature is pure wiring.

**Rationale**: The domain service correctly aggregates bills and incomes using `BigDecimal`,
filters uncategorised bills from the category map, and iterates months for period queries.
No business logic gaps were identified during spec review.

**Alternatives considered**: N/A — domain was pre-existing and correct.
