# Tasks: Accounts Page

**Input**: Design documents from `specs/007-accounts-page/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

**Tests**: Included — Constitution Principle VI mandates unit tests for the new domain logic
(derived balance calculation in `GetAccountServiceImpl`) and integration tests against a real
database (no mocks). Frontend-only tasks follow the 005 precedent (no test tasks required —
verified via TypeScript type-check + manual smoke test).

**Organization**: Tasks are grouped by user story to enable independent implementation and
testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- Domain services: `Domain/src/main/java/at/ymeri/my/finance/domain/service/account/`
- Domain tests: `Domain/src/test/java/at/ymeri/my/finance/domain/service/account/`
- Swagger: `Application/src/main/resources/swagger/bill/`
- Integration tests: `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/`
- Frontend types: `frontend/src/types/index.ts`
- Frontend API client: `frontend/src/api/client.ts`
- Frontend hooks: `frontend/src/hooks/`
- Frontend components: `frontend/src/components/`

---

## Phase 1: Setup

**Purpose**: Confirm prerequisites — no new npm packages, Maven dependencies, or directories are required.

- [X] T001 Confirm no new frontend dependencies needed (MUI already installed per 006) and no new Maven dependencies needed (Spring Data JPA / MapStruct already present); confirm `frontend/src/hooks/` and `frontend/src/components/` exist

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared frontend types and API client functions that User Stories 1, 2, 3, and 4 all depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Update `frontend/src/types/index.ts` — add `AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'CASH' | 'INVESTMENT'`; add `Account` interface (`id`, `name`, `type`, `currencies: string[]`, `defaultCurrency`, `balance: number`, `institution?`); add `CreateAccountRequest` interface (`name`, `type`, `currencies`, `defaultCurrency`, `balance?`, `institution?`); add `accountId?: string` to both `CreateBillRequest` and `CreateIncomeRequest`
- [X] T003 [P] Update `frontend/src/api/client.ts` — add `fetchAccounts(): Promise<Account[]>` (GET `/api/v1/accounts`, unwraps `{ accounts: [...] }`) and `createAccount(req: CreateAccountRequest): Promise<Account>` (POST `/api/v1/accounts` via `postAndReturn`)

**Checkpoint**: Types and API client ready — all four user stories can now proceed.

---

## Phase 3: User Story 1 — View All Accounts and Their Balances (Priority: P1) 🎯 MVP

**Goal**: Users can navigate to an Accounts page and see all existing accounts with name, type,
institution, and a correctly **derived** balance (starting balance + linked incomes − linked
bills). Loading, empty, and error states are all handled.

**Independent Test**: Seed one or more accounts via the API (Swagger/curl), link an income to one
via the existing `POST /api/v1/incomes` `accountId` field, navigate to the Accounts page, and
verify the displayed balance equals starting balance + that income's amount.

### Tests for User Story 1 ⚠️

> Write these tests FIRST — they must FAIL before T005 is implemented.

- [X] T004 [P] [US1] Write `GetAccountServiceImplTest.java` in `Domain/src/test/java/at/ymeri/my/finance/domain/service/account/GetAccountServiceImplTest.java` — mock `GetAccountPersistencePort`, `GetBillPersistencePort`, `GetIncomePersistencePort`; test cases: (a) account with no linked bills/incomes returns its starting balance unchanged, (b) null starting balance is treated as zero, (c) linked incomes only → balance = starting + Σincomes, (d) linked bills only → balance = starting − Σbills, (e) mixed bills+incomes computed correctly, (f) bills/incomes belonging to a *different* `accountId` are excluded from the sum, for all three methods (`getAll`, `getByType`, `getAccountById`)

### Implementation for User Story 1

- [X] T005 [US1] Modify `Domain/src/main/java/at/ymeri/my/finance/domain/service/account/GetAccountServiceImpl.java` — add `GetBillPersistencePort` and `GetIncomePersistencePort` constructor dependencies; add a private `deriveBalance(AccountDto)` helper implementing the formula from T004; apply it to the result(s) of `getAll()`, `getByType()`, and `getAccountById()` before returning (depends on T004 failing first)
- [X] T006 [US1] Add balance-derivation test cases to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/AccountControllerIntegrationTest.java` — create an account with a starting balance via `POST /accounts`, create an income linked to it via `POST /incomes` (`accountId` already supported today), `GET /accounts/{id}`, assert the returned `balance` equals starting balance + income amount (depends on T005)
- [X] T007 [P] [US1] Create `frontend/src/hooks/useAccounts.ts` — mirrors `frontend/src/hooks/useCategories.ts`: `useAccounts(type?: AccountType)` returning `{ accounts, loading, error, refresh }`, fetches via `fetchAccounts()` on mount and on `refresh()`/`type` change (depends on T002, T003)
- [X] T008 [P] [US1] Create `frontend/src/components/AccountList.tsx` — MUI `List` + `ListItem` + `ListItemText` (`primary={name}`, `secondary="{type} · {institution ?? '—'}"`); trailing balance `Typography` formatted with `Intl.NumberFormat('de-AT', { style: 'currency', currency: account.defaultCurrency })`, coloured `error.main` when negative else default; `Skeleton` loading state; `Alert severity="error"` error state; empty state (`Typography` "No accounts yet." + CTA `Button`); "Add Account" `Button variant="contained" startIcon={<AddIcon />}` wired to an `onAddClick` prop; no type filter yet (added in US4) (depends on T002)
- [X] T009 [US1] Create `frontend/src/components/AccountsPage.tsx` — mirrors `frontend/src/components/CategoriesPage.tsx`: owns a `useAccounts()` instance, header with "Accounts" title (MUI `Typography variant="h5"`) and back `IconButton` (ArrowBack icon), renders `AccountList` with accounts/loading/error/onAddClick props (`onAddClick` is a no-op placeholder until US2 wires `AddAccountForm`) (depends on T007, T008)
- [X] T010 [US1] Update `frontend/src/App.tsx` — widen `view` state to `'dashboard' | 'categories' | 'accounts'`; add an "Accounts" `Button variant="outlined"` in the `Toolbar` next to the existing "Categories" button; conditionally render `AccountsPage` (with `onBack={() => setView('dashboard')}`) (depends on T009)

