# Quickstart: Verifying Net Worth Trend Manually

Prerequisites: full stack running (`docker-compose up`, frontend `npm run dev`), at least two
accounts with a mix of bills and income spanning several past months.

## 1. See current total net worth (US1)

1. Open the dashboard. Confirm a total net worth figure is shown prominently, equal to the sum of
   every account's current balance (cross-check against the Accounts page).
2. Record a new bill or income. Return to the dashboard and confirm the total updates without any
   extra action.
3. On a fresh account with no accounts yet, confirm net worth shows as zero with a clear
   "nothing to total yet" indication, not an error.

## 2. See the trend over recent months (US2)

1. With transaction history spanning several months, view the net worth trend.
2. Confirm it shows one point per recent month, and that the values rise/fall in a way that
   matches known account activity (e.g. a month with a large bill should show a dip).
3. Correct a past transaction's amount (008's correct flow). Reload the dashboard and confirm the
   affected month's point in the trend reflects the corrected value, not the original.

## 3. Adjust the time range (US3)

1. Switch the range from the default to a shorter option (e.g. 3 months). Confirm the trend now
   shows only that many recent months.
2. Switch to a longer option (e.g. 12 months). Confirm it extends further back, with every
   already-shown point's value unchanged from before.

## 4. Edge cases

1. An account created without any transactions yet: confirm the trend still renders for every
   period, including ones before the account had any activity (its starting balance carried
   through, per the spec's Assumptions — not excluded or shown as a gap).
2. A month with zero transactions across every account: confirm that point shows the same value as
   the point before it, not a zero or a gap in the chart.
