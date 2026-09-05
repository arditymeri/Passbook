# Phase 0 Research: Mobile Layout

**Feature**: 025-mobile-layout | **Date**: 2026-09-05

Eight decisions. R6 — how any of this gets verified — is the one the specification deliberately
refused to settle, and the one most likely to determine whether this feature stays true.

---

## R1 — What replaces the header on a small screen

**Decision**: A menu button opening a temporary drawer holding the nine destinations, with **Add
Bill** and **Add Income** kept in the bar itself as icon buttons.

**Rationale**: There are eleven actions. Bottom navigation, the other obvious pattern, comfortably
holds three to five and is wrong at eleven. A drawer holds any number and is the pattern a phone
user already knows.

The two actions kept out of the drawer are the ones FR-003 is about: recording a bill or an income
is the thing an operator does standing in a shop, and putting it two taps deep would make the phone
layout worse than useless for its most likely use. A menu button plus two icon buttons plus the
title fits 320px with room to spare.

**Alternatives considered**: *Letting the existing row wrap* — one line of change, and it produces a
header four rows tall that pushes all content below the fold. *An overflow menu holding the
excess* — the split between visible and hidden would be arbitrary, and it hides exactly the
destinations a new operator needs to discover.

---

## R2 — Where "phone" is defined, and how the two layouts avoid a gap

**Decision**: One shared `useIsPhone()` hook wrapping `useMediaQuery(theme.breakpoints.down('sm'))`,
and **every** small-screen decision in the feature keyed off it or the equivalent `sx` breakpoint.
The installed MUI puts `sm` at 600px, which was verified in `node_modules` rather than assumed.

**Rationale**: This directly answers the spec's sharpest edge case — a width at which the
small-screen navigation has been dismissed but the desktop one has not yet appeared, leaving no
navigation at all. With a single definition of the boundary, that width cannot exist: the two
arrangements are complements of one condition, not two independent conditions that might disagree.
Two components each choosing their own threshold is exactly how such a gap appears, and nobody
finds it because nobody resizes a window slowly.

**Which mechanism where**: `sx={{ display: { xs: 'none', sm: 'flex' } }}` for showing and hiding —
it is CSS, so there is no flash of the wrong layout and no re-render. `useIsPhone()` only where a
**prop** must differ, which in practice means `Dialog`'s `fullScreen`, since that is not
expressible as a style.

---

## R3 — The transaction list on a phone

**Decision**: At phone width, render each transaction as a stacked row — description and date on
one line, amount on the other, category and type as small labels, actions in the existing row menu.
The table is kept unchanged at `sm` and above.

**Rationale**: SC-003 requires description, date and amount legible *simultaneously* at 375px. Six
columns cannot do that, and neither can four: a merchant name plus a date plus a currency amount is
already most of 375px. Hiding columns keeps a layout that still does not fit.

**Alternatives considered**: *A horizontally scrolling table container.* Permitted by FR-012, which
forbids the **page** scrolling sideways rather than a container — but it makes the app's primary
content something you have to swipe to read, and the amount is the part that would fall off the
right edge. *Hiding the category and type columns.* Cheaper, and still leaves date, description and
amount fighting for 375px.

---

## R4 — Full-screen dialogs, and which dialogs should not be

**Decision**: Forms go full-screen at phone width; **confirmations stay as small dialogs**.

**The leverage**: 14 of the app's 17 dialogs render through the shared `Modal.tsx`. One change
there covers adding a bill, adding income, both correction forms, importing a statement, changing a
password, categories, accounts, savings goals, allocations, recurring proposals and the setup
template. `TransactionHistoryDialog` needs the same change applied directly.

**Why confirmations are excluded**: `RemoveConfirmDialog` and the savings-goal delete confirmation
ask one question with two buttons. Expanding that to fill a phone screen makes a small decision look
like a page, and momentarily hides the thing being confirmed. Full-screen is for a form you work
through, not a question you answer.

---

## R5 — What "big enough to tap" means

**Decision**: **44 × 44 CSS pixels** minimum for interactive controls at phone width.

**Rationale**: FR-010 requires a number from published guidance rather than an invented one.
WCAG 2.1 SC 2.5.5 (Target Size, AAA) specifies 44×44 CSS pixels, and Apple's Human Interface
Guidelines give the same figure. WCAG 2.2 SC 2.5.8 sets a lower AA floor of 24×24, and Material
Design recommends 48dp. 44 is the most widely cited and sits between the two extremes.

