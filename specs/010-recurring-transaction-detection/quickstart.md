# Quickstart: Verifying Recurring Transaction Detection Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`), at least one
EXPENSE or BOTH category available for recording bills.

## 1. Detect a new recurring series (US2)

1. Record three bills with the same category, the same description (e.g. "Netflix"), and similar
   amounts (e.g. €15.99 each), one month apart in `time`.
2. Open the dashboard's recurring proposals list (or trigger detection).
3. Confirm a proposal for "Netflix" appears, showing the category, description, and detected
   monthly cadence.
4. Confirm the proposal.

## 2. See it in the Upcoming view (US1)

1. After confirming, open the dashboard.
2. Confirm the "Upcoming" section shows "Netflix" with a predicted date roughly one month after
   the latest recorded occurrence, and the predicted amount matching the last one.
3. Let the predicted date pass without recording a new occurrence (or manually test with a past
   target month). Confirm the item is shown as overdue, not silently removed.
4. Record a new "Netflix" bill matching the series. Confirm the Upcoming item's predicted date
   advances to the following cycle and it's no longer marked overdue.

## 3. Dismiss an incorrect proposal (US2 edge case)

1. Record two unrelated one-off bills that happen to share a category and description by
   coincidence (not fewer than the detection threshold, to trigger a proposal).
2. Confirm a proposal appears.
3. Dismiss it. Confirm it disappears and does not reappear after triggering detection again.

## 4. Price-change flagging (US3)

1. With a confirmed "Netflix" series whose last recorded amount was €15.99, record a new
   occurrence at €17.99.
2. Confirm a price-change alert appears showing €15.99 → €17.99 (+€2.00).
3. Record another occurrence within a few cents of €17.99. Confirm no alert appears for that one
   (within tolerance).

## 5. Stop tracking a confirmed series (FR-011)

1. Open the recurring series management list.
2. Find the confirmed "Netflix" series and stop tracking it.
3. Confirm it no longer appears in the Upcoming view or produces price-change alerts, while past
   "Netflix" bills remain untouched in transaction history.

## 6. Corrections interoperate correctly (008 interop)

1. Correct one of the "Netflix" bills' amounts via the existing correct-bill flow.
2. Confirm the series' predictions and any price-change comparison reflect the corrected amount,
   not the pre-correction one, and that the corrected bill is not counted as a second, separate
   occurrence.

## 7. API contract check

1. Open `http://localhost:8080/swagger-ui.html`.
2. Confirm the `recurringGet`, `recurringDetect`, `recurringConfirm`, and `recurringDismiss` tags
   are present and documented, matching the schemas in `contracts/recurring-model.yaml`.
