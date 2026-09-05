# Phase 1 Data Model: Mobile Layout

**Feature**: 025-mobile-layout | **Date**: 2026-09-05

## There is no data model

No entity, no field, no migration, no API change. The last applied migration remains `V4`, and the
next feature that needs schema takes `V5`.

Stating that rather than deleting the file, because "no data model" is a claim worth making
explicitly for a feature that touches thirty-odd components: if a task in this feature appears to
need a new field, or a value the API does not already return, that task has left the feature's
scope and the answer is to report it, not to add it (spec, Out of Scope).

---

## The only state this feature introduces

| State | Where it lives | Notes |
|---|---|---|
| Is this a phone-width screen? | Derived, never stored | One shared `useIsPhone()` reading the viewport. Derived on every render from the browser, so it cannot go stale or disagree with itself |
| Is the navigation drawer open? | One component, in memory | Ordinary open/closed UI state. Closes on navigation and on dismissal; nothing depends on it surviving anything |

Neither is persisted, and neither should be. A remembered "mobile mode" would be a second source of
truth about the window's width, and the two would eventually disagree — the classic version of this
bug is an operator who rotates their phone and gets a layout for the width they no longer have.

---

## What this feature must not change

- **Every transaction, account, budget, goal and series.** Untouched. This feature reads what the
  app already fetches and lays it out differently.
- **Every API request the app makes.** Unchanged, in count and in shape. A phone must not fetch
  differently from a desktop; if a screen needs data it does not have, that is a finding to report
  rather than fix here.
