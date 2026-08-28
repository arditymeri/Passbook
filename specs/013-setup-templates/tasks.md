---
description: "Task list for Setup Templates for Categories and Accounts"
---

# Tasks: Setup Templates for Categories and Accounts

**Input**: Design documents from `/specs/013-setup-templates/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (no contracts/ — no REST endpoint is added or changed, per plan.md)

**Tests**: Not included. Same reasoning as 012's tasks.md: this codebase's `frontend/` has never had
a test runner (no vitest/jest/testing-library dependency, no `*.test.ts` file anywhere), and this
feature adds no Domain code, so the Constitution's Test-First principle (VI, scoped to "financial
calculation and business-rule logic in the Domain module") does not apply. Verification is
`tsc --noEmit` plus the manual `quickstart.md` walkthrough (Polish phase).

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3 → US4).
The shared pieces every story needs — the template/result types, the static template data, the two
new API functions that treat 409 as a non-error "skipped" outcome, and the `applySetupTemplate()`
apply loop — are built once in Foundational. `SetupTemplateDialog.tsx` is then a single component
built incrementally across all four stories: US1 gives it a minimal "apply everything, show a
generic confirmation" flow; US2 adds a real preview step before applying; US3 replaces the generic
confirmation with the actual created/skipped report; US4 adds per-item checkboxes so the apply call
uses a real selection instead of "select all" (the same incremental-single-component pattern
`TransactionFilterBar` used across 012's stories).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure — this feature
touches only `frontend/src/`. No backend module (`Domain/`, `Application/`, `Infrastructure/`,
`integration-tests/`) is touched.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The template/result type shapes, the static template data, the 409-tolerant API
functions, and the apply loop every user story's UI work depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T001 [P] Add `TemplateCategoryItem` (`name`, `type: CategoryType`), `TemplateAccountItem` (`name`, `type: AccountType`), `SetupTemplate` (`id`, `name`, `description`, `categoryItems`, `accountItems`), and `ApplyTemplateResult` (`created: string[]`, `skipped: string[]`) to `frontend/src/types/index.ts`, per `data-model.md`
- [X] T002 [P] Create `frontend/src/data/setupTemplates.ts`: `SETUP_TEMPLATES: SetupTemplate[]` with one entry, `id: "personal-finance-starter"`, bundling common category items (Groceries EXPENSE, Rent EXPENSE, Utilities EXPENSE, Entertainment EXPENSE, Transportation EXPENSE, Salary INCOME) and common account items (Checking CHECKING, Savings SAVINGS, Credit Card CREDIT_CARD) (depends on T001)
- [X] T003 [P] Add `createCategoryIfMissing(req: CreateCategoryRequest): Promise<'created' | 'skipped'>` and `createAccountIfMissing(req: CreateAccountRequest): Promise<'created' | 'skipped'>` to `frontend/src/api/client.ts` — raw `fetch` calls that resolve `'skipped'` on a `409` response, `'created'` on `2xx`, and throw on anything else (matching every other function's error convention); does not modify the existing `createCategory`/`createAccount`
- [X] T004 Create `frontend/src/utils/applySetupTemplate.ts`: `applySetupTemplate(template: SetupTemplate, selectedKeys: Set<string>): Promise<ApplyTemplateResult>` — a sequential loop over `template.categoryItems` then `template.accountItems`, skipping items whose `"category:"+name`/`"account:"+name` key isn't in `selectedKeys`, calling `createCategoryIfMissing`/`createAccountIfMissing` (accounts always created with `currencies: ["EUR"], defaultCurrency: "EUR"`, no balance/institution, per `data-model.md`), and appending each item's name to `created`/`skipped` per its outcome, preserving the template's own item order (depends on T001, T003)

**Checkpoint**: Template data, the apply loop, and the 409-tolerant API functions all exist. User story UI work can now begin.

---

## Phase 2: User Story 1 - Apply a Starter Template in One Action (Priority: P1)

**Goal**: A user can apply a starter template and have every one of its items created in one
action.

**Independent Test**: Select a template and apply it; confirm every category and account it lists
now exists, and that a bill/income form can select them exactly like any manually created one.

### Implementation for User Story 1

- [X] T005 [US1] Create `frontend/src/components/SetupTemplateDialog.tsx`: a modal showing the (currently single) template's name and description with an "Apply" button; clicking it calls `applySetupTemplate(template, allKeysFor(template))` (a local helper building the full key set from every item, since no per-item selection exists yet) and shows a generic "Template applied" confirmation with a "Done" action that closes the dialog and calls an `onApplied` callback (depends on T002, T004)
- [X] T006 [US1] Modify `frontend/src/components/CategoriesPage.tsx`: add a "Use a starter template" button near the existing "+ Add Category" action, mount `SetupTemplateDialog`, and call the `useCategories` hook's `refresh()` in `onApplied` (depends on T005)
- [X] T007 [US1] Modify `frontend/src/components/AccountsPage.tsx`: same as T006, calling the `useAccounts` hook's `refresh()` in `onApplied` (depends on T005)

**Checkpoint**: Users can apply the starter template from either management page and see the results.

---

## Phase 3: User Story 2 - Preview Before Applying (Priority: P1)

**Goal**: A user sees a template's full contents — every category and account it would create,
with type — before applying it.

**Independent Test**: Open a template's preview; confirm it lists every category and account item
(with type) without creating anything; close without applying and confirm nothing changed.

### Implementation for User Story 2

- [ ] T008 [US2] Extend `frontend/src/components/SetupTemplateDialog.tsx`: before the "Apply" button, render the template's `categoryItems` and `accountItems` as two grouped lists (name + type for each), so the user reviews the full contents before the same T005 apply action runs (depends on T005)

**Checkpoint**: Users can review exactly what a template contains before committing to apply it.

---

## Phase 4: User Story 3 - Skip Items That Already Exist (Priority: P2)

**Goal**: After applying, the user sees exactly which items were created and which were skipped
because they already existed — never a silent duplicate, never an error.

**Independent Test**: Pre-create one category matching a template item, apply the template, and
confirm the result explicitly lists that item as skipped while every other item is listed as
created; re-apply and confirm everything is now reported as skipped with nothing created.

### Implementation for User Story 3

- [ ] T009 [US3] Extend `frontend/src/components/SetupTemplateDialog.tsx`: replace the generic "Template applied" confirmation from T005 with the actual `ApplyTemplateResult` — two lists, "Created" and "Skipped (already existed)", each showing the item names from the result; when both lists are non-empty this is the common case, and when `created` is empty the message makes clear nothing new was needed rather than reading as a failure (depends on T005, T008)

**Checkpoint**: Applying a template is now safe to repeat — every outcome is visible, nothing is ever silently duplicated.

---

## Phase 5: User Story 4 - Pick Which Items to Apply (Priority: P3)

**Goal**: A user can deselect specific items in a template's preview so only the items they want
are created.

**Independent Test**: Deselect one item in the preview, apply, and confirm only the still-selected
items were created; deselect every item and confirm Apply is disabled rather than allowed to
silently succeed with nothing created.

### Implementation for User Story 4

- [ ] T010 [US4] Extend `frontend/src/components/SetupTemplateDialog.tsx`: add a checkbox next to each item in the preview list from T008 (all checked by default), track selection in local state, pass the real selected-keys set into `applySetupTemplate()` instead of T005's `allKeysFor(template)` full-selection helper, and disable the "Apply" button when the selection is empty (FR-007) (depends on T008, T009)

**Checkpoint**: All four user stories are independently functional — the full preview/select/apply/report experience works end to end.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full verification once all stories are implemented

- [ ] T011 Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the new/modified files
- [ ] T012 Re-read `frontend/src/utils/applySetupTemplate.ts` against `data-model.md`'s pseudocode and confirm the key-prefix scheme, per-item outcome handling, and preserved item order all match exactly
- [ ] T013 Execute `specs/013-setup-templates/quickstart.md` end to end in the browser

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately. T001 and T003 can run in parallel; T002 depends on T001; T004 depends on T001 and T003 — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Foundational (T002, T004) — the natural first story, establishing `SetupTemplateDialog` and both pages' entry points every later story extends
- **User Story 2 (Phase 3)**: Depends on US1's dialog (T005) already existing — adds a preview step in front of the same apply action
- **User Story 3 (Phase 4)**: Depends on US1's dialog (T005) and US2's preview (T008) — replaces the post-apply confirmation with the real result
- **User Story 4 (Phase 5)**: Depends on US2's preview (T008) and US3's result view (T009) — adds selection state on top of both
- **Polish (Phase 6)**: Depends on all four user stories being complete

### User Story Dependencies

Like 012, priority order already matches dependency order here: each story extends the same one
component (`SetupTemplateDialog.tsx`) building on the previous story's work, so they must be built
in sequence (US1 → US2 → US3 → US4) even though each is independently *testable* once its own
task is done — see each phase's Independent Test.

### Parallel Opportunities

- Foundational: T001 and T003 in parallel (different files, no dependency on each other); T002 and T004 sequential after their respective dependencies
- T006 and T007 (US1, `CategoriesPage.tsx` and `AccountsPage.tsx`) touch different files and can run in parallel once T005 is done
- US2/US3/US4's single tasks (T008, T009, T010) all touch the same file (`SetupTemplateDialog.tsx`) and so must run sequentially relative to each other — file-based coordination, not a logical dependency beyond what's already noted

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Foundational
2. Complete Phase 2 (US1): apply the template in one action from either management page
3. Complete Phase 3 (US2): preview contents before applying
4. **STOP and VALIDATE**: from an empty account, apply the template and confirm every item now
   exists and is usable; open the preview and confirm it matches what actually got created
5. Add Phase 4 (US3) — created/skipped reporting — and Phase 5 (US4) — per-item selection — as
   refinements on top; neither is required for the feature to already deliver its core value

### Incremental Delivery

1. Foundational → template data, apply loop, and 409-tolerant API functions ready
2. US1 → one-click apply works from both pages; MVP core value delivered
3. US2 → full preview before applying
4. US3 → created/skipped reporting, safe to re-run
5. US4 → per-item selection
6. Polish → type-check, code review against data-model.md, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- No backend task exists in this file — `Domain/`, `Application/`, `Infrastructure/`, and
  `integration-tests/` are all untouched by this feature, per `plan.md`'s Constitution Check
- `applySetupTemplate()` is built once in Foundational (T004) and never changes across the four
  stories — only what selects its `selectedKeys` argument evolves (T005's "select everything"
  helper, replaced by T010's real checkbox state)
- `SetupTemplateDialog.tsx`'s Apply action needs in-flight/submitting state (disable the button
  while `applySetupTemplate()`'s promise is pending), the same edge case every prior
  create/edit form in this app already handles
