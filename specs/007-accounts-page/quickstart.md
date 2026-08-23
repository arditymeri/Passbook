# Quickstart: Accounts Page

**Feature**: 007-accounts-page
**Date**: 2026-08-23

## Prerequisites

- Docker Compose stack running: `docker-compose up` (or `./mvnw -pl Launcher spring-boot:run` with Postgres/Kafka up separately)
- Frontend dev server: `cd frontend && npm run dev`

## Backend: exercising the derived balance

1. Create an account with a starting balance:

   ```
   POST /api/v1/accounts
   { "name": "ING Checking", "type": "CHECKING", "currencies": ["EUR"], "defaultCurrency": "EUR", "balance": 1000 }
   ```

   Response `balance` is `1000` (no linked transactions yet, so derived == starting).

2. Create a bill linked to that account:

   ```
   POST /api/v1/createBill
   { "amount": 50, "time": "2026-08-23T10:00:00Z", "description": "Groceries", "accountId": "<account id>" }
   ```

3. Create an income linked to the same account:

   ```
   POST /api/v1/incomes
   { "amount": 200, "time": "2026-08-23T09:00:00Z", "accountId": "<account id>" }
   ```

4. Re-fetch the account:

   ```
   GET /api/v1/accounts/<account id>
   ```

   **Expected `balance`**: `1000 − 50 + 200 = 1150`.

**Error cases**:
- Creating a bill/income with no `accountId` → succeeds exactly as before this feature; no
  account's balance changes.
- Fetching an account with no linked bills/incomes → `balance` equals its starting balance exactly.

## Frontend: exercising the UI

1. Open `http://localhost:5173`.
2. Click **Accounts** in the toolbar (new button, next to **Categories**).
3. If no accounts exist, the create form opens automatically (same behaviour as the Categories page).
4. Create an account (name, type, currency required; starting balance and institution optional).
5. Confirm the new account appears in the list immediately, with its balance formatted in its
   currency.
6. Go back to the dashboard, open **+ Add Expense**, and confirm an "Account" dropdown now appears
   with the account you just created (plus a "No account" default). Submit a bill against it.
7. Return to **Accounts** and confirm the account's balance decreased by the bill amount.
8. Repeat with **+ Add Income** and confirm the balance increases accordingly.
9. Use the type filter (`ToggleButtonGroup`) to narrow the account list to a single type.

## Swagger UI

```
http://localhost:8080/swagger-ui.html
```

Look for the **AccountGetController**, **AccountCreateController**, and **BillCreateController**
tags — the `bill` schema will now show the new optional `accountId` field.
