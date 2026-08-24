# Implementation Plan: Bill/Income Correction Flow

**Branch**: `008-bill-income-correction` | **Date**: 2026-08-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-bill-income-correction/spec.md`

## Summary

Let users correct or remove a bill/income shown in the dashboard's Recent Transactions list without
ever mutating or deleting the original record. Both actions post a **reversal** — a new Bill/Income
row with a negated amount, the same category/account, flagged `reversal=true`, referencing the
transaction it corrects via `correctsTransactionId`. A correction additionally posts a normal
**replacement** row with the fixed values. Because every existing aggregation service
(`GetSpendingAnalysisServiceImpl`, `GetBudgetStatusServiceImpl`, `GetAccountServiceImpl`) already
sums bill/income amounts with a plain `BigDecimal::add` reduction, a negative-amount reversal
**automatically nets to zero everywhere with no changes to those three services** — the only
display-layer change needed is filtering `GetBillServiceImpl.getAll()` /
`GetIncomeServiceImpl.getAll()` (the human-facing list endpoints) to hide reversal rows and any row
that has been superseded by a later correction.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 4.6 / React 18.2 (frontend)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Data JPA, MapStruct, OpenAPI Generator (delegate pattern) — backend; existing `@mui/material` v5 — frontend (no new dependencies)

**Storage**: PostgreSQL — `bill` and `income` tables gain two nullable columns each (`corrects_transaction_id`, `reversal`); no other schema changes

**Testing**: JUnit 5, Mockito (unit), TestContainers (integration) — backend; TypeScript type-check + manual smoke-test — frontend

**Target Platform**: Linux server (Docker Compose stack) + web browser, desktop-primary

**Project Type**: Web application (hexagonal-architecture backend, Maven multi-module + React SPA frontend)

**Performance Goals**: Correction/removal completes and all dependent views refresh in under 1 second on personal-scale data (matches 007's precedent)

**Constraints**:
- `BigDecimal` throughout Domain/Infrastructure (Principle IV); the one deliberate exception is that
  a **system-generated reversal row's amount is negative** — never user-input, never reachable
  through the existing public create endpoints (`AddBillService`/`AddIncomeService`), only produced
  internally by the new correction/removal services. This is what lets every existing summation
  code path net reversals out with zero changes.
- Domain module stays framework-free; correction/removal/history logic lives in new Domain services
  composed from the **existing** `AddBillPersistencePort`/`AddIncomePersistencePort` (to write the
  reversal+replacement rows) and `GetBillPersistencePort`/`GetIncomePersistencePort` (to read)
  (Principle VIII). **Revised during PR review:** the SPI surface did grow — a correction writes two
  rows, so it needs a transaction boundary and a locked read to stay correct under concurrency.
  Both are expressed as ports rather than framework annotations in Domain: a new `UnitOfWork` port
  (implemented by `SpringUnitOfWork` in Infrastructure) and `lockBillById`/`lockIncomeById` added to
  the two existing get-ports. Domain imports no transaction API.
- New endpoints follow the verb conventions the app already uses for Account (`PUT` = replace
  current value, `DELETE` = remove) rather than inventing custom action sub-resources, for API
  consistency (Principle VII: additive, non-breaking — no existing endpoint's contract changes
  shape, only `GET /bills` and `GET /incomes`' *returned set* changes to exclude reversal/superseded
  rows, which is the intended new behavior, not a breaking schema change)
- Amount fields in the new correction form MUST use `type="text"` with string state, matching every
  existing form (Principle IV at the UI boundary)
- Correction/removal is only exposed for transactions currently visible in the dashboard's Recent
  Transactions list — no new transaction browser/search UI (per spec Assumptions)

**Scale/Scope**: 2 entity types (Bill, Income) × 3 new operations each (correct, remove, history) = 6 new endpoints; ~6 new Domain service classes; 2 new DTO fields × 2 entities; frontend gains a per-row action menu, 2 correction forms, 1 confirm dialog, 1 history dialog

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Transaction Immutability | ✅ Pass | This feature exists specifically to implement Principle I correctly — corrections and removals never touch the original row; every effect is a new, reversal-linked row. |
| II. Double-Entry Accounting | ✅ N/A | Bills/incomes remain single-entry records — unchanged, pre-existing pattern (see 001/002/007's Constitution Checks for the same call). |
| III. Account Integrity & Balance Derivation | ✅ Pass | `GetAccountServiceImpl`'s derived-balance sum (from 007) requires **zero code changes** — a negative-amount reversal linked to the same `accountId` nets out automatically. |
| IV. Currency Precision | ✅ Pass, with a documented exception | `BigDecimal` throughout. The negative-amount reversal is a deliberate, system-only exception (see Constraints above) — never exposed to a user as an input, always produced by the new correction/removal services. Pre-existing `format: double` wire-level gap on `bill`/`income`/`account` schemas (flagged in 007) is untouched, out of scope. |
| V. Audit Trail | ✅ Pass | `correctsTransactionId` + `reversal` give a queryable, permanent trail from any corrected/removed row back to what it replaced. Dedicated structured logging of the mutation event itself is a pre-existing gap across all create endpoints in this app (not introduced or worsened here) — out of scope. **Note:** because retained rows keep their `accountId`/`categoryId` forever, the existing `DeleteAccountServiceImpl`/`DeleteCategoryServiceImpl` reference blocks remain correct after a removal and are deliberately left unchanged — a removed transaction still pins its account/category, preventing a dangling reference in the audit trail. Only the blocking *message* is clarified (see T067). |
| VI. Test-First Development | ⚠ Required | New Domain logic (correction, removal, display filtering, history reconstruction) MUST have unit tests written first; integration tests MUST cover the full correct/remove/history flow against a real database. |
| VII. API Contract Stability | ✅ Pass | All new endpoints defined in OpenAPI YAML first. `GET /bills`/`GET /incomes` response *schema* is unchanged (two new optional response fields, additive); only the *set of rows returned* changes, which is this feature's intended behavior. |
| VIII. Hexagonal Architecture Compliance | ✅ Pass (re-checked post-review) | Atomicity and row locking are mediated by ports — `UnitOfWork` (new, implemented in Infrastructure) and `lockBillById`/`lockIncomeById` on the existing get-ports — so Domain carries no `@Transactional` and no `spring-tx` dependency. An earlier fix annotated the Domain services directly and was reverted for violating this principle. **Pre-existing, out of scope:** all 22 Domain services still carry Spring's `@Service`, which VIII also forbids; that predates this feature and is unchanged here. |

**Gate decision**: PASS. Test-First (VI) is mandatory, matching every prior backend-touching feature (001, 002, 007) in this project.

## Project Structure

### Documentation (this feature)

```text
specs/008-bill-income-correction/
├── plan.md                          # This file
├── research.md                      # Phase 0 output
├── data-model.md                    # Phase 1 output
├── quickstart.md                    # Phase 1 output
├── contracts/
│   ├── bill-model.yaml              # Updated bill schema + new correction/history schemas
│   ├── bill-correction-controller.yaml   # PUT/DELETE /bills/{id}, GET /bills/{id}/history
│   ├── income-model.yaml            # Updated income schema + new correction/history schemas
│   ├── income-correction-controller.yaml # PUT/DELETE /incomes/{id}, GET /incomes/{id}/history
│   └── component-contracts.md       # Frontend hook/component contracts
└── tasks.md                         # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
Application/src/main/resources/swagger/bill/
├── bill-model.yaml                              # MODIFY — add reversal/correctsTransactionId to
│                                                 #          `bill`; add correctBillRequest,
│                                                 #          billHistoryResponse schemas
└── bill-correction-controller.yaml              # NEW — PUT/DELETE /bills/{id}, GET /bills/{id}/history

