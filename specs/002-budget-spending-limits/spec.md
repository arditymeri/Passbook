# Feature Specification: Budget / Spending Limits

**Feature Branch**: `002-budget-spending-limits`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "Budget / Spending Limits — let a user set a monthly spending limit per category, then compare it against actual spendingByCategory from the spending analysis."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Set a Monthly Budget for a Category (Priority: P1)

As a user, I want to define a spending limit for a specific category in a given month, so that I
have a target to stay within when tracking my expenses.

**Why this priority**: Without the ability to create budgets, no other budget feature is possible.
This is the foundational write operation.

**Independent Test**: Can be fully tested by creating a budget entry for a category and month,
then retrieving it and verifying the stored amount matches what was submitted.

**Acceptance Scenarios**:

1. **Given** a valid category ID and a positive spending limit for May 2026, **When** the user submits a new budget, **Then** the budget is saved and returned with a generated ID, the category ID, the year, the month, and the limit amount.
2. **Given** a budget already exists for a category and month, **When** the user submits a new budget for the same category and month, **Then** the existing budget is updated (upsert), not duplicated.
3. **Given** a budget limit of zero or a negative value, **When** the user submits the budget, **Then** the request is rejected with a validation error.
4. **Given** a category ID that does not exist in the system, **When** the user submits a budget for it, **Then** the request is rejected with a not-found error.

---

### User Story 2 - View Budget vs. Actual Spending (Priority: P2)

As a user, I want to see my budget alongside my actual spending for a given month, so that I can
immediately understand which categories are over budget, on track, or have remaining allowance.

**Why this priority**: Viewing budget vs. actual is the core value of the feature — without it,
budgets are just stored numbers with no actionable insight.

**Independent Test**: Can be tested by seeding a budget and spending data for the same month,
then calling the budget status endpoint and verifying that each category shows the correct
budgeted amount, actual amount spent, remaining amount, and an over/under status flag.

**Acceptance Scenarios**:

1. **Given** a budget of €500 for "Groceries" in May 2026 and €420 of actual bills categorised as "Groceries" in May 2026, **When** the user requests the budget status for May 2026, **Then** the response shows budgeted=500, actual=420, remaining=80, status=UNDER_BUDGET for "Groceries".
2. **Given** a budget of €100 for "Entertainment" and €150 of actual spend, **When** the user requests the budget status, **Then** remaining=-50 and status=OVER_BUDGET for "Entertainment".
3. **Given** a category has a budget but zero actual spend, **When** the user requests the budget status, **Then** actual=0, remaining=budget amount, status=UNDER_BUDGET.
4. **Given** a category has actual spend but no budget defined, **When** the user requests the budget status, **Then** that category is included in the response with budgeted=0, actual=actual spend, status=OVER_BUDGET (no limit set means any spend is over).

---

### User Story 3 - List and Delete Budgets (Priority: P3)

As a user, I want to list all budgets for a given month and delete individual budgets I no longer
need, so that I can manage my budget entries as my financial goals change.

**Why this priority**: Read and delete complete the lifecycle. A user who sets a wrong budget must
be able to remove it.

**Independent Test**: Can be tested independently by creating several budgets for different
categories in one month, listing them, and verifying all are returned; then deleting one and
confirming it no longer appears.

**Acceptance Scenarios**:

1. **Given** three budgets exist for May 2026, **When** the user lists budgets for May 2026, **Then** all three are returned in the response.
2. **Given** a budget exists, **When** the user deletes it by ID, **Then** the response is success and the budget no longer appears in subsequent list calls.
3. **Given** a budget ID that does not exist, **When** the user attempts to delete it, **Then** the response is a not-found error.

---

### Edge Cases

- What happens when a month has no budgets set? The budget status response returns only the categories with actual spend, all marked OVER_BUDGET (no limit set).
- What happens when a month has budgets but no actual spend? All categories show actual=0, remaining=budget amount, status=UNDER_BUDGET.
- What happens if the same category appears in multiple months? Each (category, year, month) combination is a separate budget entry; they are independent.
- What happens when a budget limit is updated mid-month? The new limit takes effect immediately for all subsequent status queries.
- What is the maximum number of budgets per month? No enforced limit — one per category per month is the natural bound.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a user to create or update a spending limit (budget) for a specific category and a specific year-month combination.
- **FR-002**: The system MUST enforce that a budget limit is a positive value greater than zero; zero and negative values MUST be rejected.
- **FR-003**: The system MUST enforce that the referenced category exists; budgets for non-existent categories MUST be rejected.
- **FR-004**: Creating a budget for a category-month combination that already has a budget MUST update the existing record (upsert), not create a duplicate.
- **FR-005**: The system MUST provide a budget status view for a given year and month, returning for each category that has a budget or actual spend: the budgeted amount, the actual amount spent (sourced from bills), the remaining amount (budgeted minus actual), and a status of UNDER_BUDGET or OVER_BUDGET.
- **FR-006**: Categories with actual spend but no budget MUST appear in the status view with budgeted=0 and status=OVER_BUDGET.
- **FR-007**: The system MUST allow a user to list all budget entries for a given year and month.
- **FR-008**: The system MUST allow a user to delete a budget entry by its ID.
- **FR-009**: The remaining amount MAY be negative when actual spend exceeds the budget limit.

### Key Entities

- **Budget**: A spending limit set by the user for one category in one calendar month. Key attributes: unique ID, category ID, year (integer), month (integer 1–12), limit amount (exact positive decimal).
- **BudgetStatus**: A read-only view combining a Budget with actual spend from the bill data. Key attributes: category ID, budgeted amount, actual amount, remaining amount (budgeted − actual), status (UNDER_BUDGET or OVER_BUDGET).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can create or update a monthly category budget with a single action and immediately see the updated budget reflected in the status view without any additional steps.
- **SC-002**: The budget status view for a month correctly computes remaining and over/under status for 100% of categories that have a budget or actual spend in that month.
- **SC-003**: A user can list all budgets for a month and delete any entry in two actions or fewer.
- **SC-004**: Budget status values are arithmetically consistent: `remaining = budgeted − actual` holds for 100% of entries in every response.

## Assumptions

- Each budget is scoped to a single (category, year, month) triplet; multi-month or annual budgets are out of scope for this version.
- Actual spend for the budget status view is sourced exclusively from the existing Bill data (expenses), not from income entries.
- Budgets reference existing categories by ID; categories are managed separately and must exist before a budget can be created for them.
- There is no notification or alerting when a user goes over budget in this version — status is read-only, not push-based.
- Currency is not tracked at the budget level; all amounts are treated as the same currency as the underlying bills (multi-currency budget comparison is out of scope).
- Only the budget limit amount can be updated; the category and month of an existing budget cannot be changed (delete and recreate instead).
