# Feature Specification: Accounts Page

**Feature Branch**: `007-accounts-page`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "add an account page (list + create mirroring the categories page pattern from 005). Show account balances. Let Add Bill and Add income forms pick an account"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View All Accounts and Their Balances (Priority: P1)

A user navigates to the Accounts section of the app and sees a list of all existing accounts, including their name, type, institution, and current balance. If no accounts exist yet, a clear empty state is shown with a prompt to add the first one.

**Why this priority**: Viewing accounts and balances is the foundation of the feature — it's the core value the user asked for ("show account balances") and gives context before creating new entries or linking transactions.

**Independent Test**: Can be fully tested by navigating to the Accounts page and observing the list — delivers immediate value by surfacing the user's financial accounts and their current balances.

**Acceptance Scenarios**:

1. **Given** accounts exist in the system, **When** the user opens the Accounts page, **Then** all accounts are displayed with their name, type badge, institution (if set), and current balance formatted in the account's default currency.
2. **Given** an account has a starting balance and one or more linked bills or incomes, **When** the user views the account list, **Then** the displayed balance equals the starting balance plus the sum of linked incomes minus the sum of linked bills.
3. **Given** an account's derived balance is negative, **When** the user views the list, **Then** that account's balance is visually distinguished from zero/positive-balance accounts (e.g., colour-coded).
4. **Given** no accounts exist, **When** the user opens the Accounts page, **Then** an empty state message is shown with a visible call-to-action to add the first account.
5. **Given** the page is loading, **When** data is being fetched, **Then** a loading indicator is shown until data is ready.
6. **Given** the backend is unreachable, **When** the user opens the Accounts page, **Then** a clear error message is displayed.

---

### User Story 2 - Create a New Account (Priority: P1)

A user fills in and submits a form to create a new account. Required fields are name, type, and at least one currency; starting balance and institution are optional. On success, the new account appears immediately in the list.

**Why this priority**: Creating accounts is the core action of this feature. Without it the list is read-only and has no practical value for a new user.

**Independent Test**: Can be fully tested by submitting the create form and verifying the new item appears in the list with the correct balance — delivers direct user value by enabling the user to organise their finances by account.

**Acceptance Scenarios**:

1. **Given** the user is on the Accounts page, **When** they open the create form and submit with a valid name, type, and currency, **Then** the new account is added and visible in the list without a full page reload.
2. **Given** the user submits the form with the name field empty, **When** validation runs, **Then** an error message is shown next to the name field and the form is not submitted.
3. **Given** the user submits the form without selecting a type, **When** validation runs, **Then** an error message is shown next to the type field and the form is not submitted.
4. **Given** the user submits the form without selecting a currency, **When** validation runs, **Then** an error message is shown next to the currency field and the form is not submitted.
5. **Given** the user provides an optional starting balance, **When** the account is created, **Then** that starting balance is stored and reflected in the list as the account's current balance (since it has no linked transactions yet).
6. **Given** the user provides an optional institution name, **When** the account is created, **Then** the institution is stored and reflected in the list.

---

### User Story 3 - Select an Account When Recording a Bill or Income (Priority: P2)

A user adding a bill or an income entry can optionally choose which account the transaction belongs to, from a list of their existing accounts.

**Why this priority**: Tying transactions to accounts is the payoff of having accounts at all — linking a bill or income to an account directly changes that account's derived balance, which is the number the user actually cares about. It is placed after account viewing/creation because an account must exist before it can be selected.

**Independent Test**: Can be fully tested by opening the Add Bill or Add Income form, selecting an account, submitting, and confirming both that the transaction was created without error and that the account's balance on the Accounts page reflects the change.

**Acceptance Scenarios**:

1. **Given** one or more accounts exist, **When** the user opens the Add Bill form, **Then** an account selector is present listing all existing accounts by name, plus a "None" option.
2. **Given** one or more accounts exist, **When** the user opens the Add Income form, **Then** an account selector is present listing all existing accounts by name, plus a "None" option.
3. **Given** the user selects an account and submits a valid bill, **When** the bill is created, **Then** it is associated with the selected account and that account's balance on the Accounts page decreases by the bill amount.
4. **Given** the user selects an account and submits a valid income, **When** the income is created, **Then** it is associated with the selected account and that account's balance on the Accounts page increases by the income amount.
5. **Given** the user leaves the account selector on "None" and submits a valid bill or income, **When** the entry is created, **Then** it is created successfully with no account association and no account's balance changes, exactly as before this feature existed.
6. **Given** no accounts exist yet, **When** the user opens the Add Bill or Add Income form, **Then** the account selector shows only the "None" option and does not block form submission.

---

### User Story 4 - Filter Accounts by Type (Priority: P3)

A user filters the account list to show only accounts of a chosen type (e.g., only Credit Cards) so they can quickly find what they're looking for.

**Why this priority**: Filtering is a convenience feature that becomes more useful once a user has accumulated several accounts across types. It does not block core usage.

**Independent Test**: Can be fully tested by selecting a type filter and verifying the displayed list narrows accordingly.

**Acceptance Scenarios**:

1. **Given** a mixed list of accounts, **When** the user selects a specific type filter, **Then** only accounts of that type are displayed.
2. **Given** a filter is active, **When** the user clears the filter, **Then** all accounts are shown again.

---

### Edge Cases