Application/src/main/resources/swagger/income/
├── income-model.yaml                            # MODIFY — same additions as bill-model.yaml
└── income-correction-controller.yaml            # NEW — PUT/DELETE /incomes/{id}, GET /incomes/{id}/history

Application/pom.xml                              # MODIFY — add bill-correction / income-correction
                                                  #          OpenAPI generator executions

Application/src/main/java/at/ymeri/my/finance/
├── application/mapper/
│   ├── BillMapper.java                          # MODIFY (likely auto-mapped, verify)
│   └── IncomeMapper.java                        # MODIFY (likely auto-mapped, verify)
└── controller/
    ├── bill/BillCorrectionController.java       # NEW — implements correct/remove/history delegate
    └── income/IncomeCorrectionController.java   # NEW — implements correct/remove/history delegate

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/bill/BillDto.java                       # MODIFY — add correctsTransactionId, reversal
├── data/income/IncomeDto.java                   # MODIFY — add correctsTransactionId, reversal
├── api/
│   ├── CorrectBillService.java                  # NEW
│   ├── RemoveBillService.java                   # NEW
│   ├── GetBillService.java                      # MODIFY — add getHistory(UUID)
│   ├── CorrectIncomeService.java                # NEW
│   ├── RemoveIncomeService.java                 # NEW
│   └── GetIncomeService.java                    # MODIFY — add getHistory(UUID)
└── service/
    ├── bill/
    │   ├── CorrectBillServiceImpl.java          # NEW
    │   ├── RemoveBillServiceImpl.java           # NEW
    │   └── GetBillServiceImpl.java              # MODIFY — filter getAll(); implement getHistory()
    └── income/
        ├── CorrectIncomeServiceImpl.java        # NEW
        ├── RemoveIncomeServiceImpl.java         # NEW
        └── GetIncomeServiceImpl.java            # MODIFY — filter getAll(); implement getHistory()

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/BillEntity.java                       # MODIFY — add corrects_transaction_id, reversal columns
└── entity/IncomeEntity.java                     # MODIFY — add corrects_transaction_id, reversal columns

integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/
├── BillCorrectionControllerIntegrationTest.java # NEW
├── IncomeCorrectionControllerIntegrationTest.java # NEW
└── AccountControllerIntegrationTest.java        # MODIFY — add correction/removal balance cases

frontend/src/
├── types/index.ts                               # MODIFY — add CorrectBillRequest,
│                                                 #          CorrectIncomeRequest, TransactionHistoryEntry;
│                                                 #          add correctsTransactionId? to Bill/Income
├── api/client.ts                                # MODIFY — add correctBill, removeBill,
│                                                 #          fetchBillHistory, correctIncome,
│                                                 #          removeIncome, fetchIncomeHistory
└── components/
    ├── RecentTransactions.tsx                   # MODIFY — add a per-row action menu (Correct/Remove/History)
    ├── CorrectBillForm.tsx                      # NEW — mirrors AddBillForm, pre-filled
    ├── CorrectIncomeForm.tsx                    # NEW — mirrors AddIncomeForm, pre-filled
    ├── RemoveConfirmDialog.tsx                  # NEW — shared confirm dialog for both types
    └── TransactionHistoryDialog.tsx             # NEW — shared history view for both types
```

**Structure Decision**: Web application (existing hexagonal-architecture backend + React SPA
frontend). No new modules or top-level directories. All backend changes stay within
`Application`/`Domain`/`Infrastructure`/`integration-tests`; all frontend changes stay within
`frontend/src/`, following the same layout precedent as 001, 002, and 007.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
