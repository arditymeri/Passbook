# Phase 1 Quickstart: Auto-Post Confirmed Recurring Series

**Feature**: 023-auto-post-recurring-series | **Date**: 2026-09-04

Validation scenarios, each mapped to a user story, each marked **locally executable** or
**CI-verified**. No Docker daemon here, as in 021 and 022.

This feature is unusually favourable for local testing: its hard parts are calendar arithmetic
(which occurrences are due) and a matching rule (which import supersedes which prediction). Both are
pure Domain logic, and both run here — provided the posting service takes "today" as a parameter
rather than reading the clock (research R7). If it reads the clock, none of the scenarios below can
be tested at all, which is why that decision is load-bearing rather than stylistic.

---

## Scenario 1 — Rent appears without being typed (US1, FR-001)

**Setup**: a series confirmed on 1 February, `MONTHLY`, whose latest real occurrence is 1 February
for €1250 on the current account.

```
postDueOccurrences(today = 2026-03-01)
```

**Expected**: one transaction — €1250, dated 1 March, on the same account, with the series' category
and description. It appears in balances, budgets and analysis like any other.

**Status**: **runs locally** as a Domain test. The end-to-end version (does it really show up in the
dashboard?) is CI-verified.

---

## Scenario 2 — Running again changes nothing (US1, FR-004, SC-002)

```
postDueOccurrences(today = 2026-03-01)   → posts 1
postDueOccurrences(today = 2026-03-01)   → posts 0
postDueOccurrences(today = 2026-03-02)   → posts 0
```

**Expected**: one transaction, not three.

**Why it holds**: identity is `recurring:<seriesId>:2026-03-01` every time, and 022's unique index
refuses the second and third. Note that the third call *recomputes the same due set* — there is no
"already ran today" check, deliberately (research R1).

**Status**: the identity derivation runs locally; the refusal is **CI-verified**, since it is the
database doing the work.

---

## Scenario 3 — Three weeks of downtime (US1, FR-003, SC-003)

**Setup**: a `WEEKLY` series, latest real occurrence 1 March. The instance is off from 2 March to
25 March.

```
postDueOccurrences(today = 2026-03-25)
```

**Expected**: exactly three transactions — 8, 15 and 22 March. Not one. Not twenty-four.

**The two failure modes this catches**: posting only the most recent missed occurrence (a naive
"is it due today?" check), and posting one per day of downtime (a naive catch-up loop).

**Status**: **runs locally**. This is the scenario most worth writing first.

---

## Scenario 4 — The bank's version wins (US2, FR-007, FR-008, SC-004)

**Setup**: rent auto-posted for 1 March at €1250. Then a statement is imported containing
`2026-03-01,RENT,-1250.00`.

**Expected**:

| Check | Value |
|---|---|
| Transactions on the account for 1 March | 3 — the prediction, its reversal, the imported row |
| Balance effect for March | −€1250, counted once |
| The auto-posted row | Still present, unmodified |
| The reversal | References the auto-posted row |

**The assertion that matters is the balance**, not the row count. Three rows is the correct outcome
of a design that never deletes; a balance of −€2500 is the bug this whole story exists to prevent.

**Status**: **CI-verified end to end.** The matching rule underneath — is this import within the
cadence and amount tolerance of that prediction? — **runs locally**.

---

## Scenario 5 — A near miss is not a match (US2, FR-009)

Same setup, but the import is `2026-03-01,RENT,-1600.00` — a 28% jump, far outside the 5% band.

**Expected**: recorded as an ordinary new transaction. **Nothing is superseded.** The operator ends
up with both, which is correct: the app should not quietly cancel its own prediction because
something vaguely similar arrived.

Repeat with a date far outside the cadence window and the same expectation holds.

**Status**: **runs locally** (the rule), **CI-verified** (the outcome).

---

## Scenario 6 — The feedback loop does not close (US1, research R2)

**The scenario that catches the subtlest bug in this feature.**

**Setup**: a `MONTHLY` series, latest *real* occurrence 1 January at €1000. Rent rises to €1100 from
March, and the operator imports statements only occasionally.

```
postDueOccurrences(2026-02-01)   → posts €1000 for 1 Feb
postDueOccurrences(2026-03-01)   → posts ???  for 1 Mar
```

**Expected**: the 1 March post is **€1000** — derived from the January *real* occurrence, not from
the February prediction. Then import a statement containing the real 1 March rent at €1100:

**Expected**: the February prediction is untouched (no matching import), the March prediction is
superseded by the €1100 fact, and the *next* run derives €1100 because the anchor has moved to a real
occurrence.

**What fails without R2's filter**: the app derives each month from its own previous guess, the
anchor drifts onto invented dates, and a rent increase is never picked up even after the bank reports
it. Everything still *looks* fine.

**Status**: **runs locally**, and it is the highest-value test in the feature.

---

## Scenario 7 — Confirmation is not retroactive (FR-005, research R4)

**Setup**: a series detected from occurrences ending 1 March 2024, confirmed today.

```
postDueOccurrences(today)
```

**Expected**: **nothing is posted for 2024 or 2025.** Confirming says "expect this from now on", not
"invent the past". Posting twenty-four months of rent because someone clicked confirm is the worst
outcome this feature could produce.

**Status**: **runs locally**.

---

## Scenario 8 — A series with no usable history is skipped (FR-006)

**Setup**: a confirmed series whose only occurrences are auto-posted rows that were later superseded
— so its *real* occurrence list is empty.

**Expected**: the series is skipped and reported as skipped. **No transaction is posted with an
invented amount or account.** A series carries neither of its own.

**Status**: **runs locally**. Reachable in practice, not a defensive branch.

---

## Scenario 9 — Stopping (US4, FR-014, FR-015)

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/recurring-series/$ID/stop
```

**Expected**: status `STOPPED`; the next posting run writes nothing for it; the series is still
listed with its history; transactions already posted are untouched; and it remains distinguishable
from a `DISMISSED` series.

**Status**: **CI-verified**.

---

## Scenario 10 — A monthly series due on the 31st (FR-017)

**Setup**: `MONTHLY`, latest real occurrence 31 January.

**Expected**: the February occurrence resolves to 28 or 29 February — the last valid day — rather
than being skipped or throwing.

**Delivered differently from the original wording here, deliberately.** March does *not* return to
the 31st: stepping from 28 February gives 28 March. Returning to the 31st would mean carrying a
nominal day-of-month alongside the ledger — invisible state that can disagree with the transactions
actually recorded. The clamped answer is derived from the previous occurrence and nothing else, so
it can always be recomputed from what is stored.

In practice the drift stops at the first real occurrence: a bank posting rent on 31 March re-anchors
the series on that date. It accumulates only for a series posting to an account whose statements are
never imported — the case the README and CHANGELOG both name outright.

**Status**: **runs locally**. This falls out of `java.time` rather than needing a rule of our own,
but both the clamp and the non-return to the 31st are asserted so a future refactor cannot quietly
change either.

---

## What runs where

| Runs locally | Needs CI |
|---|---|
| Which occurrences are due, from any "today" | The `V3` migration |
| Downtime catch-up, and its two failure modes | Uniqueness refusing a repeat post |
| The confirmation lower bound | Reconciliation inside the import path |
| The real-occurrence filter (feedback loop) | The scheduler firing at all |
| Supersession matching and near-misses | Stopping, end to end |
| Month-end date resolution | Balances after supersession |
| Skipping a series with no usable history | |

Report the two columns separately. As in 022, a green local build is evidence about the left column
only — but here the left column contains most of what can actually go wrong.
