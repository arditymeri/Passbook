# Quickstart: Verifying Envelope Budgeting Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`), at least one
EXPENSE or BOTH category and one INCOME entry already recorded for the current month.

## 1. See the unallocated balance (US1)

1. Open the new **Budgeting** view from the top nav.
2. Confirm the header shows: total income to date, total allocated to date, and unallocated
   (income − allocated). With no allocations yet, unallocated should equal total income.

## 2. Assign income to a category (US2)

1. Pick a category of type EXPENSE or BOTH and assign, e.g., €400 for the current month.
2. Confirm: the category's envelope balance shows €400, and the header's unallocated figure
   decreases by €400.
3. Change the same category's amount to €300 for the same month (not a new entry).
4. Confirm: envelope balance is now €300, unallocated increased back by €100.
5. Confirm an INCOME-type category is not offered in the allocation picker at all.

## 3. Move money between categories (US3)

1. With two categories that each have a positive envelope balance (e.g., Dining Out €200,
   Groceries €300), open "Move money," move €50 from Dining Out to Groceries.
2. Confirm: Dining Out → €150, Groceries → €350, unallocated unchanged.
3. Attempt to move more than a category's current envelope balance (e.g., €1,000 from a €150
   envelope). Confirm the request is rejected with a message stating the available balance.

## 4. Carryover across a month boundary (resolved clarification)

1. Leave a category with a positive envelope balance at the end of the current month (assign more
   than its bills for the month).
2. Navigate the Budgeting view to next month.
3. Confirm that category's envelope balance already reflects last month's leftover, with no action
   taken — nothing resets to zero at the boundary.
4. Confirm the unallocated header figure for the new month also includes any leftover unallocated
   balance from before, on top of the new month's income.

## 5. Repeat a month's assignments (US4)

1. In a month with several categories assigned, open "Repeat last month" for the following month.
2. Confirm the preview lists every category from the source month with the amount that will be
   added, and flags any category that already has its own new assignment in the target month.
3. Confirm, and verify each listed category's envelope balance increased by exactly the previewed
   amount (a top-up on top of any carryover — not a reset).
4. Try repeating a month with zero allocations; confirm a clear "nothing to repeat" message and no
   change made.

## 6. Over-allocation flagging (US1 Scenario 4 / FR-007)

1. Assign more, across all categories combined, than total recorded income to date.
2. Confirm the unallocated figure goes negative and is visually distinguished (e.g. red) from a
   positive balance — the same treatment 007 uses for a negative account balance.

## 7. API contract check

1. Open `http://localhost:8080/swagger-ui.html`.
2. Confirm `budgetGet`'s `/budgets/status` response schema now includes `unallocated` and each
   entry includes `envelopeBalance`.
3. Confirm the new `budgetTransfer` (`POST /budgets/transfer`) and `budgetRepeat`
   (`POST /budgets/repeat`) tags are present and documented.
