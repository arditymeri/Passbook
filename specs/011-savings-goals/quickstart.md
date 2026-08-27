# Quickstart: Verifying Savings Goals Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`), at least two
accounts available (one to fund a goal, one spare to test the one-goal-per-account rule).

## 1. Create a goal (US1)

1. Open the Goals page from the nav bar.
2. Create a goal named "Vacation Fund" with a target amount (e.g. €2000) and link it to an
   account. Leave the target date blank.
3. Confirm the goal appears in the list with that name, target amount, and linked account, and no
   pace indicator (no target date was set).
4. Try creating a second goal linked to the same account. Confirm it is rejected with a message
   explaining the account already funds a goal.

## 2. See progress at a glance (US2)

1. Record income/bills against the linked account so its balance sits below the target.
2. Reopen the Goals page. Confirm the goal shows the correct saved amount (matching the account's
   balance), percent complete, and remaining amount.
3. Record another transaction that changes the account's balance. Reopen the Goals page and
   confirm the numbers update without any extra action.
4. Record enough income to push the balance at or above the target amount. Confirm the goal is
   now clearly marked as achieved.

## 3. Pace warnings (US3)

1. Create a goal with a target date a few months out and a target amount well above the linked
   account's current balance. Confirm it shows as falling behind (little progress, most of the
   time already elapsed relative to nothing saved) or on pace, matching straight-line expectations
   for the amounts/dates used.
2. Create a goal with a target date already in the past and an unmet target amount. Confirm it is
   shown as overdue, not merely "falling behind."
3. Confirm a goal with no target date never shows a pace indicator, only saved/percent/remaining.

## 4. Manage a goal (US4)

1. Edit an existing goal's target amount and/or target date. Confirm the Goals page reflects the
   new values immediately.
2. Delete a goal. Confirm it disappears from the list, and that its linked account and that
   account's transaction history are unaffected (check the Accounts page and transaction history).

## 5. API contract check

1. Open `http://localhost:8080/swagger-ui.html`.
2. Confirm the `goalGet`, `goalAdd`, `goalUpdate`, and `goalDelete` tags are present and
   documented, matching the schemas in `contracts/goal-model.yaml`.
