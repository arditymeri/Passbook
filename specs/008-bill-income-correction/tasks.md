---
description: "Task list for Bill/Income Correction Flow"
---

# Tasks: Bill/Income Correction Flow

**Input**: Design documents from `specs/008-bill-income-correction/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

**Tests**: Included — Constitution Principle VI mandates unit tests for domain logic and
integration tests against a real database (no mocks). This feature's correctness (reversals netting
to exactly zero, originals never mutated) is precisely the kind of invariant Principle VI exists to
protect, so backend test tasks are non-negotiable. Frontend-only tasks follow the 005/007 precedent
(verified via TypeScript type-check + manual smoke test).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Domain data: `Domain/src/main/java/at/ymeri/my/finance/domain/data/{bill,income}/`
- Domain API ports: `Domain/src/main/java/at/ymeri/my/finance/domain/api/`
- Domain services: `Domain/src/main/java/at/ymeri/my/finance/domain/service/{bill,income}/`
- Domain tests: `Domain/src/test/java/at/ymeri/my/finance/domain/service/{bill,income}/`
- Infrastructure entities: `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/`
- Swagger: `Application/src/main/resources/swagger/{bill,income}/`
- Application controllers: `Application/src/main/java/at/ymeri/my/finance/controller/{bill,income}/`
- Integration tests: `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/`
- Frontend: `frontend/src/{types,api,components}/`

---

## Phase 1: Setup

**Purpose**: Confirm prerequisites — no new dependencies or directories are required.

- [X] T001 Confirm no new Maven dependencies (Spring Data JPA / MapStruct / OpenAPI Generator already present) and no new frontend dependencies (MUI v5 already installed per 006) are needed; confirm `frontend/src/components/` and the four Domain `{bill,income}` service/test directories exist

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The two new fields on both entities, the OpenAPI contracts, and the generated API
interfaces that every user story depends on. Nothing in US1/US2/US3 can start until this is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Add `correctsTransactionId` (String) and `reversal` (boolean) fields to `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java`
- [X] T003 [P] Add `correctsTransactionId` (String) and `reversal` (boolean) fields to `Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java`
- [X] T004 [P] Add `corrects_transaction_id` (String) and `reversal` (boolean) `@Column`s to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java` — Hibernate `ddl-auto: update` adds the columns; both are nullable so existing rows need no migration (per data-model.md)
- [X] T005 [P] Add `corrects_transaction_id` (String) and `reversal` (boolean) `@Column`s to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java`
- [X] T006 [P] Update `Application/src/main/resources/swagger/bill/bill-model.yaml` — add `correctsTransactionId` and `reversal` to the `bill` schema; add `correctBillRequest` and `billHistoryResponse` schemas (per `contracts/bill-model.yaml`)
- [X] T007 [P] Update `Application/src/main/resources/swagger/income/income-model.yaml` — add `correctsTransactionId` and `reversal` to `incomeResponse`; add `correctIncomeRequest` and `incomeHistoryResponse` schemas (per `contracts/income-model.yaml`)
- [X] T008 [P] Copy `contracts/bill-correction-controller.yaml` to `Application/src/main/resources/swagger/bill/bill-correction-controller.yaml` (PUT/DELETE `/bills/{id}`, GET `/bills/{id}/history`)
- [X] T009 [P] Copy `contracts/income-correction-controller.yaml` to `Application/src/main/resources/swagger/income/income-correction-controller.yaml` (PUT/DELETE `/incomes/{id}`, GET `/incomes/{id}/history`)
- [X] T010 Add two `<execution>` blocks (`bill-correction`, `income-correction`) to `Application/pom.xml`, copying the existing `bill-get` block's configuration and pointing `inputSpec` at the two new controller YAMLs (depends on T008, T009)
- [X] T011 Run `./mvnw -pl Application generate-sources` and verify the generated `BillCorrectionApi`/`IncomeCorrectionApi` delegate interfaces plus the new request/response models exist; delete the stale generated `Bill.java`/`IncomeResponse.java` first if the new fields don't appear (`skipOverwrite=true` means existing generated files are not regenerated — this bit us in 007) (depends on T006, T007, T010)
- [X] T012 Verify `Application/.../application/mapper/BillMapper.java`, `Application/.../application/mapper/IncomeMapper.java`, `Infrastructure/.../mapper/BillMapper.java`, and `Infrastructure/.../mapper/IncomeMapper.java` auto-map the two new fields by name; add explicit `@Mapping` entries only where MapStruct does not (depends on T002–T005, T011)

**Checkpoint**: Both entities carry the correction fields end-to-end, and the six new endpoints have generated delegate interfaces ready to implement.

---

## Phase 3: User Story 1 — Correct a Mistake in a Bill or Income (Priority: P1) 🎯 MVP

**Goal**: A user can fix a wrong amount/category/date/description on a bill or income from the
dashboard. The original row is never touched; a reversal plus a corrected replacement are posted,
and every total in the app reflects only the corrected value.

**Independent Test**: Record a bill with a wrong amount, correct it via the UI, then verify: the
original bill still returns its original data from `GET /api/v1/bill/{id}`, the Recent Transactions
list shows only the corrected value, and the monthly summary / category spend / budget status /
linked account balance all reflect the corrected amount with no double-counting.

### Tests for User Story 1 ⚠️

> Write these FIRST — they must FAIL before the corresponding implementation tasks.

- [X] T013 [P] [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/CorrectBillServiceImplTest.java` — mock `AddBillPersistencePort` + `GetBillPersistencePort`; assert: (a) the original DTO passed in is never mutated, (b) exactly two rows are written — a reversal with the **negated** original amount, `reversal=true`, `correctsTransactionId=<original id>`, same `categoryId`/`accountId`/`time` as the original, and a replacement with the corrected values, `reversal=false`, `correctsTransactionId=<original id>`, (c) correcting an already-corrected row reverses that row's amount (not the first original's), (d) an amount ≤ 0 in the correction is rejected, (e) correcting a non-existent id throws
- [X] T014 [P] [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/income/CorrectIncomeServiceImplTest.java` — same assertions as T013 for income (with `source` in place of `categoryId`)
- [X] T015 [P] [US1] Write display-filter tests in `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/GetBillServiceImplTest.java` — assert `getAll()` hides (a) any row with `reversal=true`, (b) any row whose id is referenced by another row's `correctsTransactionId`, and shows (c) plain uncorrected rows and (d) only the newest replacement in a two-generation correction chain
- [X] T016 [P] [US1] Write the equivalent display-filter tests in `Domain/src/test/java/at/ymeri/my/finance/domain/service/income/GetIncomeServiceImplTest.java`

### Implementation for User Story 1

- [X] T017 [P] [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/CorrectBillService.java` — `BillDto correctBill(UUID id, BillDto correctedValues)`
- [X] T018 [P] [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/CorrectIncomeService.java` — `IncomeDto correctIncome(UUID id, IncomeDto correctedValues)`
- [X] T019 [US1] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/CorrectBillServiceImpl.java` — load the current row, write a negated-amount reversal + a corrected replacement via `AddBillPersistencePort`, both with `correctsTransactionId` set; validate the corrected amount > 0; throw `NoSuchElementException` for an unknown id and `IllegalStateException` if the target row is already superseded (per the 409 in the contract) (depends on T013)
- [X] T020 [US1] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/income/CorrectIncomeServiceImpl.java` — same as T019 for income (depends on T014)
- [X] T021 [US1] Update `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/GetBillServiceImpl.java` — filter `getAll()` per data-model.md's visibility rule (hide `reversal=true` rows and any row referenced by another row's `correctsTransactionId`); leave `getBillById()` unfiltered so an original stays directly fetchable (depends on T015)
- [X] T022 [US1] Update `Domain/src/main/java/at/ymeri/my/finance/domain/service/income/GetIncomeServiceImpl.java` — same filtering as T021 (depends on T016)
- [X] T023 [P] [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/bill/BillCorrectionController.java` implementing the generated correction delegate's `correctBill` — map 404 for `NoSuchElementException`, 409 for `IllegalStateException`, 400 for `IllegalArgumentException` (depends on T011, T019)
- [X] T024 [P] [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/income/IncomeCorrectionController.java` implementing `correctIncome` with the same error mapping (depends on T011, T020)
- [X] T025 [US1] Create `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BillCorrectionControllerIntegrationTest.java` — full flow against real Postgres: create a bill → `PUT /bills/{id}` → assert the original is unchanged via `GET /bill/{id}`, the corrected value is the only one in `GET /bills`, the monthly summary reflects only the corrected amount (no double-count), and a second correction chains correctly. Include a **cross-month** case: correct a bill's `time` into the following month, then assert the original month's summary drops back as if the bill never existed there AND the new month's summary picks up the corrected amount (spec.md edge case) (depends on T023)
- [X] T026 [US1] Create `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/IncomeCorrectionControllerIntegrationTest.java` — same flow for income (depends on T024)
- [X] T027 [US1] Add correction cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/AccountControllerIntegrationTest.java` — correcting an account-linked bill's amount must move that account's derived balance to reflect only the corrected amount (verifies research.md Decision 1's "zero changes needed to `GetAccountServiceImpl`" claim) (depends on T023)
- [X] T028 [P] [US1] Update `frontend/src/types/index.ts` — add `accountId?`, `source?`, `correctsTransactionId?` to `Transaction`; add `CorrectBillRequest`, `CorrectIncomeRequest`, `TransactionHistoryEntry` (per `contracts/component-contracts.md`)
- [X] T029 [US1] Update `frontend/src/api/client.ts` — add `putAndReturn<T>()` and `del()` helpers alongside the existing `request`/`post`/`postAndReturn`; add `correctBill(id, req)` and `correctIncome(id, req)` (depends on T028)
- [X] T030 [US1] Update `frontend/src/hooks/useDashboardData.ts` — carry `accountId`, `source` (income only), and `correctsTransactionId` through the existing `Bill[]`/`Income[]` → `Transaction[]` mapping (no new fetch) (depends on T028)
- [X] T031 [P] [US1] Create `frontend/src/components/CorrectBillForm.tsx` — mirrors `AddBillForm.tsx` but initializes field state from a `transaction: Transaction | null` prop on open and submits via `correctBill`; amount stays `type="text"` string state (Principle IV); 409 shows "This transaction was already corrected or removed — please refresh and try again" (depends on T028, T029)
- [X] T032 [P] [US1] Create `frontend/src/components/CorrectIncomeForm.tsx` — same as T031 for income, mirroring `AddIncomeForm.tsx` (depends on T028, T029)
- [X] T033 [US1] Update `frontend/src/components/RecentTransactions.tsx` — add a trailing per-row `IconButton` (`MoreVertIcon`) opening an MUI `Menu`; wire a **Correct** item to a new `onCorrect: (t: Transaction) => void` prop; show a small "Corrected" `Chip` on rows whose `correctsTransactionId` is set (depends on T028)
- [X] T034 [US1] Update `frontend/src/App.tsx` — add `correctingTransaction: Transaction | null` state; pass `onCorrect` to `RecentTransactions`; render `CorrectBillForm`/`CorrectIncomeForm` based on `correctingTransaction.type`; on success call the existing `handleSaveSuccess` so every dependent view refreshes (depends on T031, T032, T033)

**Checkpoint**: User Story 1 fully functional — a mistaken bill or income can be corrected end-to-end from the dashboard, with all totals correct and the original preserved.

---

## Phase 4: User Story 2 — Remove a Bill or Income Recorded by Mistake (Priority: P2)

**Goal**: A user can remove a transaction that should never have existed. Same non-destructive
reversal mechanism as a correction, but with no replacement row, and gated behind an explicit
confirmation step.

**Independent Test**: Record a bill, remove it via the UI (confirming the dialog), then verify: the
original still returns its data from `GET /api/v1/bill/{id}`, it no longer appears in Recent
Transactions, and no total (summary, category spend, budget status, account balance) reflects it.

### Tests for User Story 2 ⚠️

- [X] T035 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/RemoveBillServiceImplTest.java` — assert exactly **one** row is written (the negated-amount reversal, `reversal=true`, `correctsTransactionId=<id>`), the original is never mutated, no replacement row is created, removing an already-corrected row reverses its current value, and removing an unknown/already-removed id throws
- [X] T036 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/income/RemoveIncomeServiceImplTest.java` — same assertions for income

### Implementation for User Story 2

- [X] T037 [P] [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/RemoveBillService.java` — `void removeBill(UUID id)`
- [X] T038 [P] [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/api/RemoveIncomeService.java` — `void removeIncome(UUID id)`
- [X] T039 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/RemoveBillServiceImpl.java` — write only the negated-amount reversal; same 404/409 error semantics as correction (depends on T035)
- [X] T040 [US2] Implement `Domain/src/main/java/at/ymeri/my/finance/domain/service/income/RemoveIncomeServiceImpl.java` — same as T039 for income (depends on T036)
- [X] T041 [P] [US2] Add the `removeBill` delegate method to `Application/src/main/java/at/ymeri/my/finance/controller/bill/BillCorrectionController.java` — returns 204, maps 404/409 as in T023 (depends on T023, T039)
- [X] T042 [P] [US2] Add the `removeIncome` delegate method to `Application/src/main/java/at/ymeri/my/finance/controller/income/IncomeCorrectionController.java` (depends on T024, T040)
- [X] T043 [P] [US2] Add removal cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BillCorrectionControllerIntegrationTest.java` — `DELETE /bills/{id}` returns 204, the original is still fetchable and unchanged, it vanishes from `GET /bills`, and the monthly summary drops back as if it never existed (depends on T041)
- [X] T044 [P] [US2] Add removal cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/IncomeCorrectionControllerIntegrationTest.java` (depends on T042)
- [X] T045 [US2] Add removal balance cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/AccountControllerIntegrationTest.java` — removing an account-linked bill restores that account's derived balance to its pre-bill value (depends on T041)
- [X] T046 [US2] Add `removeBill(id)` and `removeIncome(id)` to `frontend/src/api/client.ts` using the `del()` helper from T029 (depends on T029)
- [X] T047 [P] [US2] Create `frontend/src/components/RemoveConfirmDialog.tsx` — shared MUI `Dialog` showing the transaction's description/amount for context, `Button variant="outlined"` Cancel and `Button variant="contained" color="error"` Remove, disabled while submitting (satisfies FR-008's mandatory confirmation) (depends on T028)
- [X] T048 [US2] Update `frontend/src/components/RecentTransactions.tsx` — add a **Remove** item to the row action menu wired to a new `onRemove: (t: Transaction) => void` prop (depends on T033)
- [X] T049 [US2] Update `frontend/src/App.tsx` — add `removingTransaction` state, render `RemoveConfirmDialog`, and on confirm call `removeBill`/`removeIncome` by `transaction.type` then `handleSaveSuccess` (depends on T046, T047, T048)

**Checkpoint**: User Stories 1 and 2 both fully functional — transactions can be corrected or removed, always non-destructively.

---

## Phase 5: User Story 3 — See That a Transaction Was Corrected (Priority: P3)

**Goal**: A user can open a corrected transaction's history and see its prior value(s), so they
trust the number shown.

**Independent Test**: Correct a transaction twice, open its History dialog, and verify both prior
values appear newest-first; open History on a never-corrected transaction and verify it shows
nothing.

### Tests for User Story 3 ⚠️

- [X] T050 [P] [US3] Write history-walk tests in `Domain/src/test/java/at/ymeri/my/finance/domain/service/bill/GetBillServiceImplTest.java` — assert `getHistory(id)` walks `correctsTransactionId` backward newest-first to the original, returns empty for a never-corrected row, excludes reversal rows from the chain, and handles a two-generation chain
- [X] T051 [P] [US3] Write the equivalent history-walk tests in `Domain/src/test/java/at/ymeri/my/finance/domain/service/income/GetIncomeServiceImplTest.java`

### Implementation for User Story 3

- [X] T052 [P] [US3] Add `List<BillDto> getHistory(UUID id)` to `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetBillService.java` and implement it in `Domain/src/main/java/at/ymeri/my/finance/domain/service/bill/GetBillServiceImpl.java` per research.md Decision 4 (depends on T050)
- [X] T053 [P] [US3] Add `List<IncomeDto> getHistory(UUID id)` to `Domain/src/main/java/at/ymeri/my/finance/domain/api/GetIncomeService.java` and implement it in `Domain/src/main/java/at/ymeri/my/finance/domain/service/income/GetIncomeServiceImpl.java` (depends on T051)
- [X] T054 [P] [US3] Add the `getBillHistory` delegate method to `Application/src/main/java/at/ymeri/my/finance/controller/bill/BillCorrectionController.java` (depends on T023, T052)
- [X] T055 [P] [US3] Add the `getIncomeHistory` delegate method to `Application/src/main/java/at/ymeri/my/finance/controller/income/IncomeCorrectionController.java` (depends on T024, T053)
- [X] T056 [P] [US3] Add history assertions to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BillCorrectionControllerIntegrationTest.java` — after two corrections, `GET /bills/{id}/history` returns both prior values newest-first (depends on T054)
- [X] T057 [P] [US3] Add history assertions to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/IncomeCorrectionControllerIntegrationTest.java` (depends on T055)
- [X] T058 [US3] Add `fetchBillHistory(id)` and `fetchIncomeHistory(id)` to `frontend/src/api/client.ts` (depends on T029)
- [X] T059 [P] [US3] Create `frontend/src/components/TransactionHistoryDialog.tsx` — shared MUI `Dialog` rendering a `List` of history entries (amount, date, description) newest-first, with a `Skeleton` loading state (depends on T028)
- [X] T060 [US3] Update `frontend/src/components/RecentTransactions.tsx` — add a **History** item to the row action menu wired to a new `onHistory: (t: Transaction) => void` prop (depends on T048)
- [X] T061 [US3] Update `frontend/src/App.tsx` — add `viewingHistoryFor` state plus fetched-history state; on open, fetch via `fetchBillHistory`/`fetchIncomeHistory` by `transaction.type` and render `TransactionHistoryDialog` (depends on T058, T059, T060)

**Checkpoint**: All three user stories functional — correct, remove, and inspect correction history.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verify the whole feature builds, all suites pass, and the manual flows in
`quickstart.md` behave correctly.

- [X] T062 [P] Run `./mvnw -pl Domain test` — confirm all new correction/removal/filter/history unit tests and every pre-existing Domain test pass
- [X] T063 [P] Run `./mvnw -pl integration-tests test` — confirm the two new correction integration test classes and all pre-existing integration tests (including the 007 account-balance ones) pass
- [X] T064 [P] Run `cd frontend && npm run build` — confirm TypeScript type-check and Vite build complete with zero errors
- [ ] T065 Manual smoke-test per `quickstart.md` — verify correction, removal, history, the cross-month correction edge case (change a transaction's date into another month and confirm both months' totals adjust), and that account balances follow corrections/removals
- [ ] T066 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows the `billCorrection` and `incomeCorrection` tags with all six new endpoints
- [X] T067 [P] Clarify the deletion-blocked messages in `Domain/src/main/java/at/ymeri/my/finance/domain/service/account/DeleteAccountServiceImpl.java` and `Domain/src/main/java/at/ymeri/my/finance/domain/service/category/DeleteCategoryServiceImpl.java` — a removed/corrected transaction still pins its account/category (retained permanently per Principle I), so the message must say so rather than implying a visible transaction exists (per `/speckit-analyze` finding F1; the blocking behaviour itself is intentional and unchanged — see plan.md Principle V note)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **blocks all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 only
- **US2 (Phase 4)**: Depends on Phase 2; its controller tasks (T041/T042) extend the controllers US1 creates (T023/T024), and its frontend tasks extend US1's menu (T033) and API helpers (T029)
- **US3 (Phase 5)**: Depends on Phase 2; extends the same controllers and menu as US1/US2
- **Polish (Phase 6)**: Depends on all desired user story phases

### User Story Dependencies

- **US1 (P1)**: Independent once Phase 2 is done — the MVP
- **US2 (P2)**: Backend logic is fully independent of US1 (its own services and tests), but it
  *appends to* the controllers and frontend surfaces US1 introduces, so in practice it follows US1
  rather than running truly parallel to it
- **US3 (P3)**: Same — independent Domain logic (`getHistory`), but attaches to US1's controllers
  and row menu. Its history walk is only meaningful once corrections exist (US1)

### Within Each User Story

- Tests are written and MUST fail before the matching implementation
- Domain API interface → Domain service impl → Application controller → integration test
- Frontend types → API client → components → `App.tsx` wiring

### Parallel Opportunities

- **Phase 2**: T002–T009 are eight different files — fully parallel; then T010 → T011 → T012 in sequence
- **US1 tests**: T013–T016 — four different test files, fully parallel
- **US1 impl**: T017/T018 parallel; T019/T020 parallel (different files); T023/T024 parallel; T031/T032 parallel
- **US2**: T035/T036 parallel; T037/T038 parallel; T041/T042 parallel; T043/T044 parallel
- **US3**: T050/T051 parallel; T052/T053 parallel; T054/T055 parallel; T056/T057 parallel
- **Polish**: T062, T063, T064, T066 — independent verification steps, fully parallel
- Bill-side and income-side work is symmetric throughout and can be split between two developers

---

## Parallel Example: Phase 2 → US1

```text
# Phase 2 — run simultaneously (eight different files):
T002: BillDto fields          T003: IncomeDto fields
T004: BillEntity columns      T005: IncomeEntity columns
T006: bill-model.yaml         T007: income-model.yaml
T008: bill-correction ctrl    T009: income-correction ctrl

# Then sequentially:
T010: pom.xml executions  →  T011: generate-sources  →  T012: verify mappers

# US1 tests — run simultaneously (four different files):
T013: CorrectBillServiceImplTest     T014: CorrectIncomeServiceImplTest
T015: GetBillServiceImplTest         T016: GetIncomeServiceImplTest

# Then implementation, bill-side and income-side in parallel:
T017 → T019 → T023 → T025      (bill)
T018 → T020 → T024 → T026      (income)
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (confirm — no work)
2. Phase 2: Foundational (T002–T012) — fields, contracts, generated interfaces
3. Phase 3: US1 (T013–T034) — correction end-to-end
4. **STOP and VALIDATE**: correct a bill and an income; verify the original is untouched and every total reflects only the corrected value
5. Ship/demo — this alone closes the "no way to fix a mistake without Swagger" gap

### Incremental Delivery

1. Phase 1 + 2 → correction fields and contracts in place
2. Phase 3 (US1) → correct a mistake ← **MVP**
3. Phase 4 (US2) → remove a bad entry entirely
4. Phase 5 (US3) → see correction history
5. Phase 6 → full build/test verification and manual smoke test

---

## Notes

- `[P]` tasks touch different files with no unmet dependencies — safe to run simultaneously
- **The single most important invariant to test**: a reversal's amount is the exact negation of what
  it reverses, and reversals keep the same `categoryId`/`accountId`/`time` as their target. This is
  what makes `GetSpendingAnalysisServiceImpl`, `GetBudgetStatusServiceImpl`, and
  `GetAccountServiceImpl` net corrections out with **zero changes** (research.md Decision 1) — if a
  reversal's category or account drifts, per-category and per-account totals silently break even
  though the grand total still looks right
- Reversal rows MUST only ever be created by the new correction/removal services — never reachable
  through `AddBillService`/`AddIncomeService` (Principle IV: no user-supplied negative amounts)
- `getBillById()`/`getIncomeById()` stay **unfiltered** so an original remains directly fetchable
  (this is what the integration tests assert to prove Principle I compliance); only `getAll()` filters
- Amount fields in the correction forms MUST use `type="text"` with string state, never
  `type="number"` (Constitution Principle IV, same rule as every existing form)
- Watch out for `skipOverwrite=true` in `Application/pom.xml` — regenerating does **not** update
  already-generated model files. T011 explicitly handles this; it cost time in 007
