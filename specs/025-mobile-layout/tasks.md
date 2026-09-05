---
description: "Task list for 025-mobile-layout"
---

# Tasks: Mobile Layout

**Input**: Design documents from `/specs/025-mobile-layout/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included, and one of them is written first — but read Phase 1 before assuming this works
like a backend feature. A layout cannot be unit-tested, and the automated check covers exactly one
requirement (the most important one).

**Organization**: By user story, so each can be implemented, verified and shipped on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md
- **Automated / by eye**: which half of the verification covers it. Say which, always.

## Path Conventions

React SPA under `frontend/`. **No backend module is touched by any task in this feature.** A task
that appears to need one has left the feature's scope.

---

## Phase 1: Setup — the check, written first

**Purpose**: Get the automated half working *before* any layout changes, so its first run fails on
the overflow that exists today. That is the clearest demonstration the check works, and it will
never be that easy to demonstrate again.

**Two facts that will otherwise cost half a day**, both established while planning:

1. **There is no router.** Views are React state (`view === 'accounts'` in `App.tsx`), not URLs, so
   the check cannot `goto('/accounts')`. It must drive the UI — open the navigation, click the
   destination. This is arguably better: it proves navigation and overflow in one pass.
2. **The app is behind a login.** Seed `localStorage['passbook.authToken']` and stub the API; the
   frontend only asks whether a token exists, and the stubs answer everything else.

- [ ] T001 Add Playwright as a dev dependency in `frontend/package.json` and create `frontend/playwright.config.ts`: Chromium only, `webServer` running `vite preview` against the built `dist`, and the three viewport widths from `contracts/layout-contract.md` (320, 375, 1280).
- [ ] T002 Add a `test:layout` script to `frontend/package.json` running the Playwright spec.
- [ ] T003 Create `frontend/tests/layout.spec.ts` with the fixtures: stub every `**/api/**` request with plausible responses (a session, an account, some categories, a handful of transactions, an empty budget) and seed the auth token into `localStorage` before the first navigation. **No backend, no database** — the whole point is that this runs against static files.
- [ ] T004 In the same spec, write the invariant: at each of the three widths, visit every screen and assert `document.documentElement.scrollWidth <= window.innerWidth`. Screens: dashboard, accounts, budgeting, categories, goals, sync — reached by driving the navigation, per the note above.
- [ ] T005 Write the reachability check: at 375px, every destination reachable at 1280px is reachable (SC-002). This is the check that will fail until US1 lands, and it should.
- [ ] T006 Run `npm run build && npm run test:layout` and **confirm it fails** at 320 and 375. Record what overflows. If it passes, the check is not looking at anything — that is the failure mode to catch now rather than after the layout work has hidden it.
- [ ] T007 Add the layout check to the existing frontend job in `.github/workflows/ci-cd.yaml` (`npx playwright install --with-deps chromium`, then `npm run test:layout`). The job already runs `npm ci` and `npm run build`, so this appends to a job that already produces the artefact under test. Expect CI red until US1–US4 land — that is the point of writing it first, but say so in the commit so nobody mistakes it for a broken pipeline.

**Checkpoint**: A failing check that describes the problem precisely.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: One definition of "phone". Everything downstream keys off it.

- [ ] T008 Create `frontend/src/hooks/useIsPhone.ts` exporting `useIsPhone()` as `useMediaQuery(theme.breakpoints.down('sm'))` — 600px in the installed MUI 9.0.1, verified rather than assumed. Document in the file that **this is the only definition of "phone" in the app** and that a component inventing its own threshold reintroduces the gap described below.
- [ ] T009 Add a comment in `frontend/src/theme.ts` pointing at `useIsPhone` and `specs/025-mobile-layout/contracts/layout-contract.md`, so the next person to reach for a breakpoint finds the rule rather than picking a number.

> **Why one definition, and not two that happen to match.** Both layouts are complements of a
> single condition, so no width can exist at which the phone navigation has gone and the desktop
> navigation has not arrived — an operator with no navigation at all. Two components each choosing
> a threshold is exactly how that gap appears, and nobody finds it because nobody resizes a window
> slowly (research R2, quickstart scenario 2).

**Checkpoint**: The boundary exists in one place. No behaviour has changed.

---

## Phase 3: User Story 1 — Every part of the app can be reached (P1)

**Goal**: Nothing is off the edge. Every destination reachable on a phone.

**Independent test**: At 375px, reach every destination that exists at 1280px. Ships alone — the app
becomes usable on a phone even if every screen inside is still cramped.

**This phase is the feature.** A beautifully laid out transaction list is no use to someone who
cannot navigate anywhere.

- [ ] T010 [US1] Create `frontend/src/components/AppNavigation.tsx`: a menu button opening a temporary `Drawer` listing the nine destinations (Accounts, Budgeting, Recurring, Goals, Categories, Import, Sync, Change Password, Log Out). Extracted rather than added inline because `App.tsx` is already 377 lines with eleven buttons in its header, and a second arrangement inline would make it unreadable.
- [ ] T011 [US1] Keep **Add Bill** and **Add Income** in the bar itself as icon buttons at phone width, not in the drawer (FR-003, research R1). These are what an operator reaches for standing in a shop; two taps deep would make the phone layout worse than useless for its most likely use.
- [ ] T012 [US1] In `frontend/src/App.tsx`, render `AppNavigation` below `sm` and the existing button row at `sm` and above, using `sx={{ display: { xs: ..., sm: ... } }}`. **Do not delete or alter the existing row** — additive only (contract §5). The desktop header must be byte-for-byte what it was.
- [ ] T013 [US1] Close the drawer on navigation and on dismissal, and make sure the drawer is reachable and dismissible by touch alone.
- [ ] T014 [US1] Run `npm run test:layout`. The reachability check (T005) must now pass at 375px. Overflow may still fail elsewhere — that is US2–US4.
- [ ] T015 [US1] Quickstart scenario 2 **by eye**: resize slowly from 1280 to 320 and back, confirming there is navigation at every single width and the swap happens once, at 600px, in both directions. Worth doing carefully once.

**Checkpoint**: The app is navigable on a phone. Ships alone if the rest slips.

---

## Phase 4: User Story 2 — The transaction list is readable (P2)

**Goal**: Description, date and amount legible together at 375px.

**Independent test**: Read the ten most recent transactions on a phone without zooming or scrolling
sideways.

- [ ] T016 [US2] In `frontend/src/components/RecentTransactions.tsx`, add a stacked rendering below `sm`: description and date on one line, amount on the other, category and type as small labels, actions in the existing row menu. **The table above `sm` is untouched** — this is a second branch, not a replacement (contract §5).
- [ ] T017 [US2] Truncate a long description with an ellipsis rather than letting it wrap unbounded or widen the row (FR-007). One long merchant name is enough to widen the page for every screen, and it arrives from a bank statement rather than from anything anyone typed — so tidy test data will never surface it.
- [ ] T018 [US2] Keep every per-transaction action available at phone width — correct, remove, history, and the necessity tag (FR-006). Losing an action on a phone is a functional regression, not a layout trade-off.
- [ ] T019 [US2] **Do not abbreviate amounts to save space.** €1,234.56 stays €1,234.56; €1.2k is a Principle IV violation wearing a design decision's clothes, and a layout feature is exactly where that temptation arrives looking reasonable.
- [ ] T020 [US2] Quickstart scenarios 3 and 4 **by eye** at 375 and 320, the second with a deliberately long description. Then `npm run test:layout` for the overflow half.

**Checkpoint**: The most-looked-at screen reads on a phone.

---

## Phase 5: User Story 3 — Forms and dialogs work with a thumb (P2)

**Goal**: Forms use the screen; controls can be hit.

**Independent test**: At 375px, add a bill start to finish by touch, and correct an existing
transaction, without mis-tapping.

- [ ] T021 [US3] In `frontend/src/components/Modal.tsx`, set `fullScreen={useIsPhone()}`. **This one line covers 14 of the app's 17 dialogs** — adding a bill and income, both correction forms, statement import, change password, categories, accounts, savings goals, allocations, recurring proposals and the setup template.
- [ ] T022 [P] [US3] Apply the same to `frontend/src/components/TransactionHistoryDialog.tsx`, which has its own `Dialog` rather than going through `Modal`.
- [ ] T023 [US3] **Leave the confirmations alone**: `frontend/src/components/RemoveConfirmDialog.tsx` and the delete confirmation inside `frontend/src/components/SavingsGoalsPage.tsx` stay as small dialogs (research R4). Full-screen is for a form you work through, not a question you answer — expanding a two-button confirmation to fill the screen hides the thing being confirmed. This task is a decision to verify, not a change to make.
- [ ] T024 [US3] Give every full-screen dialog an obvious dismissal at phone width (FR-009) — a close control in the title area. A boxed dialog can be dismissed by tapping outside it; a full-screen one cannot, so the affordance that existed implicitly must now exist explicitly.
- [ ] T025 [US3] Raise interactive controls to at least **44 × 44 CSS pixels** at phone width (FR-010, contract §4) — the `size="small"` icon buttons, the transaction row menu, and anything added in T010–T011. 44 is WCAG 2.1 SC 2.5.5 and Apple's HIG, not an invented number.
- [ ] T026 [US3] Make a dialog taller than the screen scroll within itself with its primary action still reachable (FR-011, quickstart scenario 10).
- [ ] T027 [US3] Quickstart scenarios 6, 7 and 8 **by eye**, and scenario 6 **on a real phone if possible** — a real keyboard covers half the screen, which a desktop browser's device toolbar does not simulate.

**Checkpoint**: The app can be used, not only read, on a phone.

---

## Phase 6: User Story 4 — The remaining screens fit (P3)

**Goal**: Nothing scrolls sideways, anywhere.

**Independent test**: Visit every screen at 320 and 375; none scrolls horizontally.

- [ ] T028 [P] [US4] `frontend/src/components/SummaryCard.tsx`: make the three `minWidth: 160` stacks conditional — `{ xs: 0, sm: 160 }` — so they stack vertically on a phone. A 480px floor on a 375px screen is the clearest overflow in the app.
- [ ] T029 [P] [US4] `frontend/src/components/TransactionFilterBar.tsx`: the `minWidth: 220` control becomes full-width at `xs`, and the filters stack.
- [ ] T030 [P] [US4] `frontend/src/components/CashFlowForecastCard.tsx`: the `minWidth: 140` becomes conditional.
- [ ] T031 [P] [US4] `frontend/src/components/SyncPage.tsx`: the `maxWidth: 720` cap becomes conditional, and its table either stacks or scrolls **within its own container** — a rarely visited screen does not warrant the transaction list's care, and FR-012 forbids the *page* scrolling sideways, not a container.
- [ ] T032 [US4] **Leave `App.tsx`'s dashboard card grid alone.** It already wraps to one column at phone width via `flexWrap` with `minWidth: 280` children. Touching it is churn with a regression risk and nothing to gain. A verification, not a change.
- [ ] T033 [US4] Run `npm run test:layout`. **All three widths must now pass on every screen** — this is SC-001 and the moment the feature's central claim becomes true.

**Checkpoint**: No screen scrolls sideways at any supported width.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T034 Review the whole diff against contract §5, asking of every hunk: **does this change what a desktop user sees?** If yes it is wrong. This is how SC-006 is kept — there is no automated check that can prove the desktop looks the same, only that it does not overflow.
- [ ] T035 Quickstart scenario 11 **by eye** at 1280: every screen and several dialogs, confirming the desktop is as it was.
- [ ] T036 Quickstart scenario 10 **by eye**: 667 × 375, a phone in landscape — short rather than narrow. Nothing may depend on the screen being tall.
- [ ] T037 [P] Update `README.md` to say the app is usable on a phone and name the supported widths, so the claim is checkable rather than promotional.
- [ ] T038 [P] Add a `CHANGELOG.md` entry under `[Unreleased]`: the app now works on a phone, with navigation in a drawer, full-screen forms and a readable transaction list. Note that **nothing changes for desktop users**.
- [ ] T039 Run `cd frontend && npm run build` and `npm run test:layout`, and report the automated result and the by-eye result **separately**. A green automated run is evidence about one requirement — the most important one, and still only one.
- [ ] T040 Walk `quickstart.md` against what was delivered and correct any drift, particularly that every scenario marked **by eye** was actually looked at rather than assumed.
- [ ] T041 Mark completed tasks `[X]` in this file, add an Implementation Outcome section recording any divergence, then commit and push to `claude/project-status-s0au7m`.

---

## Dependencies

```
Phase 1 (T001–T007)   the check, failing
      │
Phase 2 (T008–T009)   one definition of "phone" — blocks every story
      │
      ├─► Phase 3 US1 (T010–T015)   the feature; ships alone
      ├─► Phase 4 US2 (T016–T020)   independent of US1
      ├─► Phase 5 US3 (T021–T027)   independent of US1 and US2
      └─► Phase 6 US4 (T028–T033)   independent of all three
                    │
                    └─► Phase 7 Polish (T034–T041)
```

US2, US3 and US4 depend on nothing but the shared hook, and can be done in any order or by
different people. Only US1 must come first if you want something shippable early.

## Parallel Execution

Phase 6 is almost entirely parallel — four files, no shared state:

```bash
Task: "SummaryCard conditional minimums"              # T028
Task: "TransactionFilterBar conditional minimums"     # T029
Task: "CashFlowForecastCard conditional minimums"     # T030
Task: "SyncPage conditional cap and table"            # T031
```

In Phase 5, T021 and T022 touch different files and run together. In Phase 7, T037 and T038 do.

## Implementation Strategy

### The MVP is US1 alone

US1 is the difference between an app that is awkward on a phone and one that is unusable on a
phone. Ship it and the app is navigable even if every screen inside it is still cramped; the rest
improve a thing that works rather than enabling one that does not.

1. Phase 1 — **the check first, and confirm it fails** (T006). A check that passes on day one is
   looking at nothing.
2. Phase 2 — one definition of "phone", before anything uses it
3. Phase 3 — US1, then stop and consider shipping
4. Phases 4–6 — any order
5. Phase 7 — the diff review is the important one

### Notes

- **Commit per phase.** T010–T012 (the navigation) deserve their own commit and a careful read.
- **Additive only.** `minWidth: { xs: 0, sm: 160 }`, never `minWidth: 80`. Any task that changes a
  desktop value is wrong however reasonable it looks.
- **No backend module is touched.** If a screen needs data it does not have, report it — do not fix
  it here (spec, Out of Scope).
- **Say which half verified what.** "Automated" and "by eye" are not interchangeable, and this
  feature's automated half covers exactly one requirement.
