# Implementation Plan: Mobile Layout

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-mobile-layout/spec.md`

## Summary

Fit the existing design onto a phone. Four pieces: a menu button and drawer replacing a header row
of eleven buttons that currently runs off the screen; a stacked rendering of the transaction list
below 600px; full-screen forms — 14 of 17 dialogs share one component, so that is nearly one change;
and conditional minimum widths on the handful of components that assert a floor wider than a phone.

**Two decisions carry the feature.** One shared definition of "phone", so the two layouts are
complements of a single condition and no width can exist with neither. And **additive-only
changes** — every edit adds a small-screen branch, none alters a desktop value — which makes
"the desktop is unchanged" true by construction rather than by a test that might notice afterwards.

The specification refused to settle how any of this is verified, and research R6 settles it: a
narrow Playwright check for the one thing a machine judges better than a person — that no page
scrolls sideways, at three widths, on every route — plus a written manual procedure for everything
that needs eyes.

## Technical Context

**Language/Version**: TypeScript 4.6, React 18

**Primary Dependencies**: MUI 9.0.1 (installed version verified, not assumed); **one new dev
dependency** — Playwright, for the layout check

**Storage**: None. No entity, no field, no migration; the last applied migration stays `V4`

**Testing**: Playwright at 320 / 375 / 1280 against the built SPA with the API stubbed at the
network layer — no backend, no database. Plus a manual procedure in `quickstart.md` for the
judgements a machine cannot make

**Target Platform**: Browsers, phone and desktop. 320px floor, 375px reference, 1280px desktop

**Project Type**: React SPA; **frontend only** — no module of the backend is touched

**Performance Goals**: None specific. Showing and hiding is done in CSS rather than JavaScript, so
no layout decision costs a re-render

**Constraints**: The desktop must be visually identical afterwards (SC-006), with no automated check
capable of proving it — hence the additive-only rule; no API change, in shape or in count of
requests

**Scale/Scope**: ~30 components, of which 4 named files plus one shared dialog carry most of it

## Constitution Check

*GATE: passed before Phase 0; re-evaluated after Phase 1 design — result unchanged.*

| Principle | Verdict | Reasoning |
|---|---|---|
| I. Transaction Immutability | **N/A** | Writes no data of any kind |
| II. Ingestion Is Idempotent | **N/A** | Touches no ingestion path |
| III. Balance Derivation | **N/A** | Displays balances the API already computes; derives nothing |
| IV. Currency Precision | **N/A** | Formats amounts the API already sends; performs no arithmetic on money. Worth stating because a layout change is a plausible place to be tempted into rounding for space — abbreviating €1,234.56 to €1.2k would be a Principle IV violation dressed as a design choice, and is not being done |
| V. Audit Trail & Observability | **N/A** | No state-changing operation |
| VI. Test-First Development | **PASS, by analogy** | The principle binds Domain business logic and there is none here. Its spirit — that a claim be checkable — is answered by R6 with an automated check rather than a promise to look. The check is written before the layout work, so the overflow it catches first is the overflow that exists today |
| VII. API Contract Stability | **PASS** | No contract touched. No endpoint, no model, and the same requests in the same shapes |
| VIII. Hexagonal Architecture | **N/A** | Frontend only; no module boundary involved |

### The pipeline-first bias, recorded rather than skipped

The constitution watches the ratio of features that consume transaction data to features that
produce it. This one does neither — it presents — so the bias does not strictly engage. Recording it
anyway, because this is the second consecutive feature that does not advance the pipeline, and
because a real gap was found while choosing it:

> Statement import computes a category suggestion, returns it to the frontend, and discards it at
> confirm — `IngestTransactionsServiceImpl.toBill` never sets `categoryId`. And
> `DetectRecurringSeriesServiceImpl.billOccurrences()` opens with `.filter(b -> b.getCategoryId() != null)`.
> So every imported transaction is invisible to analysis, budgets and envelopes, imported bills can
> never form a recurring series, and feature 023's auto-posting has nothing to post for an account
> you import.

That is the pipeline work, and it should be the next feature.

## Project Structure

### Documentation (this feature)

```text
specs/025-mobile-layout/
├── spec.md
├── plan.md              # This file
├── research.md          # R1–R8
├── data-model.md        # There isn't one, and why saying so matters
├── quickstart.md        # 11 scenarios, each marked automated or by eye
├── checklists/
│   └── requirements.md
├── contracts/
│   └── layout-contract.md   # The UI contract: one breakpoint, three widths, two rules
└── tasks.md             # /speckit-tasks output — not created here
```

### Source Code

```text
frontend/
├── src/
│   ├── hooks/
│   │   └── useIsPhone.ts              # NEW — the single definition of "phone"
│   ├── components/
│   │   ├── AppNavigation.tsx          # NEW — menu button + drawer, extracted from App.tsx
│   │   ├── Modal.tsx                  # full-screen at phone width: 14 dialogs in one change
│   │   ├── TransactionHistoryDialog.tsx   # the same, applied directly
│   │   ├── RecentTransactions.tsx     # stacked rendering below sm; table unchanged above
│   │   ├── SummaryCard.tsx            # conditional minimums
│   │   ├── TransactionFilterBar.tsx   # conditional minimums
│   │   ├── CashFlowForecastCard.tsx   # conditional minimums
│   │   └── SyncPage.tsx               # conditional width cap; table may scroll in-container
│   └── App.tsx                        # header swaps to AppNavigation below sm
├── tests/
│   └── layout.spec.ts                 # NEW — overflow and reachability at three widths
├── playwright.config.ts               # NEW
└── package.json                       # NEW script: test:layout

