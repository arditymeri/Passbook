# Implementation Plan: Transaction Import

**Branch**: `claude/project-status-s0au7m` (spec directory `017-transaction-import`) | **Date**: 2026-08-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-transaction-import/spec.md`

## Summary

Let a user upload a CSV file of transactions, review a parsed preview (date, description, amount,
bill/income direction, suggested category, duplicate/error flags), correct or exclude rows, and
confirm to create them. Like 013's setup templates, this is a **pure frontend feature**: parsing a
simple CSV, suggesting a category via the same description-normalization the app already uses for
recurring-series matching, and detecting duplicates against `allTransactions` (already fetched,
full history) can all happen client-side — and every reviewed, non-excluded row is created by
calling the **existing** `createBill`/`createIncome` endpoints, exactly like every other manual
add already does. No new backend endpoint, no new Domain service, no new persisted data.

## Technical Context

**Language/Version**: TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler). No backend changes.

**Primary Dependencies**: Existing `@mui/material` v5 component set (a `Dialog` review flow,
mirroring `SetupTemplateDialog.tsx`'s (013) structure) plus the browser's built-in `File`/`FileReader`
API to read the uploaded CSV as text. No CSV-parsing library is added — the accepted shape (FR/
Assumptions: a plain date/description/amount CSV) is simple enough for a small hand-rolled parser
that still correctly handles quoted fields containing commas, matching this app's established
"no new dependency unless clearly justified" precedent (014's inline-SVG chart, 013 avoiding a
form-library).

**Storage**: N/A — no new persisted state, and nothing about an import batch itself is ever
stored; only the individual bill/income records a user confirms are created, via the existing
create endpoints and their existing persistence.

**Testing**: TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — the same
frontend-testing convention every prior feature's plan.md has documented (no frontend test runner
exists in this codebase).

**Target Platform**: Web browser, desktop-primary (unchanged).

**Project Type**: Web application — this feature touches only the `frontend/` half.

**Performance Goals**: Parsing and reviewing a statement of "several dozen" rows (SC-001) is a
handful of synchronous string operations and array passes over already-in-memory data — negligible
cost. Creating the confirmed rows is one `createBill`/`createIncome` call per row, issued
sequentially against existing single-record endpoints (the same pattern 013's template-application
loop already uses) — acceptable at this app's established personal scale.

**Constraints**:
- No new REST endpoint, no new Domain service, no new SPI port, no new persisted table (mirrors
  012/013/014/016's Constitution Check — there is no new backend surface)
- `POST /bills`/`POST /incomes` are not modified — every imported row is created through the exact
  same validated path a manual add already uses, so no new server-side invariant is introduced
- FR-003/FR-012: nothing is created until the user explicitly confirms; canceling must leave
  history completely unchanged — enforced by keeping every parsed row purely in local component
  state until a single confirm action issues the create calls
- FR-006/FR-007: duplicate detection is an **exact** match (account + date + amount + description)
  against `allTransactions` (already fetched, correction-aware per 012/014's precedent) — a simple
  comparison, not fuzzy matching, and always overridable per-row
- FR-009: a row that fails to parse (bad/missing date or amount) must not block the rest of the
  file — parsing produces one candidate-or-error per line independently, never a single
  file-wide failure

**Scale/Scope**: 0 new backend files. ~4 frontend files: one new pure parsing/matching utility
module, one new review dialog component, a small "Import" entry point (button + file picker) on
the dashboard, and the `createBill`/`createIncome` calls already exposed by `api/client.ts`
(unmodified).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | Every created row is a brand-new bill/income record via the existing create endpoints — nothing is ever edited or removed by this feature; a row a user doesn't want is simply never created (excluded before confirm), not deleted after. |
| II. Double-Entry Accounting | ✅ N/A | Unchanged — bills/incomes aren't journal lines in this app's model, same as every other feature that creates them. |
| III. Account Integrity & Balance Derivation | ✅ N/A | Imported rows flow through the same create endpoints as manual entry, so the account balance derives from them identically — no new derivation path. |
| IV. Currency Precision | ✅ Pass | Parsed amounts are converted to the same `BigDecimal`-backed request shape (`CreateBillRequest`/`CreateIncomeRequest`) every manual add already uses before hitting the backend; client-side parsing/preview display uses JS `number` only for the review UI, the established, previously-accepted precedent (012-016) for frontend display — never for the actual create payload's validation. |
| V. Audit Trail | ✅ N/A | No new state-changing operation type — each import is just N ordinary bill/income creations, each already covered by whatever audit behavior (or pre-existing gap) manual creation already has. |
| VI. Test-First Development | ⚠ Pre-existing gap, not worsened | Scoped to Domain financial/business-rule logic; this feature adds none. The new parsing/duplicate-matching utility has no automated test, for the same documented reason as every prior frontend-only feature (no frontend test runner in this codebase) — verified instead by `tsc --noEmit` and a manual `quickstart.md` walkthrough. |
| VII. API Contract Stability | ✅ N/A | No endpoint is added, removed, or changed. |
| VIII. Hexagonal Architecture Compliance | ✅ N/A | No Domain/Infrastructure/Application code is touched. |

**Gate decision**: PASS. Like 012/013/014/016, this is presentation-layer-only, reusing existing
validated create endpoints for every actual write — the feature's only genuinely new logic (CSV
parsing, category suggestion, duplicate detection) is read-only review-time computation.

## Project Structure

### Documentation (this feature)

```text
specs/017-transaction-import/
├── plan.md                      # This file
└── quickstart.md                # Manual verification walkthrough
```

No `research.md`/`data-model.md`/`contracts/`: the technical approach has no open unknowns to
research (mirrors 012/013's precedent for a straightforwardly frontend-only feature), and no REST
endpoint is added or changed.

### Source Code (repository root)

```text
frontend/src/
├── types/index.ts                          # MODIFY — add ImportCandidate,
│                                            #          ImportCandidateStatus
├── utils/
│   └── transactionImport.ts                # NEW — parseImportFile(csvText): ImportCandidate[],
│                                            #        suggestCategory(description, categories),
│                                            #        detectDuplicates(candidates, allTransactions)
├── components/
│   └── ImportTransactionsDialog.tsx         # NEW — file picker, parsed review table (editable
│                                             #        category, per-row include/exclude, flags),
│                                             #        confirm/cancel, calls createBill/createIncome
│                                             #        per confirmed row (mirrors
│                                             #        SetupTemplateDialog.tsx's structure)
└── App.tsx                                  # MODIFY — an "Import" entry point (toolbar button)
                                              #          opening ImportTransactionsDialog, passing
                                              #          allTransactions/categories/accounts
                                              #          useDashboardData already fetches
```

No backend directories (`Domain/`, `Application/`, `Infrastructure/`, `integration-tests/`) are
touched by this feature.

**Structure Decision**: Frontend-only change to the existing web application, structured as a
review dialog (mirroring 013's `SetupTemplateDialog`) launched from a new toolbar button, rather
than a full page — importing is an occasional action, not a primary navigation destination, the
same reasoning that already justifies every other dialog-based flow in `App.tsx` (add/correct
bill or income, remove confirmation, recurring proposals).

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
