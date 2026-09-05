# Phase 1 Quickstart: Mobile Layout

**Feature**: 025-mobile-layout | **Date**: 2026-09-05

Validation scenarios, each mapped to a user story and marked **automated** or **by eye**.

This feature inverts the usual split in this repository. Every other feature's hard parts were
Domain rules testable in milliseconds; here the hard parts are visual, and no test can judge whether
something reads well. So the automated half is deliberately narrow — it checks the one thing a
machine judges better than a person and a person cannot repeat reliably — and the manual half is
written out as a procedure rather than left as "have a look on your phone".

**Run the automated half:**

```bash
cd frontend && npm run build && npm run test:layout
```

No backend and no database: it serves the built SPA and stubs every API call.

**Set up the manual half:** open the app, then in the browser's developer tools use the device
toolbar to set an exact width. 375 × 667 is the reference phone; 320 × 568 the floor.

---

## Scenario 1 — Nothing is off the edge (US1, FR-002, SC-002)

**At 375px**, look at the header.

**Expected**: a menu button, the title, and buttons for Add Bill and Add Income. Nothing clipped,
nothing beyond the right edge.

Open the menu. **Expected**: all nine destinations — Accounts, Budgeting, Recurring, Goals,
Categories, Import, Sync, Change Password, Log Out — reachable, none missing.

**Status**: **automated** (reachability), **by eye** (nothing clipped).

---

## Scenario 2 — The layout boundary has no gap (US1, edge case)

**Resize slowly** from 1280px down to 320px and back, watching the header.

**Expected**: at every single width there is navigation — either the full row or the menu button,
never neither. The swap happens at exactly 600px, in one step, in both directions.

**Why this scenario exists**: this is the failure that only appears when someone drags a window edge
slowly, which nobody does. It is prevented by construction — both arrangements are complements of
one condition (contract §1) — and this scenario is how you confirm the construction held.

**Status**: **by eye**, and worth doing once carefully rather than often.

---

## Scenario 3 — Rent is legible on a phone (US2, FR-005, SC-003)

**At 375px**, look at the ten most recent transactions.

**Expected**: for each one, the description, the date and the amount are all readable at once,
without zooming and without swiping sideways. The amount is not truncated — it is the number the
operator came to see.

**Status**: **by eye**. Legibility is the thing no machine judges.

---

## Scenario 4 — A long merchant name does not break the page (US2, FR-007, edge case)

**At 320px**, find or create a transaction with a very long description
(`DAUERAUFTRAG MIETE WOHNUNG HAUPTSTRASSE 42 MONATLICH`).

**Expected**: the description truncates or wraps. The amount stays put. **The page does not scroll
sideways.**

**Why this matters more than it looks**: one long string is enough to widen the page for every
screen, and it arrives from a bank statement rather than from anything anyone typed — so it is not
caught by trying the app with tidy test data.

**Status**: **automated** (the overflow half), **by eye** (that the truncation reads sensibly).

---

## Scenario 5 — Nothing scrolls sideways, anywhere (FR-012, SC-001)

**At 320 and 375**, visit every screen: dashboard, accounts, budgeting, categories, goals, sync,
and each dialog.

**Expected**: vertical scrolling only.

**Status**: **automated**, and this is the check worth having. It covers screens nobody listed,
including ones added after this feature, and it is the requirement everything else in the feature
is an instance of.

---

## Scenario 6 — A bill can be added with a thumb (US3, FR-008, SC-004)

**At 375px**, add a bill from start to finish using touch only.

**Expected**: the form fills the screen rather than sitting in a box with margins. Every field is
reachable, the keyboard does not hide the field being typed into, and the save button can be
reached. There is an obvious way to leave without saving.

**Status**: **by eye**, and best done on a real phone rather than a simulated one — a real keyboard
covers half the screen, which a desktop browser's device toolbar does not simulate.

---

## Scenario 7 — A confirmation is still a confirmation (US3, R4)

**At 375px**, delete a transaction.

**Expected**: a small dialog with a question and two buttons — **not** a full screen. Full-screen is
for forms you work through, not questions you answer, and expanding a two-button confirmation to
fill the screen hides the thing being confirmed.

**Status**: **by eye**.

---

## Scenario 8 — Things can actually be tapped (US3, FR-010, SC-005)

**At 375px**, tap the row menu on a transaction, then each control in the header.

**Expected**: each hits first time. Measure a sample in the element inspector: at least 44 × 44 CSS
pixels.

**Status**: **by eye**, measured rather than judged.

---

## Scenario 9 — The occasional screens fit too (US4, SC-001)

**At 375px**, visit the monthly summary, the transaction filters, the forecast card and the sync
page.

**Expected**: the summary's three figures are all visible; every filter is reachable; nothing
scrolls sideways. The sync page's table may scroll within its own container — it is a rarely
visited screen and does not warrant the transaction list's treatment.

**Status**: **automated** (overflow), **by eye** (that all three summary figures are present).

---

## Scenario 10 — Landscape (SC-007, edge case)

**At 667 × 375** — a phone turned sideways, short rather than narrow.

**Expected**: still usable. Nothing depends on the screen being tall; a dialog taller than the
screen scrolls within itself and its confirm button is reachable.

**Status**: **by eye**.

---

## Scenario 11 — The desktop is untouched (SC-006)

**At 1280px**, visit every screen and open several dialogs.

**Expected**: identical to before this feature. Same header row, same table, same dialog sizes, same
spacing.

**Why there is no automated check for this**: a machine can confirm the desktop does not overflow,
not that it looks the same. This is instead made true by construction — every change adds a phone
branch and none alters a desktop value (contract §5) — so the real check is reading the diff and
asking of each hunk whether it changes what a desktop user sees. This scenario confirms the
construction held.

**Status**: **by eye**, plus the diff review.

---

## What is checked how

| Automated | By eye |
|---|---|
| No page scrolls horizontally, at three widths, on every route | Whether anything is legible |
| Every destination reachable at phone width | Whether the phone layout reads sensibly |
| A long description does not widen the page | Whether a real keyboard hides a field |
| — | Whether the desktop still looks right |
| — | Touch targets, measured |
| — | Landscape |

Report the two separately. Unlike this repository's backend features, a green automated run is
evidence about one requirement — the most important one, and still only one.
