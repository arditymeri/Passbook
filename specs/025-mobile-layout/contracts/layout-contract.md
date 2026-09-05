# Layout Contract: Mobile Layout

**Feature**: 025-mobile-layout | **Date**: 2026-09-05

No REST contract changes — this feature touches no endpoint and no model. What it does introduce is
a **UI contract**: a small set of rules that every component in the app is expected to hold to, and
against which "works on mobile" becomes a checkable claim rather than an opinion (FR-014).

---

## 1. One definition of "phone"

```
phone   = viewport width <  600px      (MUI `sm` breakpoint, verified in the installed package)
desktop = viewport width >= 600px
```

**Exactly one** definition, exported as `useIsPhone()` and used by every small-screen decision in
the app. Components that need only to show or hide use the equivalent style breakpoint (`xs` / `sm`),
which resolves to the same boundary.

**No component may define its own threshold.** Two thresholds that disagree produce a width at which
the phone navigation has gone and the desktop navigation has not arrived — an operator with no
navigation at all, at a width nobody tests because nobody resizes a window slowly.

---

## 2. Named widths

| Width | Name | What must be true |
|---|---|---|
| 320px | Floor | Everything works. The narrowest phone still in circulation |
| 375px | Reference | Everything works and is comfortable. The common modern phone |
| 1280px | Desktop | Identical to before this feature |

These three are what the automated check runs at, and what "works on mobile" means for this project.

---

## 3. The invariant

> **No page scrolls horizontally at any width from 320px upward.**

The whole of FR-012 and SC-001. It is the single most useful rule here because it is checkable in
seconds on any screen — including ones added later that nobody thought to list — and because almost
every individual overflow problem is an instance of it.

A *container* may scroll horizontally where that is a deliberate choice (a wide table on a
rarely-visited screen). The **page** may not.

---

## 4. Touch targets

> **Interactive controls are at least 44 × 44 CSS pixels at phone width.**

From WCAG 2.1 SC 2.5.5 (AAA) and Apple's Human Interface Guidelines, which agree on 44. WCAG 2.2's
AA floor is 24×24 and Material Design suggests 48dp; 44 sits between them and is the most widely
cited.

---

## 5. Additive-only changes

> **Every change adds a phone branch. No change alters an existing desktop value.**

```
minWidth: { xs: 0, sm: 160 }      ✓  desktop keeps 160, by construction
minWidth: 80                      ✗  changes what a desktop user sees
```

This is how SC-006 is kept true without a test that could catch it, and it gives review one question
to ask of every hunk: *does this change what a desktop user sees?* If yes, it is wrong.

---

## 6. What is verified, and by what

| Claim | Checked by |
|---|---|
| No page scrolls horizontally at 320 / 375 / 1280 (SC-001) | Automated, every route |
| Every destination reachable at 375 as at 1280 (SC-002) | Automated |
| Transaction description, date and amount legible together (SC-003) | Manual — legibility needs eyes |
| A bill can be added by touch alone (SC-004) | Manual |
| Controls meet 44×44 (SC-005) | Manual, against the rule above |
| Desktop visually identical (SC-006) | Construction (§5), plus manual spot-check |
| Usable in landscape (SC-007) | Manual |

The automated half needs no backend: it serves the built frontend and stubs every API call at the
network layer.
