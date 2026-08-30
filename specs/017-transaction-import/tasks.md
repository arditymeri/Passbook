---
description: "Task list for Transaction Import"
---

# Tasks: Transaction Import

**Input**: Design documents from `/specs/017-transaction-import/`

**Prerequisites**: plan.md ✅, spec.md ✅ (no research.md/data-model.md/contracts/ — no open
technical unknowns and no REST endpoint is added or changed, per plan.md)

**Tests**: Not included. Same reasoning as every prior frontend-only feature's tasks.md: this
codebase's `frontend/` has never had a test runner, and this feature adds no Domain code, so the
Constitution's Test-First principle (VI, scoped to "financial calculation and business-rule logic
in the Domain module") does not apply. Verification is `tsc --noEmit`, a manual hand-check of the
parsing/matching utility against constructed sample rows (Polish phase), and the manual
`quickstart.md` walkthrough.

**Organization**: Tasks are grouped by user story, in spec priority order (US1 → US2 → US3). The
shared piece every story needs — the new types and the parsing/category-suggestion/
duplicate-detection utility functions — is built once in Foundational.
`ImportTransactionsDialog.tsx` is then a single component built incrementally: US1 gives it the
core upload → parse → preview → confirm/cancel flow (every non-error row included by default); US2
adds duplicate flagging and the per-row include/exclude checkbox; US3 adds category suggestion/
editing, reusing US2's checkbox as the general "exclude any row" mechanism it already is (the same
incremental-single-component pattern `TransactionFilterBar` (012), `SetupTemplateDialog` (013),
`NetWorthCard` (014), and `SpendingTrendsCard` (016) already used).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: Which user story this task belongs to (US1–US3)
- Every task names its exact file path

## Path Conventions

All paths are relative to the repo root, following `plan.md`'s Project Structure — this feature
touches only `frontend/src/`. No backend module (`Domain/`, `Application/`, `Infrastructure/`,
`integration-tests/`) is touched.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The new types and the pure parsing/suggestion/duplicate-detection functions every
user story's UI depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T001 [P] Add `ImportDirection` (`'BILL' | 'INCOME'`), `ImportRowStatus`
  (`'ok' | 'duplicate' | 'error'`), and `ImportCandidate` (`id: string`, `date: string`,
  `description: string`, `amount: number`, `direction: ImportDirection`, `categoryId?: string`,
  `status: ImportRowStatus`, `errorMessage?: string`, `included: boolean`) to
  `frontend/src/types/index.ts`
- [ ] T002 Create `frontend/src/utils/transactionImport.ts`:
  `parseImportFile(csvText: string): ImportCandidate[]` — splits the text into lines (handling a
  header row and quoted fields that may contain commas, without a new dependency per plan.md),
  and for each data row builds an `ImportCandidate`: `date`/`description` taken directly,
  `amount` as the row's absolute value, `direction` from the amount's sign (negative → `'BILL'`,
  positive → `'INCOME'`), `status: 'error'` with an `errorMessage` when the date or amount is
  missing/unparseable (FR-009 — this row is still included in the returned array, just flagged,
  so the rest of the file is unaffected), `status: 'ok'` and `included: true` otherwise,
  `categoryId` left `undefined` (depends on T001)
- [ ] T003 Extend `frontend/src/utils/transactionImport.ts`:
  `suggestCategory(description: string, allTransactions: Transaction[]): string | undefined` —
  normalizes `description` (trim + lowercase, matching `RecurringMatching.normalizeDescription`'s
  backend rule) and returns the `categoryId` of the most recent existing `BILL` transaction whose
  normalized description matches, or `undefined` when nothing matches (FR-005; per spec Assumptions,
  reuses the same description-matching approach recurring-series detection already uses, not a
  new classifier) (depends on T002)
- [ ] T004 Extend `frontend/src/utils/transactionImport.ts`:
  `detectDuplicates(candidates: ImportCandidate[], accountId: string, allTransactions:
  Transaction[]): ImportCandidate[]` — returns a new array where every `'ok'` candidate that
  exactly matches an existing transaction on `accountId` by calendar date, amount, and normalized
  description is switched to `status: 'duplicate'` and `included: false` (FR-006); everything else
  is returned unchanged (depends on T002)