**Where it bites**: MUI's `size="small"` icon buttons — the transaction row menu in particular — and
any icon-only control added for the phone header.

---

## R6 — How this is verified at all

**The question the specification left open**, and it deserves a real answer rather than a default.

**Decision**: **Both, with different jobs.** A small Playwright check for the things a machine can
judge and a person cannot repeat reliably, and a written manual checklist for the things a machine
cannot judge.

**What the automated check does**: loads the built frontend at 320, 375 and 1280 pixels, visits
every route, and asserts that `document.documentElement.scrollWidth` does not exceed the viewport —
which is SC-001 and FR-012 exactly, the highest-leverage requirement in the feature — plus that the
navigation is reachable at each width, which is SC-002. All API calls are stubbed at the network
layer, so **no backend and no database are needed**: the check serves the built SPA and nothing
else.

**Why this is affordable, checked rather than assumed**: CI already has a frontend job that runs
`npm ci` and `npm run build` on Node 22. Adding a browser and one spec file to a job that already
produces the artefact under test is a modest addition, not a new pipeline.

**Why not a unit-test framework instead**: the spec's own checklist note is right that it would
prove *which arrangement renders* at a given width but not that anything fits — a test environment
with no layout engine has no widths, no `scrollWidth`, and no opinion about overflow. It would give
false confidence about precisely the requirement that matters most here.

**Why the manual checklist is still needed**: no machine can judge whether text is legible, whether
a phone layout reads sensibly, or whether the desktop still looks right. Those need eyes, and
FR-015 asks for a repeatable procedure rather than an invitation to have a look.

**Alternatives considered**: *Manual only* — free, and the feature's central claim would then have
no check at all and would silently rot the first time a component was added. *Screenshot baselines* —
catches SC-006 in principle, and in practice produces failures on every font-rendering difference
between a laptop and CI, which trains everyone to ignore it.

---

## R7 — The components with fixed minimum widths

**Decision**: Make each minimum conditional rather than removing it —
`minWidth: { xs: 0, sm: 160 }` — so the desktop value is preserved by construction.

**Rationale**: See R8. Removing the minimum outright would change the desktop layout, which
SC-006 forbids; making it conditional cannot.

**Where**: `SummaryCard` (three 160px items in one row, a 480px floor), `TransactionFilterBar`
(a 220px control among siblings), `CashFlowForecastCard` (140px), and `SyncPage`, which has both a
width cap and a table. The `SyncPage` table gets R3's treatment or a scrolling container — it is a
rarely-visited screen and does not warrant the transaction list's care.

**Explicitly left alone**: the dashboard card grid in `App.tsx` already wraps to one column at
phone width. It works; touching it would be churn with a regression risk and no gain.

---

## R8 — Keeping the desktop untouched

**Decision**: Make SC-006 true **by construction**, not by testing for it. Every change in this
feature adds a small-screen branch; **no change alters an existing value**.
`minWidth: 160` becomes `minWidth: { xs: 0, sm: 160 }`, never `minWidth: 80`.

**Rationale**: SC-006 is the criterion most likely to be violated quietly while attention is on the
phone, and the one with no natural backstop — the automated check can prove the desktop does not
overflow, not that it looks the same. A discipline that makes the regression impossible to express
is worth more than a test that might notice it afterwards. It also gives review a single question to
ask of every hunk in the diff: *does this change what a desktop user sees?* If yes, it is wrong.

---

## Constitution notes carried into the plan

- **Principles I–V, VII and VIII do not engage.** No financial logic, no data written, no API
  contract touched, nothing in Domain. This is presentation only.
- **Principle VI** binds Domain business logic, and there is none here. Its spirit — that a claim
  should be checkable — is what R6 answers, and answers with an automated check rather than a
  promise to look.
- **The pipeline-first bias, honestly.** The constitution watches the ratio of features that consume
  transaction data to features that produce it. This one neither produces nor consumes; it presents.
  But it is the second consecutive feature that does not advance the pipeline, and the gap found
  while choosing it is still open: statement import computes a category suggestion, shows it, and
  discards it, and `DetectRecurringSeriesServiceImpl` filters out every bill without a category —
  so imported transactions can never form a recurring series and feature 023 has nothing to post for
  an imported account. That is the pipeline work, and it should be the next feature.
