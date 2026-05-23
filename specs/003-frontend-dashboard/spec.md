# Feature Specification: Frontend Dashboard

**Feature Branch**: `003-frontend-dashboard`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "Frontend Dashboard — a single-page dashboard that visualises the user's financial data. Shows: monthly income vs expenses summary, per-category spending breakdown, budget vs actual status per category, and a list of recent bills and incomes. User can navigate between months. No authentication — single user app."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Monthly Financial Summary (Priority: P1)

As a user, I want to open the dashboard and immediately see my income, expenses, and net balance
for the current month, so that I understand my financial health at a glance without any setup.

**Why this priority**: This is the entry point of the dashboard. Everything else builds on having
a clear summary visible first. If this story alone is delivered, the dashboard already has value.

**Independent Test**: Open the dashboard. Verify it shows the current month's name and year, a
total income figure, a total expenses figure, and a net balance (income minus expenses). Verify
a previous/next month navigation control is present and functional.

**Acceptance Scenarios**:

1. **Given** the dashboard loads, **When** the page is displayed, **Then** the current month and year are shown as the selected period, along with total income, total expenses, and net balance figures.
2. **Given** the dashboard is on May 2026, **When** the user clicks "previous month", **Then** the display updates to April 2026 with the correct figures for that month.
3. **Given** the dashboard is on May 2026, **When** the user clicks "next month", **Then** the display updates to June 2026.
4. **Given** no transactions exist for the selected month, **When** the dashboard loads that month, **Then** all monetary figures display as zero rather than showing an error.

---

### User Story 2 - View Per-Category Spending Breakdown (Priority: P2)

As a user, I want to see how much I spent in each category for the selected month, so that I can
identify where my money is going and which categories consume the most of my budget.

**Why this priority**: The summary total alone doesn't reveal spending patterns. The category
breakdown is the most actionable insight — it tells the user what to change.

**Independent Test**: With bills assigned to multiple categories in the selected month, open the
dashboard and verify the spending breakdown section lists each category with its total spend amount,
ordered from highest to lowest.

**Acceptance Scenarios**:

1. **Given** bills in three categories for the selected month, **When** the breakdown section is displayed, **Then** each category appears with its name and total spent amount, sorted by amount descending.
2. **Given** no categorised bills for the selected month, **When** the breakdown section is displayed, **Then** a "no spending data" placeholder is shown rather than an empty or broken layout.
3. **Given** the user navigates to a different month, **When** the month changes, **Then** the category breakdown updates automatically to reflect the new month's data.

---

### User Story 3 - View Budget vs. Actual Status (Priority: P3)

As a user, I want to see each category's spending limit alongside what I actually spent, with a
clear visual indicator of whether I am over or under budget, so that I can act on budget overruns
immediately.

**Why this priority**: This is the direct consumer of the Budget feature built in sprint 002.
Without a visual representation, the budget data is not actionable.

**Independent Test**: With at least one budget set and bills posted for the same month, open the
dashboard and verify that the budget section shows the category name, budgeted amount, actual
amount, remaining amount, and an OVER/UNDER indicator for each category that has a budget or spend.

**Acceptance Scenarios**:

1. **Given** a budget of €500 for Groceries and €420 of actual spend, **When** the budget status section is displayed, **Then** Groceries shows budgeted=500, actual=420, remaining=80, and a green UNDER BUDGET indicator.
2. **Given** a category with €150 spent and no budget set, **When** the budget status section is displayed, **Then** that category shows budgeted=0, actual=150, and a red OVER BUDGET indicator.
3. **Given** no budgets and no spending for a month, **When** the budget status section is displayed, **Then** a "no budget data" placeholder is shown.

---

### User Story 4 - View Recent Transactions (Priority: P4)

As a user, I want to see a list of my most recent bills and incomes for the selected month, so
that I can quickly verify recent entries without navigating to a separate screen.

**Why this priority**: The summary and charts provide the big picture; recent transactions provide
the detail layer. P4 because the other three stories deliver the primary value independently.

