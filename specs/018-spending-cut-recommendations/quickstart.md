# Quickstart: Spending Cut Recommendations

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`).

## Setup

1. Confirm three recurring bill series with clearly different monthly costs, e.g. "Rent" (€900,
   MONTHLY), "Netflix" (€8, MONTHLY), "Internet" (€45, MONTHLY) — record at least the detection
   threshold's worth of occurrences for each and confirm the proposed series via the Recurring
   panel (same setup used by feature 010/015's quickstarts).
2. Record a fourth series' occurrences with an amount that increases on its most recent
   occurrence versus its first (e.g. a "Gym" bill: €25, €25, €32) and confirm it.
3. Set a budget for a category (e.g. "Dining Out", €150/month) and record bills in that category
   totaling more than €150 in the most recently completed calendar month.
4. Record several occurrences in another category (e.g. "Groceries") with a clearly higher total
   in the current month than the previous month, to produce a trending-up mover.

## Scenario 1 — Recurring costs ranked by size (US1)

1. Open the Spending Cut Recommendations view.
2. **Expected**: Rent (€900), Internet (€45), Netflix (€8) [and Gym, once confirmed] appear
   ordered highest to lowest monthly cost, with a total monthly recurring spend equal to their
   sum (US1.1, FR-001–003).
3. Leave one detected series in "proposed" status (don't confirm it).
4. **Expected**: it does not appear in the ranked list (US1.2, FR-001 scoped to CONFIRMED only).

## Scenario 2 — Tag transactions by necessity (US2)

1. Open an individual bill transaction (e.g. one "Dining Out" entry) and tag it "Unnecessary".
2. **Expected**: the tag is saved and visible wherever that transaction is shown (US2.1, FR-004,
   FR-006).
3. Tag a second transaction "Avoidable" and a third "Necessary".
4. Open the Spending Cut Recommendations view.
5. **Expected**: the Unnecessary- and Avoidable-tagged transactions appear with their amounts
   summed into potential savings; the Necessary-tagged one does not appear (US2.2, FR-007–008).
6. Change the Avoidable-tagged transaction's tag to "Necessary" and reload the view.
7. **Expected**: it no longer appears (US2.3).
8. Correct the amount on an Unnecessary-tagged bill via the existing correction flow.
9. **Expected**: the corrected bill still carries the "Unnecessary" tag (data-model.md R3).

## Scenario 3 — Price-creep call-out (US3)

1. With the Gym series confirmed (€25 → €32 across its occurrences), open the Spending Cut
   Recommendations view.
2. **Expected**: Gym is flagged "price increased" showing €25 → €32 (a €7 increase) (US3.1, FR-009).
3. Check Rent/Netflix/Internet (constant amount across occurrences).
4. **Expected**: none of them are flagged as a price increase (US3.2).
5. Confirm a series whose amount decreased since its first occurrence.
6. **Expected**: it is not flagged (US3.3, FR-010 — a decrease is not a cut opportunity).

## Scenario 4 — Over-budget and trending-up categories (US4)

1. With "Dining Out" over its €150 budget for the most recently completed month, open the view.
2. **Expected**: "Dining Out" appears flagged with an excess equal to actual minus €150 (US4.1,
   FR-011).
3. With "Groceries" trending upward month-over-month but no budget set for it, reload the view.
4. **Expected**: "Groceries" still appears, flagged by the trend rather than a budget (US4.2,
   FR-012).
5. Pick a category that is both over budget and trending up in the same period.
6. **Expected**: it appears once, not twice (FR-013).

## Scenario 5 — Combined savings total

1. With all of the above in place, open the Spending Cut Recommendations view.
2. **Expected**: one total is shown summing every ranked recurring item's monthly cost, every
   tagged transaction's amount, and every flagged category's excess (SC-004, FR-014).

## Scenario 6 — Empty state (Edge Case / FR-018)

1. In a fresh environment with no confirmed recurring series, no tagged transactions, and no
   budgets, open the Spending Cut Recommendations view.
2. **Expected**: a clear explanatory empty state, not a blank or broken screen.

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up` or the `integration-tests` module, consistent with every prior feature
(007/009/010/011/012/013/014/015). This walkthrough should be executed manually once
implementation lands in an environment with Docker available.
