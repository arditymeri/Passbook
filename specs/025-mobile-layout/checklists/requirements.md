# Specification Quality Checklist: Mobile Layout

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-05
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

### Validation iterations

**Iteration 1 — three failures, all fixed:**

1. *No implementation details* — FAILED, badly. The first draft was written from the investigation
   notes and was full of them: component filenames, `useMediaQuery`, `fullScreen`, `minWidth: 160`,
   MUI breakpoints. Every one is a plan decision. Rewritten to describe what an operator meets:
   "eleven actions in a single row that neither wraps nor scrolls", "a boxed dialog with margins",
   "three figures side by side with a combined floor wider than the screen". The specific files and
   numbers belong in research and tasks, where they are already recorded.

2. *Success criteria are measurable* — FAILED. "The app works on a phone" is not checkable, and
   with no automated test to appeal to it would have been settled by argument. SC-001 to SC-007 now
   name widths — 320, 375, 1280 — and state what must be true at each.

3. *Requirements are testable* — FAILED on touch targets. "Controls should be big enough to tap" is
   an opinion. FR-010 now requires a stated minimum taken from published accessibility guidance,
   with the number itself a plan decision rather than one invented here.

**Iteration 2 — clean.**

### Deliberate notes for planning

- **FR-012 is the highest-leverage requirement in the feature.** "No screen scrolls horizontally at
  phone width" subsumes most of the individual overflow problems and is checkable in seconds on any
  screen, including ones nobody thought to list. Prefer it to enumerating components.
- **The verification question is real and is not answered here.** There is no frontend test
  framework — no test runner at all — so no test can assert a layout. The plan must decide between
  introducing one as part of this feature and writing a manual check (FR-015), and must say which
  and why rather than drifting into the manual option by default. Note that a unit-test framework
  would let you assert *which arrangement renders* at a given width, but not that it *looks right* —
  so it is a smaller win here than it first appears.
- **SC-006 is the regression guard and has no automated backstop.** "Desktop is visually identical"
  is the criterion most likely to be quietly violated while attention is on the phone layout, and
  the one nothing will catch. Worth deciding deliberately how it gets checked.
- **The edge case about the layout boundary is not hypothetical.** A width at which the small-screen
  navigation has been dismissed but the desktop one has not yet appeared would leave an operator
  with no navigation at all, and it is exactly the sort of thing that is never tested because
  nobody resizes a window slowly.
