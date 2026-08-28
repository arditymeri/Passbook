# Feature Specification: Setup Templates for Categories and Accounts

**Feature Branch**: `013-setup-templates`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Setup templates for categories and accounts. Let a new user quickly get started instead of manually creating every category and account one by one. Offer a small set of predefined starter templates (e.g. \"Personal Finance Starter\") bundling common categories (Groceries, Rent, Utilities, Entertainment, Salary, etc.) and common accounts (Checking, Savings, Credit Card). The user can preview a template's contents, pick which items to apply, and apply it in one action -- creating whichever categories/accounts don't already exist and skipping/reporting ones that do (since category and account names are already unique). Builds on the existing category/account creation services and the app's onboarding flow (AccountsPage/CategoriesPage already prompt to add the first account/category)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Apply a Starter Template in One Action (Priority: P1)

A new user, faced with an empty categories/accounts list, doesn't want to create "Groceries",
"Rent", "Utilities", "Checking", "Savings", and everything else one form submission at a time.
They pick a starter template and apply it, and every category and account in that template is
created for them at once.

**Why this priority**: This is the entire point of the feature — removing the tedious,
one-item-at-a-time setup grind that makes a brand-new install feel like a chore before it's
useful.

**Independent Test**: Can be fully tested by selecting a template and applying it, then confirming
every category and account listed in that template now exists.

**Acceptance Scenarios**:

1. **Given** a user with no categories or accounts yet, **When** they apply the "Personal Finance
   Starter" template, **Then** every category and account it bundles now appears in their
   categories and accounts lists.
2. **Given** the template has been applied, **When** the user adds a bill or income afterward,
   **Then** the newly created categories and accounts are selectable exactly like any manually
   created one.

---

### User Story 2 - Preview Before Applying (Priority: P1)

Before committing to a template, a user wants to see exactly what it will create — which
categories, which accounts — so there are no surprises in their own finance data.

**Why this priority**: Equal priority to applying itself: a personal finance app is exactly the
kind of tool where a user should never be asked to bulk-create data blind. Preview and apply are
two halves of one usable action.

**Independent Test**: Can be fully tested by opening a template's preview and confirming it lists
every category (with type) and account (with type) it would create, without anything actually
being created until the user applies it.

**Acceptance Scenarios**:

1. **Given** a user is browsing available templates, **When** they open a template's preview,
   **Then** they see the full list of categories and accounts it contains, with no data created
   yet.
2. **Given** a user has previewed a template but closes the preview without applying, **When** they
   check their categories/accounts lists, **Then** nothing has changed.

---

### User Story 3 - Skip Items That Already Exist (Priority: P2)

A user who already created a few categories or accounts (or who applies a second template sharing
some items with the first) applies a template. Items matching something they already have are
left alone — not duplicated, not errored on — and the rest are created.

**Why this priority**: Makes the feature safe to use more than once and safe for users who aren't
starting from a completely empty account, but it's a refinement on top of US1's core "apply and
create" behavior rather than a prerequisite for it.

**Independent Test**: Can be fully tested by pre-creating one category whose name matches a
template item, applying the template, and confirming that item is skipped (no duplicate, no
error) while every other item in the template is created, with the result clearly showing what
was skipped versus created.

**Acceptance Scenarios**:

1. **Given** a category already exists with the same name as one in the template, **When** the
   user applies the template, **Then** that category is not duplicated and the result indicates it
   was skipped because it already existed.
2. **Given** every item in a template already exists, **When** the user applies it, **Then**
   nothing new is created and the result clearly states nothing new was added, rather than showing
   an error.

---

### User Story 4 - Pick Which Items to Apply (Priority: P3)

A user likes most of a template but not all of it — they want "Groceries" and "Checking" but not
"Investment" — so they deselect the items they don't want before applying.

**Why this priority**: A nice refinement for users who want partial customization, but the
all-or-nothing apply from US1 already delivers the feature's core value without it.

**Independent Test**: Can be fully tested by deselecting one item in a template's preview, applying
it, and confirming only the still-selected items were created.

**Acceptance Scenarios**:

1. **Given** a user is previewing a template, **When** they deselect one specific item, **Then**
   applying the template does not create that item, while every still-selected item is created.
2. **Given** a user deselects every item in a template, **When** they attempt to apply it, **Then**
   the system makes no changes and clearly communicates that nothing is selected, rather than
   silently doing nothing.

---

### Edge Cases

- Applying a template when every one of its items already exists: nothing is created, and the
  result clearly states so rather than reading as an error.
- A template item's name matches an existing category/account of a different type (e.g. the
  template offers an EXPENSE category named "Entertainment" but the user already has a BOTH
  category with that exact name): still treated as a name match and skipped — the existing item is
  never modified, and the template item is simply not created.
