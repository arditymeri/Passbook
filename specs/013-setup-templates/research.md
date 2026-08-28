# Phase 0 Research: Setup Templates for Categories and Accounts

No `[NEEDS CLARIFICATION]` markers remain in the spec — every open design question was resolved
with a documented default in the spec's Assumptions section during `/speckit-specify`. This phase
turns those defaults into concrete, testable decisions, and records one finding that shaped the
whole approach (mirroring 012's).

## Finding: the existing create endpoints already provide exactly the semantics this feature needs

**Found**: `POST /categories` (`CategoryCreateController` → `AddCategoryServiceImpl`) and
`POST /accounts` (`AccountCreateController` → `AddAccountServiceImpl`) both already reject a
duplicate name with `409 Conflict` (`IllegalStateException` from `existsByName` checks, mapped by
each controller's `@ExceptionHandler`). Neither service has any concept of a "template" or
"import" — a category/account created through them is, by construction, indistinguishable from
any other.

**Implication**: FR-004 (skip existing items, never duplicate) and FR-009 (template-created items
behave identically to manually created ones) are both automatically satisfied just by calling
these two endpoints — no new Domain logic, no new persisted "applied from template" flag, no new
uniqueness check to write or maintain in parallel with the ones that already exist.

## Decision: Template definitions are a static, hardcoded frontend constant — not persisted anywhere

**Decision**: `frontend/src/data/setupTemplates.ts` exports a `SETUP_TEMPLATES: SetupTemplate[]`
array. No database table, no Domain DTO, no REST endpoint to fetch templates.

**Rationale**: The spec's own Assumptions section rules out a user-editable template system for
this feature's scope ("a small, fixed set... not user-created or user-editable"). A static
frontend constant is the simplest thing that satisfies that requirement — introducing a
`setup_template` table and a `GET /setup-templates` endpoint to serve data that never changes and
is never user-specific would be backend surface with no corresponding capability gained.

**Alternatives considered**: A new `SetupTemplate` Domain entity + `GET /setup-templates` endpoint
— rejected as unnecessary backend surface for genuinely static, build-time-known data; would also
need its own OpenAPI contract and generated model classes for zero behavioral difference from a
frontend constant array.

## Decision: Applying skips on `409`, not on a client-side pre-check

**Decision**: `applySetupTemplate()` calls `createCategoryIfMissing()`/`createAccountIfMissing()`
for every selected item and treats a `409` response as `'skipped'`. It does **not** first fetch the
current category/account lists and filter out names that already exist client-side.

**Rationale**: Letting the existing create endpoint be the single source of truth for "does this
name already exist" avoids a duplicate-logic/race-condition class of bug (the list could be stale
between an initial fetch and the apply loop actually running, however unlikely at this app's
single-user scale) and keeps `applySetupTemplate()` a thin, obviously-correct sequence of calls to
already-tested behavior rather than reimplementing the uniqueness check it would otherwise need to
match exactly.

**Alternatives considered**: Fetching existing categories/accounts first and pre-filtering the
selected items client-side before calling create — rejected as redundant with what the create
endpoint already guarantees, and as a second place the "what counts as a match" rule (exact name)
would need to stay in sync with the backend's actual enforcement.

## Decision: `createCategoryIfMissing`/`createAccountIfMissing` are new, narrowly-scoped API client functions — not a change to the existing `createCategory`/`createAccount`

**Decision**: Two new functions are added to `frontend/src/api/client.ts`, each doing a raw `fetch`
and inspecting `res.status` directly: `409` resolves to `'skipped'`, `2xx` resolves to `'created'`,
anything else throws (matching every other function's error convention). The existing
`createCategory`/`createAccount` (used by `AddCategoryForm`/`AddAccountForm`, where a 409 really
is an error the user needs to see and fix) are untouched.

**Rationale**: A 409 means two very different things depending on caller intent — "you typed a
name that's taken, please fix it" (the manual add-forms' case, where it should surface as an
error) versus "this template item is already covered, nothing to do" (this feature's case, where
it's an expected, non-error outcome). Reusing `postAndReturn` (which throws on any non-ok status)
would force awkward error-message string-sniffing to tell the two cases apart; a small dedicated
function that treats `409` as a normal return value is simpler and cannot be confused with the
manual-form error path.

**Alternatives considered**: Reusing `createCategory`/`createAccount` and catching+string-matching
the thrown error's message to detect a 409 — rejected as fragile (coupling to error-message text)
compared to just checking `res.status` directly in a dedicated function.

## Decision: Apply loop is sequential, not parallel

**Decision**: `applySetupTemplate()` awaits each `POST` in turn (a plain `for` loop with `await`),
rather than `Promise.all`/`Promise.allSettled` over every selected item at once.

**Rationale**: A template has at most a handful of items (single digits to low teens) — sequential
requests complete well within the sub-second range this app's other actions already target, and a
simple sequential loop is easier to reason about and to report results from in a stable order (the
created/skipped report naturally lists items in the same order the template defines them).

**Alternatives considered**: Parallel requests via `Promise.allSettled` — rejected as unnecessary
optimization for a request count this small; would also complicate preserving a stable,
template-defined order in the results report for no real latency benefit.

## Decision: `SetupTemplateDialog` is reachable from both `CategoriesPage` and `AccountsPage`, not a new standalone page

**Decision**: One shared dialog component is mounted from both existing management pages via a
new "Use a starter template" entry point on each; neither page gets a dedicated new route/view.

**Rationale**: A template bundles both category and account items together, so applying it from
either page should create both kinds — there's no reason to split the feature by page. Mounting
from both satisfies FR-008 ("reachable from the existing category/account management screens")
without inventing a third place in the navigation for something that's really an extension of
setup, not a new standalone capability.

**Alternatives considered**: A single new top-level "Setup" page — rejected as an unnecessary new
navigation entry for what both existing "empty state" screens already exist to handle; also splits
the feature's entry point away from where a user already is when they'd want it (adding their
first category or account).
