# Feature Specification: Mobile Layout

**Feature Branch**: `claude/project-status-s0au7m`

**Created**: 2026-09-05

**Status**: Draft

**Input**: User description: "025-mobile-layout: Make Passbook usable on the phone its operator would actually check it on..."

## Context

Passbook is a personal finance app, and the moment a person most wants to look at one is standing
in a shop wondering whether they can afford something. That is a phone moment. Today the app does
not work on a phone — not "looks cramped", but *does not work*: most of the navigation is off the
edge of the screen with no way to reach it.

The frontend has no responsive behaviour of any kind. Searching the entire source finds no use of
screen-size awareness, no size-dependent styling, and no full-screen dialogs. The page does declare
a correct viewport, so it scales rather than rendering at desktop width — which makes the failures
cramped rather than microscopic, and is the only reason the app is currently *approachable* on a
phone at all.

This feature fits the existing design onto a small screen. It is not a redesign.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every part of the app can be reached (Priority: P1)

The operator opens Passbook on their phone. Today the header holds eleven actions in a single row
that neither wraps nor scrolls, so on a typical phone most of them are simply beyond the edge of the
screen with no gesture that brings them into view. Accounts, budgeting, categories, sync, importing,
changing a password, logging out — all present in the markup, none reachable. After this feature,
every destination the desktop offers can be reached on a phone.

**Why this priority**: It is the difference between an app that is awkward on a phone and one that
is unusable on a phone. Nothing else in this feature is worth doing if this is not done — a
beautifully laid out transaction list is no use to someone who cannot navigate to anything.

**Independent Test**: Open the app at a phone width and reach every destination that exists at
desktop width. Delivers value on its own: the app becomes usable on a phone even if every screen
inside it is still cramped.

**Acceptance Scenarios**:

1. **Given** the app at a phone width, **When** the operator looks at the header, **Then** no
   action is positioned outside the visible area.
2. **Given** the app at a phone width, **When** the operator navigates to each destination in turn,
   **Then** every destination available at desktop width is reachable, and none has been removed.
3. **Given** the app at desktop width, **When** the operator uses the header, **Then** it behaves
   as it does today — this feature adds a small-screen arrangement, it does not replace the
   existing one.
4. **Given** the operator is on a phone, **When** they want to add a bill or income — the two
   actions someone reaches for most often — **Then** those are no harder to reach than any other,
   and preferably easier.

---

### User Story 2 - The transaction list is readable on a phone (Priority: P2)

The transaction list is the main content of the dashboard and the thing the operator opens the app
to look at. It is presented as six columns — date, description, category, amount, type, and a row
menu — which cannot fit a phone screen legibly. After this feature, an operator can read their
recent transactions on a phone: what it was, when, and how much, without pinching, and without
horizontal scrolling swallowing the amount.

**Why this priority**: It is the single most-looked-at screen. Second only because a list you cannot
navigate to is worse than one you can navigate to and find cramped.

**Independent Test**: At a phone width, read the ten most recent transactions and identify each
one's description, date and amount without zooming or scrolling sideways.

**Acceptance Scenarios**:

1. **Given** the dashboard at a phone width, **When** the operator looks at a transaction, **Then**
   its description, date and amount are all legible at once.
2. **Given** the dashboard at a phone width, **When** the operator scrolls, **Then** the page
   scrolls vertically only — nothing forces sideways scrolling.
3. **Given** a transaction with a long description, **When** it is displayed on a phone, **Then**
   it does not push the amount off-screen or distort the row.
4. **Given** the dashboard at desktop width, **When** the operator looks at the list, **Then** it is
   unchanged from today.
5. **Given** a transaction on a phone, **When** the operator wants to correct, remove or inspect it,
   **Then** those actions are still available.

---

### User Story 3 - Forms and dialogs are usable with a thumb (Priority: P2)

Every form in the app — adding a bill, importing a statement, changing a password, confirming a
deletion — appears in a boxed dialog sized for a desktop, with margins around it. On a phone those
margins are screen space there is none of, and the controls inside end up smaller than a fingertip
can reliably hit. After this feature, a form on a phone uses the whole screen, and the things the
operator taps are big enough to tap.

**Why this priority**: Reading is the common case, entering is the occasional one — but an app you
cannot enter a transaction into on a phone will not be used on a phone.

**Independent Test**: At a phone width, add a bill from start to finish using only touch, and
correct an existing transaction, without mis-tapping.

**Acceptance Scenarios**:

1. **Given** a phone width, **When** any form or dialog opens, **Then** it uses the full screen
   rather than a boxed area with margins.
2. **Given** a full-screen dialog on a phone, **When** the operator wants to leave without saving,
   **Then** there is an obvious way to dismiss it.
3. **Given** a phone, **When** the operator taps any control — a button, a row menu, a dropdown —
   **Then** the target is large enough to hit reliably on the first attempt.
4. **Given** a desktop width, **When** a dialog opens, **Then** it appears as it does today.

---

### User Story 4 - The remaining screens fit (Priority: P3)

Several components assert fixed minimum widths that exceed a phone screen: the monthly summary
places three figures side by side with a combined floor wider than the screen; the transaction
filter bar holds controls with their own minimums; the forecast card and the sync page do the same,
and the sync page also presents a table. Each produces the same result — content pushed out of view
or the whole page scrolling sideways.

**Why this priority**: Real, but these are screens an operator visits occasionally rather than every
time they open the app.

**Independent Test**: Visit every screen in the app at a phone width and confirm none of them
scrolls sideways.

