# Quickstart: Transaction Import

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`).

## Setup

1. Create an account (e.g. "Checking") and at least one expense category whose name matches a
   description you'll use below (e.g. a "Groceries" category, and a recurring "Netflix" bill
   already recorded once, so category suggestion has something to match against).
2. Prepare a CSV file with a header row and a few data rows, e.g.:
   ```
   date,description,amount
   2026-08-01,Netflix,-15.99
   2026-08-03,Groceries,-64.20
   2026-08-05,Salary,2500.00
   2026-08-99,Bad Row,not-a-number
   ```

## Scenario 1 — Import without manual entry (US1)

1. Open the Import dialog, choose the CSV file and the "Checking" account.
2. **Expected**: a preview lists the Netflix, Groceries, and Salary rows with their date,
   description, amount, and BILL/INCOME direction; the "Bad Row" line is flagged as unparseable
   and excluded, without preventing the other three from being reviewable (US1.3).
3. Confirm the import.
4. **Expected**: all three valid transactions now appear in "Checking"'s history with matching
   date, description, and amount (US1.1).
5. Open the Import dialog again with the same file but click Cancel instead of confirming.
6. **Expected**: no new transaction is created (US1.2).

## Scenario 2 — Re-upload doesn't double-count (US2)

1. Re-upload the exact same CSV file against the same account.
2. **Expected**: the Netflix, Groceries, and Salary rows are each flagged as a likely duplicate
   and excluded from the import by default (US2.1).
3. Explicitly include one flagged row anyway and confirm.
4. **Expected**: only that one row is created; the others remain excluded (US2.2).

## Scenario 3 — Correct a row before saving (US3)

1. Upload a fresh CSV with a row whose description doesn't match any category.
2. In the review, assign it a category manually.
3. Confirm.
4. **Expected**: the created transaction has the manually assigned category, not left blank
   (US3.1).
5. Upload again, this time excluding one specific valid row before confirming.
6. **Expected**: every other row is created except the excluded one (US3.2).

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up`, consistent with every prior feature (007-016). This walkthrough should be
executed manually once implementation lands in an environment with Docker available.
