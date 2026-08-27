# Feature Specification: Transaction Search & Filtering

**Feature Branch**: `012-transaction-search`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Transaction search and filtering. Let a user search and filter their transaction history by description text, category, account, date range, and amount range, so they can quickly find a specific transaction or narrow down the recent-transactions view without scrolling through everything. Builds on the existing transaction history/dashboard patterns already established by prior features (RecentTransactions, GetBillService/GetIncomeService)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a Transaction by Description (Priority: P1)

A user remembers roughly what a transaction was called — "Netflix", "rent", "freelance" — but not when it happened. They type a few characters into a search box and see every past bill or income transaction whose description contains that text, across their full history, not just the currently viewed month.

**Why this priority**: This is the core value of the feature and the most common reason someone would reach for it — finding one specific transaction fast. Every other filter is a refinement on top of this.

**Independent Test**: Can be fully tested by typing a partial description into the search box and confirming every matching transaction appears, including ones from months other than the one currently selected on the dashboard, and that non-matching transactions are excluded.

**Acceptance Scenarios**:

1. **Given** the user has transactions in several different months, **When** they search for a description that matches a transaction from three months ago, **Then** that transaction appears in the results even though it isn't in the currently selected month.
2. **Given** a search term that matches part of a transaction's description (not the whole thing), **When** the user searches, **Then** the transaction is still included (partial, case-insensitive match).
3. **Given** a search term that matches nothing, **When** the user searches, **Then** the results area shows a clear "no transactions found" message instead of an error or a blank screen.

---

### User Story 2 - Narrow Results by Category, Source, or Account (Priority: P1)

A user wants to see only their grocery spending, or only transactions on a particular account, without also typing a search term.

**Why this priority**: Structured filtering is just as fundamental as text search for "narrowing down the view" — the feature description calls out both use cases as first-class, and neither depends on the other to be useful.

**Independent Test**: Can be fully tested by selecting a category (for bills) or income source (for income) and/or an account filter, with no search text entered, and confirming only transactions matching every selected filter appear.

**Acceptance Scenarios**:

1. **Given** transactions across several categories, **When** the user filters by one category, **Then** only bills in that category appear (income transactions, which have no category, are excluded unless the user is filtering by income source instead).
2. **Given** transactions across several accounts, **When** the user filters by one account, **Then** only transactions recorded against that account appear.
3. **Given** both a category filter and an account filter are active, **When** the user views results, **Then** only transactions matching both at once appear (combined, not either-or).

---

### User Story 3 - Narrow Results by Date Range or Amount Range (Priority: P2)

A user wants to see only transactions from a specific week, or only ones above a certain amount — for example, checking every bill over €100 last quarter.

**Why this priority**: Valuable refinements once the basic search/category/account filtering already works, but less commonly the very first thing a user reaches for compared to US1/US2.

**Independent Test**: Can be fully tested by setting a date range and/or a minimum/maximum amount, with no other filters active, and confirming only transactions within those bounds appear.

**Acceptance Scenarios**:

1. **Given** transactions spanning many months, **When** the user sets a start and end date, **Then** only transactions dated within that range (inclusive) appear.
2. **Given** transactions of varying amounts, **When** the user sets a minimum amount, a maximum amount, or both, **Then** only transactions whose amount falls within those bounds appear.
3. **Given** a date range or amount range combined with a search term or category filter, **When** the user views results, **Then** every active filter applies together.

---

### User Story 4 - Filter by Transaction Type and Clear All Filters (Priority: P3)

A user wants to look at just their expenses (or just their income), and afterward wants to get back to the normal dashboard view in one click rather than resetting each filter individually.

**Why this priority**: A convenience on top of an already-functional search/filter experience — useful, but the feature delivers its core value (US1-US3) without it.

**Independent Test**: Can be fully tested by selecting "bills only" or "income only" and confirming the other type is excluded, and by activating several filters at once and confirming a single "clear filters" action removes all of them and restores the default view.

**Acceptance Scenarios**:

1. **Given** both bills and income exist, **When** the user filters by transaction type "bills only", **Then** no income transactions appear in the results (and vice versa for "income only").
2. **Given** several filters are active at once, **When** the user selects "clear filters", **Then** every filter resets and the view returns to the default (unfiltered, current-month) transaction list.

---

### Edge Cases

- No filters and no search text active: the view behaves exactly as the existing dashboard does today (current month's transactions, unfiltered).
- A search term containing special characters (e.g. punctuation): treated as literal text to match, not as a query syntax.
- A date range where the start date is after the end date, or an amount range where the minimum exceeds the maximum: treated as producing no results rather than an error, with the user able to correct it.
- A transaction that has been corrected: search and filters reflect its current corrected value; the original pre-correction row and its reversal never both appear as separate results.
- Filtering by category while also filtering by income source at the same time: since categories only apply to bills and sources only apply to income, this combination naturally yields no results — treated as a valid (if unhelpful) filter combination, not an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to search transactions by description text using a case-insensitive partial match.
- **FR-002**: Search MUST cover the user's full transaction history by default, not just the currently selected month.
- **FR-003**: Users MUST be able to filter bill transactions by category.
- **FR-004**: Users MUST be able to filter income transactions by income source.
- **FR-005**: Users MUST be able to filter transactions by account.
- **FR-006**: Users MUST be able to filter transactions by a date range (start date, end date, or both).
- **FR-007**: Users MUST be able to filter transactions by a minimum amount, a maximum amount, or both.
- **FR-008**: Users MUST be able to filter transactions by type (bills only, income only, or both).
- **FR-009**: System MUST combine every active filter using AND logic — a transaction must satisfy all currently active filters to appear in the results.
- **FR-010**: System MUST show a clear "no transactions found" message when the active filters and/or search term match nothing.
- **FR-011**: Users MUST be able to clear all active filters and search text in a single action, returning to the default view.
- **FR-012**: Search and filter results MUST reflect each transaction's current (corrected) value and show exactly one row per logical transaction — a corrected transaction's original row and its reversal MUST NOT both appear.
- **FR-013**: Results MUST be sorted by date, most recent first.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can locate a specific transaction from their full history by typing a partial description in under 5 seconds.
- **SC-002**: Filtering by category, income source, account, date range, amount range, or transaction type — alone or in any combination — narrows results correctly in 100% of tested combinations.
- **SC-003**: A user can clear all active filters and search text in a single action.
- **SC-004**: Search results never display a corrected transaction's stale pre-correction value or show a correction as a separate, duplicate entry.

## Assumptions

- Search text matching is a case-insensitive substring match on the transaction's description field, not a fuzzy or full-text search engine.
- All active filters combine with AND logic; there is no OR/complex query builder.
- No pagination is introduced for search results, consistent with the personal-scale (hundreds of rows) assumption used by prior features; this can be revisited if usage patterns show it's needed.
- Search and filtering read through the same correction-aware, reversal-hiding transaction view (`GetBillService`/`GetIncomeService`) that every other feature already uses to show "current" transaction values — no new data-access pattern is introduced.
- Search results update as the user types or changes a filter (no separate "search" button required), matching standard web app expectations.