**Independent Test**: With several bills and incomes recorded for the selected month, open the
dashboard and verify a transaction list shows each entry's date, description, amount, and type
(bill or income), ordered by date descending. Verify the list is limited to the most recent 10
entries.

**Acceptance Scenarios**:

1. **Given** bills and incomes exist for the selected month, **When** the transactions section is displayed, **Then** entries appear ordered by date descending, each showing date, description, amount, and whether it is an expense or income.
2. **Given** more than 10 transactions exist, **When** the transactions section is displayed, **Then** only the 10 most recent are shown.
3. **Given** no transactions exist for the selected month, **When** the transactions section is displayed, **Then** a "no transactions" placeholder is shown.

---

### Edge Cases

- What happens when the backend is unreachable? Each section displays a loading error message independently; the rest of the dashboard remains functional.
- What happens when the user navigates to a future month with no data? All sections show zero or empty placeholders — no error states.
- What happens when category names are very long? Names are truncated with an ellipsis at a reasonable width; the full name is visible on hover.
- What happens when net balance is negative (spending exceeds income)? The net balance is displayed in red to signal a deficit; positive net balance is displayed in green.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The dashboard MUST display total income, total expenses, and net balance for the currently selected month.
- **FR-002**: The dashboard MUST allow the user to navigate to the previous or next calendar month using dedicated controls, updating all sections automatically.
- **FR-003**: The dashboard MUST display per-category spending amounts for the selected month, sorted by amount descending.
- **FR-004**: The dashboard MUST display budget vs. actual status per category for the selected month, including budgeted amount, actual amount, remaining amount, and an over/under indicator.
- **FR-005**: The dashboard MUST display the 10 most recent transactions (bills and incomes combined) for the selected month, ordered by date descending, each showing date, description, amount, and type.
- **FR-006**: Each dashboard section MUST handle the loading and error states independently — a failure in one section MUST NOT prevent other sections from displaying.
- **FR-007**: The dashboard MUST display a loading indicator while data is being fetched for any section.
- **FR-008**: The net balance MUST be visually distinguished as positive (green) or negative (red).
- **FR-009**: The over/under budget indicator MUST use a consistent visual language: green for under budget, red for over budget.

### Key Entities

- **MonthlyPeriod**: The currently selected year and month; drives all data fetches. Default is the current calendar month.
- **MonthlySummary**: Aggregated income, expenses, and net balance for the period (sourced from the spending analysis API).
- **CategorySpend**: A category name and its total spend amount for the period (sourced from spendingByCategory in the summary response).
- **BudgetStatusEntry**: Per-category budgeted, actual, remaining, and status for the period (sourced from the budget status API).
- **Transaction**: A combined bill or income entry with date, description, amount, and type (sourced from the bills and incomes list APIs).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can open the dashboard and see all four sections populated with current-month data within 3 seconds on a local network connection.
- **SC-002**: A user can navigate from the current month to any past month in 2 clicks or fewer and see all sections refresh correctly.
- **SC-003**: 100% of budget status entries correctly reflect the over/under state consistent with the underlying budget and spending data.
- **SC-004**: The dashboard remains usable (other sections display correctly) when any single backend data source returns an error.

## Assumptions

- The dashboard connects to the Spring Boot backend running at `http://localhost:8080`; the Vite dev proxy forwards `/api` requests to avoid CORS issues.
- Category names are resolved by the backend in the `spendingByCategory` map using category IDs as keys; the dashboard must fetch the category list separately to display human-readable names, or display the category ID as a fallback if names are unavailable.
- The transaction list (US4) combines bills from `GET /api/v1/bills` and incomes from `GET /api/v1/incomes`, filtered client-side to the selected month, and merged and sorted by date.
- No charting library is required for the MVP — a bar or progress-bar representation using CSS is sufficient for the spending and budget sections; a charting library can be added later.
- No routing or multi-page navigation is in scope — the dashboard is a single view.
- The "current month" default on load is derived from the user's local system clock.
- No data entry (creating bills, incomes, budgets) is in scope for this dashboard — it is read-only.