**Checkpoint**: User Story 1 fully functional — navigate to Accounts, see the list with correctly derived balances sourced from the backend (accounts/incomes seeded via the API, since the create-UI doesn't exist yet — mirrors the 005 precedent).

---

## Phase 4: User Story 2 — Create a New Account (Priority: P1)

**Goal**: Users can open a form from the Accounts page, fill in name, type, and currency
(required), optionally set a starting balance and institution, and submit to create a new account
that immediately appears in the list.

**Independent Test**: Open `AddAccountForm`; submit with name, type, and currency only — verify
new item appears with balance 0; submit with all fields — verify starting balance and institution
are saved; submit with empty name — verify inline error; submit with no type/currency selected —
verify inline error; submit a duplicate name — verify server error banner.

### Implementation for User Story 2

- [X] T011 [P] [US2] Create `frontend/src/components/AddAccountForm.tsx` — mirrors `frontend/src/components/AddCategoryForm.tsx`: MUI `Dialog` (via existing `Modal`) with `TextField` name (required), `Select`+`MenuItem` type (CHECKING/SAVINGS/CREDIT_CARD/CASH/INVESTMENT, required), `TextField` defaultCurrency (required, free-text ISO 4217 code, also seeds the single-element `currencies` array), `TextField type="text" inputMode="decimal"` starting balance (optional, string state per Principle IV, omitted from request if empty), `TextField` institution (optional); inline `helperText` errors on name/type/currency; `Button variant="contained"` submit / `variant="outlined"` cancel; `Alert severity="error"` for 409 ("An account with this name already exists") and generic server errors (depends on T002, T003)
- [X] T012 [US2] Update `frontend/src/components/AccountsPage.tsx` — add `addFormOpen` boolean state; wire the "Add Account" button to open `AddAccountForm`; auto-open the form when the account list finishes loading and is empty (mirrors `CategoriesPage`'s empty-state auto-open behaviour); on `onSuccess(created)`, append `created` to the local accounts list without a re-fetch (depends on T009, T011)

**Checkpoint**: User Stories 1 and 2 both fully functional — view accounts and create new ones end-to-end, entirely through the UI.

---

## Phase 5: User Story 3 — Select an Account When Recording a Bill or Income (Priority: P2)

**Goal**: The Add Bill and Add Income forms gain an optional account selector; submitting with an
account selected links the new transaction to it, and that account's derived balance (from US1)
reflects the change on the Accounts page.

**Independent Test**: Create an account (via US2's form or the API). Open "+ Add Expense", select
that account, submit a bill — verify it succeeds and the account's balance on the Accounts page
decreases by the bill amount. Repeat with "+ Add Income" and verify the balance increases. Verify
leaving the selector on "None" behaves exactly as before this feature (no account, no balance change).

### Implementation for User Story 3

- [X] T013 [US3] Add an optional `accountId` (string) property to the `bill` schema in `Application/src/main/resources/swagger/bill/bill-model.yaml` (see `contracts/bill-model.yaml` for the target state) — additive, non-breaking change per Constitution Principle VII
- [X] T014 [US3] Run `./mvnw -pl Application generate-sources` to regenerate the `Bill` API model with `accountId`; verify `Application/src/main/java/at/ymeri/my/finance/application/mapper/BillMapper.java` requires no changes (MapStruct auto-maps `accountId` by field name since `BillDto` already has it) (depends on T013)
- [X] T015 [US3] Add bill-linkage balance-derivation test cases (plus a no-`accountId` regression case per /speckit-analyze finding C1) to `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/AccountControllerIntegrationTest.java` — create an account, create a bill linked to it via `POST /createBill` with the new `accountId` field, `GET /accounts/{id}`, assert the returned `balance` equals starting balance − bill amount; add one more case combining a linked bill and a linked income on the same account (depends on T005, T014)
- [X] T016 [US3] Update `frontend/src/hooks/useDashboardData.ts` and the `DashboardData` interface in `frontend/src/types/index.ts` — fetch accounts once (alongside the existing one-time `categories` fetch) and expose an `accounts: Account[]` field, so `AddBillForm`/`AddIncomeForm` receive accounts as a prop instead of fetching independently (mirrors the existing `categories` wiring) (depends on T002, T003)
- [X] T017 [P] [US3] Update `frontend/src/components/AddBillForm.tsx` — add `accounts: Account[]` prop; add an "Account" `Select`+`MenuItem` field (positioned after the existing Category select) with `<MenuItem value="">No account</MenuItem>` default; send `accountId: accountId || undefined` in the `CreateBillRequest` (depends on T002, T013, T014)
- [X] T018 [P] [US3] Update `frontend/src/components/AddIncomeForm.tsx` — add `accounts: Account[]` prop; add an "Account" `Select`+`MenuItem` field (positioned after the existing Source select) with `<MenuItem value="">No account</MenuItem>` default; send `accountId: accountId || undefined` in the `CreateIncomeRequest` (depends on T002)
- [X] T019 [US3] Update `frontend/src/App.tsx` — pass `accounts` from `useDashboardData` into both `<AddBillForm>` and `<AddIncomeForm>` (depends on T016, T017, T018)

**Checkpoint**: User Stories 1–3 fully functional — accounts can be viewed, created, and linked to new bills/incomes, with balances updating correctly end-to-end.

---

## Phase 6: User Story 4 — Filter Accounts by Type (Priority: P3)

**Goal**: Users can filter the Accounts list to show only accounts of a chosen type.

**Independent Test**: With accounts of multiple types in the list, select the "CREDIT_CARD" filter
— verify only credit card accounts are shown; clear the filter — verify all accounts reappear.

### Implementation for User Story 4

- [X] T020 [US4] Update `frontend/src/components/AccountList.tsx` — add `activeTypeFilter: AccountType | 'ALL'` and `onTypeFilterChange` props; render a `ToggleButtonGroup` (ALL / CHECKING / SAVINGS / CREDIT_CARD / CASH / INVESTMENT, `exclusive`, `size="small"`); apply client-side filtering to the rendered list (depends on T008)
- [X] T021 [US4] Update `frontend/src/components/AccountsPage.tsx` — add `activeTypeFilter` state (default `'ALL'`); pass filter props to `AccountList` (depends on T009, T020)

**Checkpoint**: All four user stories functional — view, create, link, and filter accounts.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verify the full feature builds, all tests pass, and the manual smoke test from `quickstart.md` succeeds.

- [X] T022 [P] Run `./mvnw -pl Domain test` — confirm `GetAccountServiceImplTest` and all existing Domain tests pass
- [X] T023 [P] Run `./mvnw -pl integration-tests test` — confirm `AccountControllerIntegrationTest` (including new balance-derivation cases) and all existing integration tests pass
- [X] T024 [P] Run `cd frontend && npm run build` — confirm TypeScript type-check and Vite build complete with zero errors
- [ ] T025 Manual smoke-test per `quickstart.md` — verify starting balance, linked-income, and linked-bill balance math end-to-end via the UI; verify empty-accounts state; verify negative-balance colour-coding; verify the type filter; verify "No account" still works unchanged on both Add Bill and Add Income
- [ ] T026 [P] Verify Swagger UI at `http://localhost:8080/swagger-ui.html` shows the updated `bill` schema with the new `accountId` field

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **blocks all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 — T004 → T005 → T006; T007, T008 can run in parallel with each other and with the backend tasks; T009 depends on T007+T008; T010 depends on T009
- **US2 (Phase 4)**: Depends on Phase 2 and on US1's `AccountsPage` (T009) — T011 can be built in parallel with Phase 3; T012 depends on T009 and T011
- **US3 (Phase 5)**: Depends on Phase 2 and on US1's derived-balance logic (T005) — T013 → T014 → T015; T016 depends on Phase 2; T017/T018 can run in parallel; T019 depends on T016–T018
- **US4 (Phase 6)**: Depends on US1's `AccountList`/`AccountsPage` (T008, T009)
- **Polish (Phase 7)**: Depends on all desired user story phases being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependency on US2/US3/US4
- **US2 (P1)**: Can start after Phase 2, but T012 needs US1's `AccountsPage` (T009) to exist to wire the button into
- **US3 (P2)**: Can start after Phase 2, but its integration test (T015) and its whole purpose depend on US1's derived-balance logic (T005) already existing; does **not** depend on US2 (an account can be seeded via the API for testing)
- **US4 (P3)**: Depends on US1's `AccountList` (T008) and `AccountsPage` (T009) already existing, since it only adds a filter on top of them

### Parallel Opportunities

- T002, T003 (Phase 2) — different files, fully parallel
- T004 (test) is written first, alone; T007, T008 (US1 frontend) can run in parallel with T005/T006 (US1 backend) since they touch different files/modules
- T017, T018 (US3 forms) — different files, parallel with each other
- T022, T023, T024, T026 (Polish) — independent verification steps, fully parallel

---

## Parallel Example: Phase 2 → US1

```text
# Run simultaneously:
T002: Update frontend/src/types/index.ts
T003: Update frontend/src/api/client.ts

# Then, in parallel:
T004: Write GetAccountServiceImplTest.java        (backend)
T007: Create frontend/src/hooks/useAccounts.ts    (frontend)
T008: Create frontend/src/components/AccountList.tsx (frontend)

# Then sequentially:
T005: Implement GetAccountServiceImpl.java changes (needs T004 to fail first)
T006: Add integration test cases                   (needs T005)
T009: Create AccountsPage.tsx                       (needs T007 + T008)
T010: Update App.tsx                                (needs T009)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — both P1)

1. Complete Phase 1: Setup (confirm, no work needed)
2. Complete Phase 2: Foundational — T002, T003 in parallel
3. Complete Phase 3 (US1): backend derivation logic + read-only Accounts page
4. Complete Phase 4 (US2): create-account form wired in
5. **STOP and VALIDATE**: Accounts page shows list + create form works end-to-end, balances derive correctly from seeded transactions
6. Add Phase 5 (US3) account-linking and Phase 6 (US4) filtering when ready

### Incremental Delivery

1. Phase 1 + 2 → types and API client wired
2. Phase 3 → read-only Accounts page with correct derived balances (US1 MVP)
3. Phase 4 → add create-account form (US2 completes the core CRUD-lite loop)
4. Phase 5 → Add Bill/Add Income can link to an account; balances update live (US3)
5. Phase 6 → type filtering (US4 enhancement)
6. Phase 7 → full build/test verification and manual smoke test

---

## Notes

- `[P]` tasks touch different files and have no unmet dependencies — safe to run simultaneously
- Balance derivation (T004/T005) is the one piece of real new business logic in this feature —
  everything else is contract exposure (T013/T014) or frontend surface mirroring 005's pattern
- Starting-balance and account-creation-balance `TextField`s MUST use `type="text"` with string
  state, never `type="number"` (Constitution Principle IV, same rule as existing amount fields)
- `AddAccountForm` must NOT independently fetch accounts for a "parent account" type selector —
  accounts have no hierarchy concept (unlike categories); no such field exists
- `AddBillForm`/`AddIncomeForm` must receive `accounts` as a prop from `App.tsx`/`useDashboardData`,
  not fetch independently — mirrors how `categories` is already threaded through today
- Submit buttons must be disabled during `submitting` state to prevent duplicate account creation
  (same edge case already handled by `AddCategoryForm`)