- A user applies two different templates that share an item by name: the second application skips
  that item since the first application already created it.
- Deselecting every item in a template's preview: applying is prevented or clearly shown to be a
  no-op, never a silent success with zero results.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a small set of predefined starter templates, each bundling a set
  of category items (name and type) and account items (name and type).
- **FR-002**: Users MUST be able to preview a template's full contents — every category and account
  it would create — before applying it.
- **FR-003**: Users MUST be able to apply a template in one action, creating every item in the
  template that doesn't already exist by name.
- **FR-004**: System MUST NOT create a duplicate category or account when a template item's name
  matches one that already exists; it MUST skip that item instead.
- **FR-005**: After applying a template, the system MUST report which items were created and which
  were skipped because they already existed.
- **FR-006**: Users MUST be able to deselect individual items within a template before applying, so
  that only the selected items are created.
- **FR-007**: Applying a template with no items selected MUST make no changes, and the system MUST
  clearly communicate that nothing was selected rather than silently succeeding with zero results.
- **FR-008**: The setup-template feature MUST be reachable from the app's existing
  category/account management screens, not require a separate flow disconnected from where
  categories and accounts are already managed.
- **FR-009**: Every category or account created by applying a template MUST behave identically
  afterward to one created manually — no special template-derived flag, status, or validation
  path.

### Key Entities

- **Setup Template**: A named, predefined bundle of category items and account items a user can
  preview and apply. Fixed and system-provided in this feature's scope — not user-created or
  user-editable.
- **Template Category Item**: A category's name and type as offered within a template.
- **Template Account Item**: An account's name, type, and default currency as offered within a
  template.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can go from zero categories/accounts to a fully usable starter set in
  under 30 seconds, instead of creating each one individually through separate forms.
- **SC-002**: Applying a template never creates a duplicate category or account, across all tested
  scenarios including partial overlap with existing data.
- **SC-003**: Users can see a template's full contents before applying it, with no category or
  account appearing afterward that wasn't shown in the preview.
- **SC-004**: Re-applying the same template a second time, or applying a different template sharing
  items with an already-applied one, results in zero duplicate items and a clear report of what was
  skipped.

## Assumptions

- A small, fixed set of predefined templates ships with the app — starting with one well-rounded
  "Personal Finance Starter" template — rather than a user-authorable template system; more
  templates can be added later without changing this feature's design.
- Template items use reasonable defaults for fields the user doesn't specify: category items carry
  no color or parent category by default; account items default to a single currency (EUR,
  matching this app's existing display convention) as both the account's currency and its default
  currency, with no starting balance and no institution.
- "Already exists" matching is by exact name — the same uniqueness rule the app already enforces
  for both categories and accounts — not fuzzy or partial matching.
- This feature only ever creates categories and accounts; it never modifies or deletes an existing
  one, even when a template's suggested type differs from an existing item sharing its name — the
  existing item is left untouched and the template item is simply skipped.
- No new permission model or multi-user concept is introduced — the same single-user assumption
  the rest of the app already makes.
