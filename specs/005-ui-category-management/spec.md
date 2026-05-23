# Feature Specification: UI Category Management

**Feature Branch**: `005-ui-category-management`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "Add a UI feature for managing categories — allowing users to view the list of categories and create new ones via a form. The form should collect name (required), type (required, one of EXPENSE/INCOME/BOTH), color (optional), and parentCategoryId (optional). The backend POST /api/v1/categories and GET /api/v1/categories endpoints already exist."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View All Categories (Priority: P1)

A user navigates to the Categories section of the app and sees a list of all existing categories, including their name, type, and color. If no categories exist yet, a clear empty state is shown with a prompt to add the first one.

**Why this priority**: Viewing the category list is the foundation of all category management. Without it, the user has no context for what already exists before creating new entries.

**Independent Test**: Can be fully tested by navigating to the Categories page and observing the list — delivers immediate value by surfacing existing financial organization.

**Acceptance Scenarios**:

1. **Given** categories exist in the system, **When** the user opens the Categories page, **Then** all categories are displayed with their name, type badge, and color indicator.
2. **Given** no categories exist, **When** the user opens the Categories page, **Then** an empty state message is shown with a visible call-to-action to add the first category.
3. **Given** the page is loading, **When** data is being fetched, **Then** a loading indicator is shown until data is ready.
4. **Given** the backend is unreachable, **When** the user opens the Categories page, **Then** a clear error message is displayed.

---

### User Story 2 - Create a New Category (Priority: P1)

A user fills in and submits a form to create a new category. Required fields are name and type; color and parent category are optional. On success, the new category appears immediately in the list.

**Why this priority**: Creating categories is the core action of this feature. Without it the list is read-only and has no practical value for a new user.

**Independent Test**: Can be fully tested by submitting the create form and verifying the new item appears in the list — delivers direct user value by enabling financial organization.

**Acceptance Scenarios**:

1. **Given** the user is on the Categories page, **When** they open the create form and submit with a valid name and type, **Then** the new category is added and visible in the list without a full page reload.
2. **Given** the user submits the form with the name field empty, **When** validation runs, **Then** an error message is shown next to the name field and the form is not submitted.
3. **Given** the user submits the form without selecting a type, **When** validation runs, **Then** an error message is shown next to the type field and the form is not submitted.
4. **Given** the user provides a name that already exists, **When** the server responds with a conflict, **Then** an inline error message informs the user the name is already taken.
5. **Given** the user provides an optional color, **When** the category is created, **Then** the color is stored and reflected in the list.
6. **Given** the user selects an optional parent category, **When** the category is created, **Then** the parent relationship is reflected in the list.

---

### User Story 3 - Filter Categories by Type (Priority: P2)

A user filters the category list to show only EXPENSE, INCOME, or BOTH categories so they can quickly find what they're looking for.

**Why this priority**: Filtering is a convenience feature that becomes important once a user has accumulated many categories. It does not block core usage.

**Independent Test**: Can be fully tested by selecting a type filter and verifying the displayed list narrows accordingly.

**Acceptance Scenarios**:

1. **Given** a mixed list of categories, **When** the user selects "EXPENSE" filter, **Then** only expense categories are displayed.
2. **Given** a filter is active, **When** the user clears the filter, **Then** all categories are shown again.

---

### Edge Cases

- What happens when a very long category name is submitted? The name should be truncated gracefully in the list view without breaking the layout.
- What happens when the user selects a parent category that no longer exists? The field should either exclude deleted parents or show a recoverable validation error.
- What happens when the form is submitted multiple times in quick succession? Duplicate submissions should be prevented (e.g., the submit button is disabled after the first click until the response arrives).
- What if the color input receives an invalid value? The form should either restrict input (e.g., via a color picker) or validate the format before submission.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to view a list of all categories showing name, type, and color.
- **FR-002**: Users MUST be able to open a form to create a new category from the Categories page.
- **FR-003**: The create form MUST require name and type before allowing submission.
- **FR-004**: The create form MUST offer EXPENSE, INCOME, and BOTH as selectable type options.
- **FR-005**: The create form MUST allow users to optionally specify a color for the category.
- **FR-006**: The create form MUST allow users to optionally select a parent category from existing categories.
- **FR-007**: After successful creation, the new category MUST appear in the list without requiring a manual page refresh.
- **FR-008**: The system MUST display field-level validation errors for missing required fields before submitting.
- **FR-009**: The system MUST display a user-friendly error when the server rejects the request (e.g., duplicate name).
- **FR-010**: The system MUST display an empty state with a call-to-action when no categories exist.
- **FR-011**: Users MUST be able to filter the category list by type (EXPENSE, INCOME, BOTH).

### Key Entities

- **Category**: Represents a financial classification. Key attributes: name (unique), type (EXPENSE / INCOME / BOTH), color (optional display hint), parentCategoryId (optional reference to another category for hierarchical grouping).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can view all existing categories within 2 seconds of navigating to the Categories page under normal network conditions.
- **SC-002**: A user can complete the create-category flow (open form → fill fields → submit) in under 60 seconds.
- **SC-003**: 100% of required-field validation errors are surfaced inline before a network request is made.
- **SC-004**: After successful creation, the new category appears in the list within 1 second without a full page reload.
- **SC-005**: The feature works correctly with 0 categories (empty state), 1 category, and 50+ categories.

## Assumptions

- The app already has navigation in place; a "Categories" entry will be added to the existing nav structure.
- A single user context is assumed — no multi-user or role-based access control is in scope.
- The color field accepts a hex color string (e.g., `#FF5733`); a color picker is the preferred input but a text field is acceptable for v1.
- Parent category selection is limited to one level of nesting (no deep hierarchies in this spec).
- The existing `GET /api/v1/categories` and `POST /api/v1/categories` backend endpoints are stable and will not change as part of this feature.
- Mobile responsiveness is desirable but not a hard requirement for v1.
