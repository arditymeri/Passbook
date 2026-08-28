# Quickstart: Verifying Setup Templates Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`).

## 1. Apply a template from an empty account (US1)

1. With no categories or accounts yet, open the Categories page (or Accounts page).
2. Click "Use a starter template".
3. Select "Personal Finance Starter" (if more than one template is offered) and apply it with
   every item selected.
4. Confirm every category and account the template lists now appears in the Categories and
   Accounts pages.
5. Add a bill/income and confirm the newly created categories/accounts are selectable exactly like
   any manually created one.

## 2. Preview before applying (US2)

1. Open the starter-template dialog again (or on a fresh browser profile).
2. Open a template's preview without clicking Apply yet.
3. Confirm every category (with type) and account (with type) it would create is listed.
4. Close the dialog without applying. Confirm nothing changed in the Categories/Accounts pages.

## 3. Skip items that already exist (US3)

1. Manually create a category whose name exactly matches one in the template (e.g. "Groceries").
2. Open the template dialog and apply the template with every item selected.
3. Confirm the result reports "Groceries" as skipped (already existed) and every other item as
   created, with no duplicate "Groceries" category appearing.
4. Apply the same template a second time. Confirm every item is now reported as skipped and
   nothing new is created.

## 4. Pick which items to apply (US4)

1. Open the template dialog's preview. Deselect one specific item (e.g. "Investment" account, if
   present).
2. Apply the template. Confirm that item was not created while every other selected item was.
3. Deselect every item. Confirm the Apply action is disabled (or clearly blocked) rather than
   silently succeeding with nothing created.

## 5. Reachable from both management screens (FR-008)

1. Confirm "Use a starter template" is reachable from the Categories page.
2. Confirm it's also reachable from the Accounts page, and that applying from either page creates
   both category and account items (not just the page-type-specific ones).
