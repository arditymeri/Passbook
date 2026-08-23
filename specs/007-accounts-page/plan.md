# Implementation Plan: Accounts Page

**Branch**: `007-accounts-page` | **Date**: 2026-08-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-accounts-page/spec.md`

## Summary

Add an Accounts page to the frontend (list + create, mirroring the 005 Categories pattern) that
shows each account's current balance, and let the Add Bill / Add Income forms optionally link a
new transaction to an account. The account, bill, and income persistence layers already support
`accountId` end-to-end (the `bill` and `income` tables already have an `account_id` column, and
`GetAccountPersistencePort`/`DeleteAccountPersistencePort` already reference it) — most of this
feature is Application-layer contract exposure and new frontend surface. The one real backend
behavior change is `GetAccountServiceImpl`: an account's balance becomes a value **derived** at
read time (starting balance + linked incomes − linked bills) instead of the raw stored column,
per Constitution Principle III.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 4.6 / React 18.2 (frontend, Vite 3 bundler)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern) — backend; existing `@mui/material` v5 component set — frontend (no new frontend dependencies)

**Storage**: PostgreSQL — existing `account`, `bill`, `income` tables; no schema migration required (`bill.account_id` and `income.account_id` columns already exist)

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration) — backend; TypeScript type-check (`tsc --noEmit`) + manual browser smoke-test — frontend

**Target Platform**: Linux server (Docker Compose stack) + web browser, desktop-primary

**Project Type**: Web application (hexagonal-architecture backend, Maven multi-module + React SPA frontend)

**Performance Goals**: Accounts list (including derived balances) renders in under 2 seconds on personal-scale data (hundreds of accounts/transactions, not thousands)

**Constraints**:
- `BigDecimal` throughout Domain and Infrastructure for all monetary values (Principle IV) — no new `double`/`float` fields introduced
- Domain module stays framework-free; balance derivation logic lives in `GetAccountServiceImpl` composed from existing SPI ports (Principle VIII)
- Adding `accountId` to the `bill` OpenAPI schema is additive only — no breaking change, no new API version (Principle VII)
- Amount fields in any new/changed frontend forms MUST use `type="text"` with string state, matching the existing 004/006 pattern (Principle IV at the UI boundary)
- No account edit/delete UI in this feature (mirrors 005 scope; backend already supports both for a future feature)

**Scale/Scope**: 3 new frontend components (`AccountsPage`, `AccountList`, `AddAccountForm`), 1 new hook (`useAccounts`), 2 existing forms modified (`AddBillForm`, `AddIncomeForm`), 1 OpenAPI schema field addition (`bill.accountId`), 1 Domain service modified (`GetAccountServiceImpl`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | No editing or deleting of bills/incomes is introduced; this feature only adds an optional foreign key at creation time. |
| II. Double-Entry Accounting | ✅ N/A | Bills/incomes remain single-entry records, consistent with the existing (pre-007) domain model — not introduced or changed by this feature. |
| III. Account Integrity & Balance Derivation | ✅ Pass | This feature's core purpose: `GetAccountServiceImpl` derives the displayed balance by summing linked bill/income amounts against the starting balance, rather than trusting a stored running total. |
| IV. Currency Precision | ✅ Pass | All monetary values stay `BigDecimal` end-to-end in Domain/Infrastructure. Pre-existing `format: double` on the OpenAPI wire schema for `bill`/`income`/`account` monetary fields is prior tech debt, not introduced or worsened by this feature — out of scope to fix here. |
| V. Audit Trail | ✅ N/A | No new state-changing operation; bill/income creation already goes through existing (unlogged) create flows. |
| VI. Test-First Development | ⚠ Required | `GetAccountServiceImpl`'s new derived-balance logic MUST have Domain unit tests (zero/some linked transactions, mixed bills+incomes) before/alongside implementation. Integration tests MUST cover account creation → linked bill/income → balance reflects the change. |
| VII. API Contract Stability | ✅ Pass | `accountId` added to the `bill` OpenAPI schema is a purely additive, optional field — non-breaking, no `/v2` needed. `account` and `income` schemas are unchanged. |
| VIII. Hexagonal Architecture Compliance | ✅ Pass | Balance derivation is added to `GetAccountServiceImpl` (Domain), composed from two existing Domain SPI ports (`GetBillPersistencePort`, `GetIncomePersistencePort`). No Spring/JPA leaks into Domain. |

**Gate decision**: PASS. Test-First (VI) is flagged as mandatory, not optional, matching the precedent set by 001 and 002.

## Project Structure

### Documentation (this feature)

```text
specs/007-accounts-page/
├── plan.md                      # This file
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   ├── bill-model.yaml          # Updated OpenAPI schema snapshot (adds accountId)
│   └── component-contracts.md   # New/changed frontend component & hook contracts
└── tasks.md                     # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/bill/
└── bill-model.yaml                              # MODIFY — add `accountId` to the shared `bill` schema

Domain/src/main/java/at/ymeri/my/finance/domain/service/account/
└── GetAccountServiceImpl.java                   # MODIFY — inject GetBillPersistencePort +
                                                  #          GetIncomePersistencePort; derive balance

Domain/src/test/java/at/ymeri/my/finance/domain/service/account/
└── GetAccountServiceImplTest.java               # NEW — unit tests for derived-balance logic

integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/
└── AccountControllerIntegrationTest.java        # MODIFY — add balance-derivation test cases

frontend/src/
├── types/index.ts                               # MODIFY — add Account, AccountType,
│                                                 #          CreateAccountRequest; add accountId to
│                                                 #          CreateBillRequest/CreateIncomeRequest
├── api/client.ts                                # MODIFY — add fetchAccounts, createAccount
├── hooks/
│   └── useAccounts.ts                           # NEW — mirrors useCategories.ts
├── components/
│   ├── AccountsPage.tsx                         # NEW — mirrors CategoriesPage.tsx
│   ├── AccountList.tsx                          # NEW — mirrors CategoryList.tsx
│   ├── AddAccountForm.tsx                       # NEW — mirrors AddCategoryForm.tsx
│   ├── AddBillForm.tsx                          # MODIFY — add optional account Select
│   └── AddIncomeForm.tsx                        # MODIFY — add optional account Select
└── App.tsx                                      # MODIFY — add 'accounts' view state + nav button
```

**Structure Decision**: Web application (existing hexagonal-architecture backend + React SPA
frontend). No new modules or directories at the top level — all backend changes stay within the
existing `Application`/`Domain`/`integration-tests` modules, and all frontend changes stay within
`frontend/src/`, following the file layout established by 001 (backend contract feature) and 005
(frontend list+create feature).

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