- What happens when a very long account name or institution name is submitted? It should be truncated gracefully in the list view without breaking the layout.
- What happens when an account has no linked transactions and no starting balance? Its balance should display as "0" (formatted in currency), not blank space.
- What happens when the form is submitted multiple times in quick succession? Duplicate submissions should be prevented (e.g., the submit button is disabled after the first click until the response arrives).
- What happens when an account referenced by a bill or income is later deleted through another channel (e.g., API)? Out of scope for this feature — account deletion is not part of this UI; the derived-balance calculation simply excludes accounts that no longer exist.
- What happens when an account has multiple supported currencies? The list view displays the derived balance in the account's designated default currency only; bills and incomes linked to the account are assumed to be in that same currency (see Assumptions).
- What happens to accounts and their balances that existed before this feature (i.e., bills/incomes created without any account link)? They are unaffected — an account's derived balance only reflects bills/incomes explicitly linked to it going forward, plus its starting balance.
- What happens if a bill or income is created against an account but the amount would take a Cash or Checking account's balance negative? The system still creates the transaction and reflects the negative balance (no overdraft prevention in this feature).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to view a list of all accounts showing name, type, institution (if set), and current balance.
- **FR-002**: Users MUST be able to open a form to create a new account from the Accounts page.
- **FR-003**: The create form MUST require name, type, and at least one currency (with a designated default currency) before allowing submission.
- **FR-004**: The create form MUST offer Checking, Savings, Credit Card, Cash, and Investment as selectable account type options.
- **FR-005**: The create form MUST allow users to optionally specify a starting balance and an institution name.
- **FR-006**: After successful creation, the new account MUST appear in the list without requiring a manual page refresh.
- **FR-007**: The system MUST display field-level validation errors for missing required fields before submitting.
- **FR-008**: The system MUST display a user-friendly error when the server rejects the account creation request.
- **FR-009**: The system MUST display an empty state with a call-to-action when no accounts exist.
- **FR-010**: Each account's balance MUST be displayed formatted as currency, using the account's default currency.
- **FR-011**: Accounts with a negative balance MUST be visually distinguished from accounts with a zero or positive balance.
- **FR-012**: Users MUST be able to filter the account list by account type.
- **FR-013**: The Add Bill form MUST allow users to optionally select an existing account to associate with the bill being created.
- **FR-014**: The Add Income form MUST allow users to optionally select an existing account to associate with the income being created.
- **FR-015**: The account selector in the Add Bill and Add Income forms MUST include a "None" option and MUST NOT require a selection to submit the form.
- **FR-016**: Bills and incomes created without an account selected MUST continue to behave exactly as they did before this feature (no account association, no errors).
- **FR-017**: An account's displayed balance MUST be automatically derived as: starting balance (set at creation) plus the sum of all incomes linked to the account, minus the sum of all bills linked to the account. The balance MUST NOT be a separately stored value that a user can edit independently of its starting balance and linked transactions.
- **FR-018**: When a bill or income is created with an account selected, the affected account's derived balance MUST reflect that transaction the next time the Accounts page (or list) is viewed, with no manual recalculation step required from the user.
- **FR-019**: Derived balance calculations MUST treat every linked bill or income as being in the account's default currency (see Assumptions) — no currency conversion is performed in this feature.

### Key Entities

- **Account**: Represents a financial account the user tracks money in/through. Key attributes: name, type (Checking / Savings / Credit Card / Cash / Investment), supported currencies, default currency, balance, institution (optional).
- **Bill / Income**: Existing transaction entities, extended with an optional reference to the Account they belong to.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can view all existing accounts and their current (derived) balances within 2 seconds of navigating to the Accounts page under normal network conditions.
- **SC-002**: A user can complete the create-account flow (open form → fill fields → submit) in under 60 seconds.
- **SC-003**: 100% of required-field validation errors are surfaced inline before a network request is made.
- **SC-004**: After successful creation, the new account appears in the list within 1 second without a full page reload, showing its starting balance as the current balance.
- **SC-005**: The feature works correctly with 0 accounts (empty state), 1 account, and 50+ accounts, and with 0, 1, and 50+ transactions linked to a given account.
- **SC-006**: Adding a bill or income with an account selected takes no longer than adding one without an account (no added friction to the existing under-60-seconds flow).
- **SC-007**: 100% of bills and incomes created without selecting an account continue to succeed with no behavioural change from before this feature, and do not affect any account's balance.
- **SC-008**: For any account, its displayed balance always exactly equals starting balance plus linked incomes minus linked bills — verified with zero discrepancy across all tested account/transaction combinations.

## Assumptions

- The app already has navigation in place; an "Accounts" entry will be added to the existing nav structure, alongside "Categories".
- A single user context is assumed — no multi-user or role-based access control is in scope.
- Mirroring the 005 Categories pattern, this feature covers list + create only; editing or deleting accounts through the UI is out of scope for this spec (the backend already supports it and may be exposed in a future feature).
- Associating a bill or income with an account is optional; the account selector defaults to "None" and existing (pre-feature) creation behaviour is fully preserved when no account is chosen.
- The existing account, bill, and income backend endpoints are assumed to be extended as needed to support the account-linking requirement and derived-balance calculation; no specific API shape is prescribed by this spec.
- Balance derivation assumes bills and incomes are append-only (cannot currently be edited or deleted through any existing feature), so the derivation is a simple running sum with no reversal/recalculation-on-delete scenario to handle in this feature.
- All bills and incomes linked to an account are assumed to be denominated in that account's default currency; cross-currency accounts and conversion are out of scope for this feature.
- Mobile responsiveness is desirable but not a hard requirement for v1.
