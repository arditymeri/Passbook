# Feature Specification: Frontend Data Entry

**Feature Branch**: `004-frontend-data-entry`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "Frontend Data Entry — allow users to create bills and incomes directly from the dashboard without leaving the browser, making the app self-contained and usable day-to-day without Swagger UI."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record an Expense (Bill) from the Dashboard (Priority: P1)

As a user, I want to enter a new expense directly on the dashboard, so that I can record a
purchase immediately without switching to a separate tool.

**Why this priority**: Bills are the most frequent data entry action in a personal finance app.
If users can't add expenses from the UI, the dashboard has no data to show. This is the highest-
value, most-used entry point.

**Independent Test**: Click "Add Expense", fill in amount and date, submit — verify the expense
appears reflected in the dashboard summary when the same month is selected. Verify that submitting
with a missing amount or zero amount shows a validation error without submitting.

**Acceptance Scenarios**:

1. **Given** the dashboard is open, **When** the user clicks "Add Expense", **Then** a form appears with fields for amount, date, description (optional), and category (optional dropdown).
2. **Given** a valid amount and date are entered, **When** the user submits, **Then** the expense is saved, the form closes, and the dashboard data refreshes to reflect the new entry.
3. **Given** the amount field is empty or zero, **When** the user attempts to submit, **Then** a validation message is shown and the form is not submitted.
4. **Given** the user opens the form and then clicks Cancel or presses Escape, **When** the form closes, **Then** no data is saved and the dashboard is unchanged.

---

### User Story 2 - Record an Income Entry from the Dashboard (Priority: P2)

As a user, I want to enter a new income entry directly on the dashboard, so that I can log
salary, freelance payments, or other income as soon as I receive them.

**Why this priority**: Income entry completes the data entry picture alongside expenses. Both
sides of the ledger need to be enterable from the UI.

**Independent Test**: Click "Add Income", fill in amount and date, submit — verify the income
appears in the dashboard summary for the selected month. Verify validation prevents submission
of zero or missing amount.

**Acceptance Scenarios**:

1. **Given** the dashboard is open, **When** the user clicks "Add Income", **Then** a form appears with fields for amount, date, description (optional), and income source (optional dropdown).
2. **Given** a valid amount and date are entered, **When** the user submits, **Then** the income is saved, the form closes, and the dashboard refreshes.
3. **Given** the amount is zero or missing, **When** the user attempts to submit, **Then** a validation error is shown and the entry is not created.
4. **Given** the user cancels the form, **When** the form closes, **Then** no data is saved.

---

### User Story 3 - Select a Category When Adding an Expense (Priority: P3)

As a user, I want to assign a category to an expense when I enter it, so that the spending
breakdown and budget tracking sections of the dashboard reflect my categorised spending immediately
after entry.

**Why this priority**: Uncategorised bills appear in totals but not in the category breakdown or
budget status sections. Category assignment at entry time is important for the dashboard to be
useful, but the bill can still be saved without a category (making this P3, not P1).

**Independent Test**: Open "Add Expense", select a category from the dropdown, submit — verify
the category breakdown section shows the new amount under the correct category. Verify the
dropdown lists only the categories that exist in the system.

**Acceptance Scenarios**:

1. **Given** categories exist in the system, **When** the user opens "Add Expense", **Then** the category dropdown lists all available categories.
2. **Given** the user selects a category and saves the expense, **When** the dashboard refreshes, **Then** the selected category appears in the spending breakdown with the correct amount.
3. **Given** no categories exist, **When** the user opens "Add Expense", **Then** the category field is still present but shows an empty list; the user may still save the expense without a category.

---

### Edge Cases

- What happens if the backend is unreachable when the user submits? The form shows an error message ("Could not save — please try again") and stays open so the user does not lose their input.
- What happens if the user enters a future date? The date is accepted — no restriction on future-dated entries.
- What happens if the user enters a date in a different month from the one currently displayed? The entry is saved correctly; a notice informs the user that the entry is in a different month and the dashboard will show it when that month is selected.
- What happens while the form is submitting? The submit button is disabled to prevent duplicate submissions; a loading state is shown.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The dashboard MUST provide an "Add Expense" action that opens a form for entering a bill with: amount (required, must be > 0), date (required, defaults to today), description (optional free text), category (optional, selected from existing categories).
- **FR-002**: The dashboard MUST provide an "Add Income" action that opens a form for entering an income entry with: amount (required, must be > 0), date (required, defaults to today), description (optional free text), source (optional, selected from a fixed list of income sources).
- **FR-003**: On successful submission of either form, the dashboard MUST automatically refresh all sections to reflect the new entry without requiring a manual page reload.
- **FR-004**: Both forms MUST validate that the amount is greater than zero before submission, displaying a clear inline error message if not.
- **FR-005**: Both forms MUST default the date field to today's date when opened.
- **FR-006**: The category dropdown in the expense form MUST be populated from the existing categories in the system. If no categories exist, the field remains visible but empty and the expense may still be saved without one.
- **FR-007**: While a form is being submitted, the submit button MUST be disabled to prevent duplicate entries.
- **FR-008**: If the backend returns an error during submission, the form MUST remain open with the user's data intact and display an error message.
- **FR-009**: Both forms MUST be dismissible (Cancel button or Escape key) without saving any data.

### Key Entities

- **BillEntry**: The data submitted when recording an expense. Key fields: amount (positive decimal), date (calendar date), description (optional text), categoryId (optional, references an existing category).
- **IncomeEntry**: The data submitted when recording an income. Key fields: amount (positive decimal), date (calendar date), description (optional text), source (optional, from a fixed list: SALARY, FREELANCE, INVESTMENT, RENTAL, GIFT, OTHER).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can record a new expense from the dashboard to seeing it reflected in the summary in 3 steps or fewer (open form → fill → submit).
- **SC-002**: A user can record a new income entry in 3 steps or fewer.
- **SC-003**: 100% of submitted entries with a valid amount and date are saved and appear in the dashboard within one refresh cycle after submission.
- **SC-004**: Invalid submissions (zero or missing amount) are rejected 100% of the time with a visible error message before any network request is made.

## Assumptions

- Entry forms are presented as modal dialogs overlaid on the dashboard; no separate page or route is needed.
- The income source options (SALARY, FREELANCE, etc.) are a fixed frontend list matching the existing `IncomeSource` enum in the backend — no API call is needed to fetch them.
- Category options for the expense form are fetched from the existing `GET /api/v1/categories` endpoint (already called by the dashboard on load); the cached list is reused, not re-fetched on form open.
- The forms do not support editing or deleting existing entries — that is a separate future feature (Bill & Income Update/Delete).
- No file attachments or receipt uploads are in scope.
- The dashboard refreshes all sections after a successful submission by re-calling the same data hooks; no optimistic UI updates are implemented.
