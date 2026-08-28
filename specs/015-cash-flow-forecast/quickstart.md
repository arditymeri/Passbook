# Quickstart: Cash Flow Forecast

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`).

## Setup

1. Create an account, e.g. "Checking" with a starting balance of €500.
2. Add and confirm a recurring MONTHLY bill against it — record two occurrences of a "Rent"
   bill, €800 each, roughly a month apart, against the "Checking" account, then confirm the
   proposed recurring series via the Recurring panel.
3. Optionally add and confirm a recurring INCOME series ("Salary", €1200) against the same
   account.

## Scenario 1 — Warning shown (US1)

1. Open the dashboard; locate the Cash Flow Forecast card for "Checking".
2. With only the €800 rent series confirmed and no offsetting income, the account is projected
   to go negative before the default 4-week window ends.
3. **Expected**: the account is clearly flagged at risk (e.g. a warning chip/color), matching
   FR-003 and acceptance scenario US1.1.

## Scenario 2 — No warning when income covers bills (US1)

1. With both the Rent (€800) and Salary (€1200) series confirmed against the same account,
   reload the forecast.
2. **Expected**: no warning is shown — the account stays positive throughout the window
   (US1.2).

## Scenario 3 — Already-negative account (US1)

1. Create a second account with a starting balance of -€50 and no recurring series.
2. **Expected**: flagged at risk immediately, identically to a future dip (US1.3).

## Scenario 4 — Day-by-day timeline (US2)

1. On the "Checking" forecast, expand/view the timeline.
2. **Expected**: each confirmed series' predicted occurrence appears in date order with its date
   and amount, and the projected balance visibly changes at each entry (US2.1).
3. Confirm a WEEKLY series and re-check: if its cadence puts more than one occurrence inside the
   window, **expected**: every occurrence appears, not just the first (US2.2).

## Scenario 5 — Correction reflected (US2)

1. Correct the most recent Rent occurrence's amount (e.g. €800 → €850) via the existing
   correct-transaction flow.
2. Reload the forecast.
3. **Expected**: the projected Rent entries now use €850, not the original €800 (US2.3, FR-007).

## Scenario 6 — Adjustable window (US3)

1. Change the forecast window from the default (4 weeks) to a shorter preset (2 weeks).
2. **Expected**: the timeline and any warning update to reflect only the nearer-term period
   (US3.1).
3. Switch to a longer preset (12 weeks) and confirm a risk not visible at 2/4 weeks becomes
   visible (US3.2, SC-003: recomputes within a couple of seconds).

## Scenario 7 — No confirmed series (Edge Case / FR-006)

1. Create a third account with no recurring series at all.
2. **Expected**: forecast shows a flat line at current balance for the whole window, no warning,
   no error (SC-004).

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up` or the `integration-tests` module, consistent with every prior feature
(007/009/010/011/012/013/014). This walkthrough should be executed manually once implementation
lands in an environment with Docker available.
