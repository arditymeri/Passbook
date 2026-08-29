# Quickstart: Spending Trends & Insights

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`).

## Setup

1. Ensure at least two categories exist (e.g. "Groceries", "Dining").
2. Record bills against "Groceries" in each of the last three months, with the most recent
   month's amount noticeably higher than the month before it.
3. Record bills against "Dining" only in the current month (none in the prior month).
4. Leave at least one category with no bills recorded at all in the last several months.

## Scenario 1 — Multi-month trend per category (US1)

1. Open the dashboard; locate the Spending Trends card.
2. **Expected**: "Groceries" shows a value for each of the last several months (the default
   window), including the rising pattern set up above, visibly distinguishable without doing the
   math by hand (US1.1).
3. Pick a month in the window where "Groceries" had no spending (if any) and confirm it displays
   as zero rather than being skipped (US1.2).
4. **Expected**: the category left with zero spending across the entire window does not appear in
   the trend list at all (US1.3).

## Scenario 2 — Biggest movers (US2)

1. On the same card, locate the "movers" section.
2. **Expected**: "Groceries" appears as a mover with the size of its increase from last month
   shown (US2.1).
3. **Expected**: "Dining" appears as a mover too, recognized as new spending from a zero prior
   month, not omitted for lack of a prior-month baseline (US2.3).
4. Correct one of the recorded bill amounts via the existing correct-transaction flow, reload, and
   confirm the trend and movers reflect the corrected amount, not the original (FR-006).

## Scenario 3 — Adjustable window (US3)

1. Change the trend window from the default to a shorter preset.
2. **Expected**: the trend updates to show only that nearer-term history (US3.1).
3. Switch to a longer preset and confirm it extends further back, potentially surfacing a pattern
   not visible in the shorter window (US3.2, SC-003: recomputes within a couple of seconds).
4. Confirm the movers section is unaffected by the window change (research.md §3) — it always
   compares the same two most recent months regardless of the selected window.

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up`, consistent with every prior feature (007-015). This walkthrough should be
executed manually once implementation lands in an environment with Docker available.
