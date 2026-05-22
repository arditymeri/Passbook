# Feature Specification: Spending Analysis API

**Feature Branch**: `001-spending-analysis-api`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "Spending Analysis API — expose the existing GetSpendingAnalysisService domain interface (getMonthlySummary and getSummaryForPeriod) via REST."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Monthly Financial Summary (Priority: P1)

As a user, I want to see a summary of my income, expenses, and net balance for a specific month,
so that I can understand how I performed financially in that period at a glance.

**Why this priority**: This is the core value of the feature. A single month view is the most
common financial review pattern and delivers immediate insight with minimal input.

**Independent Test**: Can be fully tested by requesting a summary for a specific year and month,
and verifying that totals for income, expenses, net balance, and per-category spending are returned
correctly based on existing transaction data.

**Acceptance Scenarios**:

1. **Given** bills and incomes exist for May 2026, **When** the user requests the summary for year=2026 month=5, **Then** the response contains `totalIncome`, `totalExpenses`, `netBalance` (income minus expenses), and `spendingByCategory` with correct amounts per category.
2. **Given** no transactions exist for the requested month, **When** the user requests the monthly summary, **Then** the response returns all monetary fields as zero and `spendingByCategory` as an empty map.
3. **Given** an invalid month value (e.g., month=13), **When** the user requests the summary, **Then** the response is a 400 Bad Request with a descriptive error.

---

### User Story 2 - View Summary for a Date Range (Priority: P2)

As a user, I want to retrieve financial summaries across a custom date range (e.g., Q1, a fiscal
year, or any arbitrary period), so that I can analyse trends and totals over time spans not limited
to a single calendar month.

**Why this priority**: Period analysis enables trend spotting and budget reviews. It builds on the
monthly summary but adds significant analytical power. Depends on the monthly summary working first.

**Independent Test**: Can be tested by requesting a range spanning multiple months and verifying
that the response contains one `MonthlySummary` entry per calendar month in the range, each with
correct aggregated totals.

**Acceptance Scenarios**:

1. **Given** transactions exist across January, February, and March 2026, **When** the user requests the period from 2026-01-01 to 2026-03-31, **Then** the response contains three monthly summary entries, one per month, each with correct totals.
2. **Given** a date range where `from` is after `to`, **When** the user requests the period summary, **Then** the response is a 400 Bad Request.
3. **Given** a date range spanning months with no transactions, **When** the user requests the period, **Then** those months are included in the response with zero values.

---

### Edge Cases

- What happens when `spendingByCategory` references a category that has since been deleted? The category ID is included in the map; the display name is resolved by the consumer.
- What happens when a bill has no category assigned? It is counted in `totalExpenses` but does not appear in `spendingByCategory`.
- What happens if the date range spans more than 24 months? The response is returned in full; no artificial cap is imposed, but latency may increase for very large ranges.
- What happens when income and bill amounts are in different currencies? All amounts are summed in their stored currency without conversion (multi-currency conversion is out of scope for this feature).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an endpoint to retrieve a financial summary for a specific year and month, returning total income, total expenses, net balance, and spending broken down by category.
- **FR-002**: The system MUST expose an endpoint to retrieve a list of monthly financial summaries for a given date range (from date to to date, inclusive).
- **FR-003**: The system MUST calculate `netBalance` as `totalIncome − totalExpenses` for each monthly period.
- **FR-004**: The system MUST aggregate bill amounts per category ID into the `spendingByCategory` map; bills with no category MUST be excluded from the map but still counted in `totalExpenses`.
- **FR-005**: The system MUST validate that month values are between 1 and 12, and that the `from` date is not after the `to` date, returning 400 on violation.
- **FR-006**: The system MUST return zero values (not errors) for months that have no recorded transactions.
- **FR-007**: The system MUST source income totals from the Income data set and expense totals from the Bill data set.

### Key Entities

- **MonthlySummary**: Represents the aggregated financial picture for one calendar month. Key attributes: `year` (integer), `month` (integer 1–12), `totalIncome` (exact decimal), `totalExpenses` (exact decimal), `netBalance` (exact decimal), `spendingByCategory` (map of category ID → exact decimal amount).
- **DateRange**: A bounded period defined by a `from` date and a `to` date (both inclusive, date-only, no time component required).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can retrieve a monthly summary with a single request and receive a complete breakdown of income, expenses, and per-category spending without requiring any additional calls.
- **SC-002**: A user can retrieve summaries for a period of up to 12 months and receive a correctly ordered list of monthly entries within a response time consistent with other read endpoints in the application.
- **SC-003**: All monetary totals in the summary are arithmetically consistent: `netBalance = totalIncome − totalExpenses` holds for 100% of responses.
- **SC-004**: The feature integrates with the existing category and account data so that `spendingByCategory` keys are valid category IDs already in the system, requiring no additional data setup.

## Assumptions

- The existing `Bill` entity represents expenses, and the existing `Income` entity represents inflows; no new transaction types are introduced by this feature.
- All monetary amounts are stored as exact decimals in the database; no currency conversion is performed — summaries aggregate amounts regardless of currency.
- Authentication and authorisation for the new endpoints follow the same policy as existing endpoints (no new security model required).
- The `spendingByCategory` map uses category IDs as keys; resolving category names for display is the responsibility of the API consumer (e.g., the frontend).
- The `from` and `to` parameters for the period endpoint are calendar dates (year-month-day); time-of-day is not considered.
- No pagination is required for the period endpoint in this version; the full list of monthly summaries for the requested range is returned in one response.