**Acceptance Scenarios**:

1. **Given** a phone width, **When** the operator visits any screen, **Then** no screen scrolls
   horizontally.
2. **Given** the monthly summary on a phone, **When** it is displayed, **Then** all of its figures
   are visible.
3. **Given** the transaction filters on a phone, **When** the operator uses them, **Then** every
   filter is reachable and usable.
4. **Given** any screen at desktop width, **When** it is displayed, **Then** it is unchanged.

---

### Edge Cases

- **A very narrow phone.** The smallest widely used screens are narrower than the common case; the
  layout must hold there too, not just at the typical size.
- **Landscape.** A phone turned sideways is short rather than narrow. Nothing may depend on the
  screen being tall.
- **A long description or a large amount.** Text that overflows must be truncated or wrapped
  deliberately, never allowed to widen the page — a single long merchant name must not make every
  screen scroll sideways.
- **The boundary between layouts.** At whatever width the small-screen arrangement gives way to the
  desktop one, both must be complete: no width may exist at which navigation is missing from both.
- **Rotating with a form open.** A part-completed form must not lose what has been typed.
- **A dialog taller than the screen.** A long form must scroll within itself, with its confirm
  action still reachable.

## Requirements *(mandatory)*

### Functional Requirements

**Reaching things (US1)**

- **FR-001**: Every destination and action available at desktop width MUST be reachable at phone
  width.
- **FR-002**: No navigation control may be rendered outside the visible area at phone width.
- **FR-003**: The two most frequent actions — recording a bill and recording income — MUST be
  reachable without first opening a menu, if the arrangement permits it.
- **FR-004**: The desktop arrangement MUST be unchanged. This feature adds behaviour at small
  widths; it does not alter what a desktop user sees today.

**Reading transactions (US2)**

- **FR-005**: At phone width, a transaction's description, date and amount MUST be simultaneously
  legible without zooming.
- **FR-006**: The per-transaction actions available at desktop width MUST remain available at phone
  width.
- **FR-007**: A long description MUST NOT displace other content or widen the page.

**Entering things (US3)**

- **FR-008**: At phone width, forms and dialogs MUST occupy the full screen.
- **FR-009**: A full-screen dialog MUST offer an obvious way to dismiss it without saving.
- **FR-010**: Interactive controls MUST meet a stated minimum touch-target size, chosen from
  published accessibility guidance rather than invented.
- **FR-011**: A dialog whose content exceeds the screen height MUST scroll internally, with its
  primary action reachable.

**Fitting (US4, and universal)**

- **FR-012**: No screen may scroll horizontally at phone width. This is the single rule that
  subsumes most of the individual overflow problems and is the easiest to check.
- **FR-013**: Every screen in the app MUST be verified at phone width, not only those named in this
  specification.

**Verification**

- **FR-014**: The widths at which the app is expected to work MUST be named explicitly, so
  "works on mobile" is a checkable claim rather than an opinion.
- **FR-015**: A repeatable check MUST exist that a person can follow to confirm each success
  criterion, listing the screens to visit and what to look for.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At every width from 320 pixels upward, no screen in the app scrolls horizontally.
- **SC-002**: At 375 pixels, every destination reachable at 1280 pixels is reachable, with none
  removed and none off-screen.
- **SC-003**: At 375 pixels, an operator can read the description, date and amount of each of their
  ten most recent transactions without zooming or scrolling sideways.
- **SC-004**: At 375 pixels, an operator can add a bill from start to finish using touch alone.
- **SC-005**: Every interactive control meets the stated minimum touch-target size.
- **SC-006**: At 1280 pixels, every screen is visually identical to before this feature.
- **SC-007**: The app remains usable at 375 pixels in landscape as well as portrait.

## Assumptions

- **Phone and desktop are the two cases.** A tablet-specific arrangement is not planned; tablets get
  whichever of the two their width selects. A third arrangement would be speculative work for a
  device nobody has said they use.
- **375 pixels is the reference width, 320 the floor.** 375 is the common modern phone; 320 is the
  narrowest still in circulation. Both are named so that "works on mobile" can be checked rather
  than argued about.
- **The existing visual design is correct** and only needs to fit. Colours, typography and component
  choices are not in question.
- **Touch and pointer both matter.** A phone-width browser window on a desktop should get the
  small-screen arrangement; the app cannot reliably detect a touchscreen and should not try.
- **The dashboard card grid already behaves.** Its cards wrap to a single column at phone width
  without change, and this feature should leave them alone rather than rework something that works.
- **There is no automated test for layout.** The frontend has no test framework at all — unlike
  every backend feature in this repository, no test can assert that a screen fits. Verification is
  therefore a person following a written check at named widths. Whether to introduce a test
  framework is a real question, deliberately left to planning rather than assumed either way here.

## Out of Scope

- **A visual redesign.** No new palette, typography or component vocabulary. Fitting the existing
  design on a small screen is the whole of it; restyling would make it impossible to tell a layout
  regression from an intended change.
- **A native app, an installable web app, offline support.** Different features with different
  arguments, none of which this one needs.
- **New navigation concepts beyond what a small screen forces.** If the desktop header needs a
  small-screen counterpart, that is in scope; reorganising what the app's destinations *are* is not.
- **Any backend or API change.** This is presentation only. If a screen needs data it does not have,
  that is a finding to report, not to fix here.
- **A tablet layout**, per the assumption above.
- **Charts and data visualisation redesign.** Existing visual elements should fit; how they present
  data is not reopened.
