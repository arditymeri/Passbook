# Quickstart: Verifying Transaction Search & Filtering Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`), a mix of bills and
income recorded across at least two different months, at least two categories, at least one
non-`OTHER` income source, and at least two accounts.

## 1. Search by description across full history (US1)

1. On the dashboard, note the currently selected month (via `MonthNav`) has some transactions.
2. Navigate `MonthNav` to a month with no transactions, or leave it on a month that doesn't contain
   a transaction you know exists in an earlier month.
3. Type part of that earlier transaction's description into the search box.
4. Confirm the transaction appears in the results, even though it isn't in the currently selected
   month.
5. Search for text that matches nothing. Confirm a clear "No transactions found" message appears
   (not an error, not a blank area).
6. Clear the search box. Confirm the view returns to the normal month-scoped "Recent Transactions".

## 2. Filter by category / income source and account (US2)

1. With no search text, select a bill category in the "Category / Source" filter.
2. Confirm only bills in that category appear, and no income transactions appear.
3. Switch the filter to an income source instead. Confirm only income with that source appears.
4. Add an account filter on top. Confirm only transactions matching both the category/source filter
   and the account filter appear.

## 3. Filter by date range and amount range (US3)

1. Clear all filters. Set a start date and end date spanning a specific week.
2. Confirm only transactions dated within that range (inclusive) appear.
3. Clear the date range. Set a minimum amount (e.g. €100).
4. Confirm only transactions at or above that amount appear.
5. Combine the amount filter with a search term. Confirm both apply together.

## 4. Filter by transaction type and clear all filters (US4)

1. Clear all filters. Select "Bills only" in the type filter.
2. Confirm no income transactions appear.
3. Switch to "Income only". Confirm no bill transactions appear.
4. Activate several filters at once (search text + category + date range).
5. Click "Clear filters". Confirm every filter resets in one action and the view returns to the
   default month-scoped "Recent Transactions".

## 5. Corrections interoperate correctly (008 interop)

1. Correct one transaction's amount or description via the existing correct flow.
2. Search or filter in a way that would match the corrected value. Confirm the corrected
   transaction appears with its new value, as a single row (not duplicated, and not showing the
   stale pre-correction value).