.github/workflows/ci-cd.yaml           # the existing frontend job gains the layout check
```

**Structure Decision**: No new module, no new directory beyond `hooks/` and `tests/`. The one
structural change worth naming is extracting the navigation out of `App.tsx`: the header is
currently eleven buttons inline in a 377-line component, and adding a second arrangement inline
would make it unreadable. Everything else is an edit in place.

**Deliberately not touched**: the dashboard card grid in `App.tsx`, which already wraps to one
column at phone width. It works. Changing it would be churn with a regression risk and nothing to
gain.

## Phase Ordering Notes

1. **Write `tests/layout.spec.ts` first, and watch it fail.** The overflow it catches on day one is
   the overflow that exists today — which is the clearest possible demonstration that the check
   works, and it will never be that easy to demonstrate again.
2. **`useIsPhone()` before anything uses it.** Every small-screen decision keys off one definition,
   and a component that invents its own threshold reintroduces the gap the whole design avoids
   (research R2, contract §1).
3. **Additive-only, in every task.** `minWidth: { xs: 0, sm: 160 }`, never `minWidth: 80`. Any hunk
   that changes what a desktop user sees is wrong, and that is the question to ask of the diff
   rather than of the running app.
4. **Confirmations are not forms.** `Modal.tsx` goes full-screen; `RemoveConfirmDialog` and the
   savings-goal delete confirmation do not (research R4). A task that makes every dialog full-screen
   has gone one step too far.
5. **Amounts are never abbreviated to save space.** Principle IV, and a layout feature is exactly
   where that temptation arrives looking reasonable.

## Complexity Tracking

No constitutional violations to justify. One addition that a reviewer should see was chosen rather
than accumulated:

| Choice | Why | Simpler alternative rejected because |
|---|---|---|
| A browser-automation dev dependency, for a layout feature | It is the only way to check the feature's central claim — that no page scrolls sideways — and CI already builds the artefact it runs against, so the marginal cost is a browser download on a job that exists | *Manual only*: free, and leaves the central claim with no check, to rot the first time a component is added. *A unit-test framework*: no layout engine, so no widths and no `scrollWidth` — it would prove which arrangement renders and stay silent about whether anything fits, which is false confidence about the one requirement that matters |
