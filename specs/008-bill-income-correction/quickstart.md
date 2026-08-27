# Quickstart: Bill/Income Correction Flow

**Feature**: 008-bill-income-correction
**Date**: 2026-08-23

## Prerequisites

- Docker Compose stack running: `docker-compose up`
- Frontend dev server: `cd frontend && npm run dev`
- At least one existing bill or income to correct/remove

## Backend: exercising a correction

1. Create a bill with a wrong amount:

   ```
   POST /api/v1/createBill
   { "amount": 40, "time": "2026-08-23T10:00:00Z", "description": "Groceries", "categoryId": "<cat id>" }
   ```

2. Correct it (the real amount was 45.50):

   ```
   PUT /api/v1/bills/<bill id>
   { "amount": 45.50, "time": "2026-08-23T10:00:00Z", "description": "Groceries", "categoryId": "<cat id>" }
   ```

   **Expected**: `200 OK` with a **new** bill id and `amount: 45.50`.

3. Re-fetch the original:

   ```
   GET /api/v1/bill/<original bill id>
   ```

   **Expected**: still returns `amount: 40` — completely unchanged.

4. List bills for the month:

   ```
   GET /api/v1/bills
   ```

   **Expected**: the original (id from step 1) does **not** appear; only the new corrected bill
   (id from step 2's response) appears, with `amount: 45.50` and `correctsTransactionId` pointing
   back to the original.

5. Check the monthly summary / category spend for that month — both should reflect `45.50`, not
   `40` and not `85.50` (i.e., no double-counting).

6. Get the correction history:

   ```
   GET /api/v1/bills/<corrected bill id>/history
   ```

   **Expected**: one entry — the original, with `amount: 40`.

## Backend: exercising a removal

1. Create a bill, then remove it:

   ```
   DELETE /api/v1/bills/<bill id>
   ```

   **Expected**: `204 No Content`.

2. `GET /api/v1/bills` — the removed bill does not appear. `GET /api/v1/bill/<id>` still returns its
   original (unchanged) data.

3. If the bill was linked to an account, `GET /api/v1/accounts/<account id>` should show a balance
   as if the bill never happened.

## Frontend: exercising the UI

1. Open `http://localhost:5173`, add a bill or income with a deliberately wrong value.
2. In Recent Transactions, open the row's action menu → **Correct**. Confirm the form opens
   pre-filled with the wrong values.
3. Fix the value and submit. Confirm the row updates to the corrected value, and the summary/
   category spend/budget status update without a page reload.
4. Open the same row's menu → **History**. Confirm the prior (wrong) value is listed.
5. Add another bill, then use its menu → **Remove**. Confirm a confirmation dialog appears before
   anything happens; confirm, and verify the row disappears entirely with no replacement.
6. If either transaction was linked to an account, check the Accounts page to confirm its balance
   reflects only the corrected/removed value.

## Swagger UI

```
http://localhost:8080/api/v1/swagger-ui.html
```

(The `/api/v1` prefix is the app's `server.servlet.context-path` — the bare
`http://localhost:8080/swagger-ui.html` is a 404.)

Look for the **billCorrection** and **incomeCorrection** tags — three endpoints each
(`PUT`, `DELETE`, `GET .../history`).