**Checkpoint**: The new types and all three pure utility functions exist and are individually
correct against hand-constructed sample data (verified in Polish). User story UI work can now
begin.

---

## Phase 2: User Story 1 - Import a Statement Instead of Typing Every Transaction (Priority: P1)

**Goal**: A user can upload a CSV, review the parsed transactions, and confirm to create them —
with unparseable rows flagged but not blocking the rest of the file.

**Independent Test**: Upload a file with several valid rows and confirm every one appears in the
selected account's history matching the file; confirm canceling instead creates nothing; confirm
a file with some invalid rows still lets every valid row be reviewed and imported.

### Implementation for User Story 1

- [ ] T005 [US1] Create `frontend/src/components/ImportTransactionsDialog.tsx` (mirroring
  `SetupTemplateDialog.tsx`'s `Modal`-based structure): a file input and an account `Select`
  (FR-002); on file selection, reads it via `FileReader.readAsText` and calls `parseImportFile`
  to build the candidate list into local state; renders a review list — one row per candidate
  showing date, description, amount, direction, and (for `status: 'error'`) the error message in
  place of a normal row (FR-003/FR-009/FR-010: also show a running count of how many rows will be
  created); a Cancel button that closes without calling any create endpoint (FR-012), and a
  Confirm button that, for every candidate with `included: true`, calls `createBill`/`createIncome`
  (per its `direction`) with the selected account, then shows a summary of how many were created
  (depends on T004)
- [ ] T006 [US1] Modify `frontend/src/App.tsx`: add an "Import" button to the toolbar opening
  `ImportTransactionsDialog`, passing the `allTransactions`, `categories`, and `accounts`
  `useDashboardData` already fetches, and calling `handleSaveSuccess` (existing refresh callback)
  after a successful import (depends on T005)

**Checkpoint**: Users can import a CSV file's valid transactions into an account, with invalid
rows clearly called out and canceling leaving history untouched.

---

## Phase 3: User Story 2 - Re-Upload the Same Statement Without Double-Counting (Priority: P1)

**Goal**: Rows that match transactions already recorded on the selected account are flagged and
excluded by default, but can be included anyway.

**Independent Test**: Import a file once, then re-upload the exact same file against the same
account and confirm every row is flagged as a likely duplicate and excluded by default (zero new
transactions if confirmed as-is); confirm explicitly re-including one flagged row creates only
that one.

### Implementation for User Story 2

- [ ] T007 [US2] Extend `frontend/src/components/ImportTransactionsDialog.tsx` (depends on T005):
  after building candidates via `parseImportFile`, call `detectDuplicates(candidates, accountId,
  allTransactions)` before rendering the review list; give every candidate row a checkbox bound
  to its `included` flag (defaulting to `detectDuplicates`'s output — unchecked for duplicates,
  checked otherwise) so the user can toggle it; visually distinguish `status: 'duplicate'` rows
  (e.g. a "Possible duplicate" chip) so the flag is legible, not just a default the user has to
  discover by inspecting a checkbox (FR-006/FR-007)

**Checkpoint**: Re-uploading an already-imported statement no longer creates duplicate
transactions by default, and the feature's MVP (US1 + US2) is complete.

---

## Phase 4: User Story 3 - Correct a Row Before It's Saved (Priority: P2)

**Goal**: A user can fix a wrong/missing suggested category, or exclude any row for any reason,
before confirming.

**Independent Test**: Change a row's suggested category before confirming and verify the created
transaction uses the correction, not the original suggestion; exclude an otherwise-valid,
non-duplicate row and verify it's never created.

### Implementation for User Story 3

- [ ] T008 [US3] Extend `frontend/src/components/ImportTransactionsDialog.tsx` (depends on T007):
  after building candidates, call `suggestCategory(candidate.description, allTransactions)` for
  every `'ok'`/`'duplicate'` candidate to seed its `categoryId`; render each row's category as an
  editable `Select` (options from `categories`, plus "Uncategorized") so the user can change or
  clear the suggestion before confirming (FR-005); note T007's per-row include checkbox already
  satisfies FR-008's "exclude any row" requirement generally, not just for duplicates — no
  additional exclude control is needed here

**Checkpoint**: All three user stories are independently functional — the full
upload/review/correct/confirm experience works end to end.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Full verification once all stories are implemented

- [ ] T009 Run `cd frontend && npx tsc --noEmit` and confirm no type errors across the
  new/modified files
- [ ] T010 Hand-verify `frontend/src/utils/transactionImport.ts` against constructed sample data
  (a small CSV string with a valid bill row, a valid income row, a row with a missing amount, and
  a row engineered to match an existing sample `Transaction`): confirm `parseImportFile` produces
  the right `direction`/`status`/`errorMessage` per row, `suggestCategory` returns the expected
  category for a description matching prior history and `undefined` otherwise, and
  `detectDuplicates` flags exactly the matching row and leaves the others `'ok'`
- [ ] T011 Execute `specs/017-transaction-import/quickstart.md` end to end in the browser —
  expected BLOCKED in this development sandbox (no Docker daemon available, consistent with every
  prior feature 007-016); report honestly rather than marking complete if not actually run

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately. T002 depends on T001; T003
  and T004 both depend on T002 — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Foundational (T004) — the natural first story,
  establishing `ImportTransactionsDialog` and its `App.tsx` entry point every later story extends
- **User Story 2 (Phase 3)**: Depends on US1's dialog (T005) already existing — adds duplicate
  detection and the include/exclude checkbox to the same review list
- **User Story 3 (Phase 4)**: Depends on US2's checkbox (T007) already existing — adds category
  suggestion/editing on top of it
- **Polish (Phase 5)**: Depends on all three user stories being complete

### User Story Dependencies

Like every prior frontend-only feature, priority order already matches dependency order here:
each story extends the same one component (`ImportTransactionsDialog.tsx`), so they must be built
in sequence (US1 → US2 → US3) even though each is independently *testable* once its own task is
done — see each phase's Independent Test.

### Parallel Opportunities

- Foundational: T001 has no dependency and could start immediately; T002 is sequential after it;
  T003 and T004 both depend only on T002 and touch the same file (`transactionImport.ts`), so in
  practice they're sequential edits to one file even though neither depends on the other's *logic*
- T005 and T006 touch different files (`ImportTransactionsDialog.tsx` vs `App.tsx`) but T006
  depends on T005 (the component must exist before it can be mounted)
- T007 and T008 both touch `ImportTransactionsDialog.tsx` and so must run sequentially relative to
  each other

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Foundational
2. Complete Phase 2 (US1): upload/parse/preview/confirm with error-row flagging
3. Complete Phase 3 (US2): duplicate detection and override
4. **STOP and VALIDATE**: import a file, re-import the same file, confirm zero duplicates by
   default (per T010's approach plus a manual pass once Docker is available)
5. Add Phase 4 (US3) — category correction — as a refinement; the feature already delivers its
   core value (import without manual entry, safe to re-run) from US1 + US2

### Incremental Delivery

1. Foundational → types and all three utility functions ready, hand-verified against sample data
2. US1 → core import flow; immediately useful on its own
3. US2 → safe re-import; MVP core value delivered
4. US3 → category correction
5. Polish → type-check, hand-verification, and manual verification

---

## Notes

- `[P]` tasks touch different files with no unmet dependency — safe to run simultaneously
- No backend task exists in this file — `Domain/`, `Application/`, `Infrastructure/`, and
  `integration-tests/` are all untouched by this feature, per `plan.md`'s Constitution Check
- Every created transaction goes through the existing `createBill`/`createIncome` endpoints
  unmodified — this feature's only genuinely new logic is the read-only, review-time parsing/
  suggestion/duplicate-detection in `transactionImport.ts`, which is why T010's hand-verification
  (not a UI click-through) is this feature's closest thing to a regression test, given the
  frontend has no test runner
