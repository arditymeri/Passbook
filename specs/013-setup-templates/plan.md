# Implementation Plan: Setup Templates for Categories and Accounts

**Branch**: `claude/project-status-s0au7m` (spec directory `013-setup-templates`) | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/013-setup-templates/spec.md`

## Summary

Let a user preview and apply a small, fixed set of predefined "starter templates" (name, category
items, account items) so they don't have to create every category and account one form submission
at a time. Like 012, this is a **pure frontend feature**: `POST /categories` and `POST /accounts`
already exist, already reject a duplicate name with `409 Conflict`
(`AddCategoryServiceImpl`/`AddAccountServiceImpl` both throw `IllegalStateException` on a name
collision, mapped to 409 by their controllers), and items they create already behave identically
to any other category/account (FR-009 is automatically satisfied by reusing the exact same
create endpoints, not a parallel "template import" path). Applying a template is simply calling
those two existing endpoints once per selected item, treating a 409 response as "skipped" rather
than an error. The templates themselves are static, hardcoded frontend data — the spec's own
Assumptions section rules out a user-editable template system for this feature's scope, so there
is nothing to persist.

## Technical Context

**Language/Version**: TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler). No backend changes.

**Primary Dependencies**: Existing `@mui/material` v5 component set (no new frontend dependencies); no new backend dependencies.

**Storage**: N/A — template definitions are a static in-memory constant, never persisted. Applying reads/writes exclusively through the existing `POST /categories` / `POST /accounts` endpoints.

**Testing**: TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — the same frontend-testing convention every prior feature's plan.md has documented (no frontend test runner exists in this codebase, per 012's research.md finding, still true).

**Target Platform**: Web browser, desktop-primary (unchanged).

**Project Type**: Web application — this feature touches only the `frontend/` half.

**Performance Goals**: A template has a handful of items (single digits to low teens); applying makes one sequential `POST` per selected item — no batching, no parallelism, no perceptible latency concern at this scale.

**Constraints**:
- No new REST endpoint, no new Domain service, no new SPI port, no new persisted table (mirrors 012's Constitution Check — there is no new backend surface)
- `POST /categories`/`POST /accounts` are not modified; template application is just repeated calls to what already exists
- A template item that collides by name with an existing category/account MUST be skipped, never overwritten or duplicated — automatically true since it reuses the existing uniqueness-enforcing create endpoints (FR-004)
- A category or account created by applying a template MUST be indistinguishable afterward from a manually created one — automatically true for the same reason (FR-009); no new "template-derived" field is added to `Category`/`Account` anywhere

**Scale/Scope**: 0 new backend files. ~6 frontend files: one new static template-data module, one new small API helper addition, one new pure "apply" function, one new dialog component, and two small extensions to existing pages (`CategoriesPage.tsx`, `AccountsPage.tsx`) to make the feature reachable (FR-008).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ N/A | Categories and accounts are not financial transactions — `AddCategoryServiceImpl`/`AddAccountServiceImpl` already do plain creates with no reversal concept, and this feature only ever *creates* (never modifies or deletes) a category/account, per the spec's own Assumptions section. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged from every prior feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass | An account created via a template starts with no balance field set (defaults per `AddAccountServiceImpl`'s existing validation), same as any manually created account — its balance is still derived at read time by `GetAccountServiceImpl`, unchanged. |
| IV. Currency Precision | ✅ Pass | No new monetary computation — template account items carry a single default currency string, validated by the same `AddAccountServiceImpl.validate()` every other account creation already goes through. |
| V. Audit Trail | ✅ N/A / Pre-existing gap | Category/account creation was never audit-logged before this feature and still isn't — applying a template is just N calls to an already-unlogged creation path, not a new gap. |
| VI. Test-First Development | ⚠ Pre-existing gap, not worsened | Scoped to Domain financial/business-rule logic; this feature adds none. The new `applySetupTemplate()` frontend function has no automated test, for the same reason documented in 012's research.md (this codebase's frontend has never had a test runner) — verified instead by `tsc --noEmit` and manual `quickstart.md` walkthrough. |
| VII. API Contract Stability | ✅ Pass | No endpoint is added, removed, or changed — `POST /categories`/`POST /accounts` are called exactly as any existing form already calls them. |
| VIII. Hexagonal Architecture Compliance | ✅ N/A | No Domain/Infrastructure/Application code is touched. |

**Gate decision**: PASS. Like 012, this is presentation-layer-only; every write it performs goes through an already-published, already-validated, already-uniqueness-enforcing endpoint.

## Project Structure

### Documentation (this feature)

```text
specs/013-setup-templates/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output (static template shape only — no persisted entity)
└── quickstart.md                # Phase 1 output
```

No `contracts/` directory: no REST endpoint is added or changed, so there is no new interface
contract to document — the same reasoning 012's plan.md already used.

### Source Code (repository root)

```text
frontend/src/
├── data/
│   └── setupTemplates.ts                   # NEW — static SETUP_TEMPLATES: SetupTemplate[]
│                                            #        constant; ships with one "Personal Finance
│                                            #        Starter" template
├── types/index.ts                          # MODIFY — add SetupTemplate, TemplateCategoryItem,
│                                            #          TemplateAccountItem, ApplyTemplateResult
├── api/client.ts                           # MODIFY — add createCategoryIfMissing,
│                                            #          createAccountIfMissing (raw fetch, treats
│                                            #          409 as a non-throwing "skipped" outcome
│                                            #          rather than an error, unlike every other
│                                            #          create function in this file)
├── utils/
│   └── applySetupTemplate.ts               # NEW — applySetupTemplate(template, selectedKeys):
│                                            #        Promise<ApplyTemplateResult>, sequential
│                                            #        POSTs via the two functions above
├── components/
│   ├── SetupTemplateDialog.tsx              # NEW — pick a template → preview with per-item
│   │                                        #        checkboxes (Categories/Accounts grouped) →
│   │                                        #        apply → created/skipped report
│   ├── CategoriesPage.tsx                   # MODIFY — add a "Use a starter template" entry
│   │                                        #          point, mount SetupTemplateDialog, refresh
│   │                                        #          categories after applying
│   └── AccountsPage.tsx                     # MODIFY — same, refreshing accounts after applying
```

No backend directories (`Domain/`, `Application/`, `Infrastructure/`, `integration-tests/`) are
touched by this feature.

**Structure Decision**: Frontend-only change to the existing web application. No new modules, no
new backend files, no new full page — `SetupTemplateDialog` is a shared modal reachable from both
`CategoriesPage` and `AccountsPage` (since a template bundles both category and account items
together, and FR-008 only requires reachability from *either* existing management screen).

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
