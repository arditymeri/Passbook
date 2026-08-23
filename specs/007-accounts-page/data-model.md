# Data Model: Accounts Page

**Feature**: 007-accounts-page
**Date**: 2026-08-23

No database migration is required — every column this feature needs already exists
(`bill.account_id` and `income.account_id` were added by an earlier, unreleased change and are
already declared on `BillEntity`/`IncomeEntity`, just not yet surfaced through the Bill API or any
frontend UI).

## Entities

### Account (existing table `account` — no schema change)

| Field | Type | Nullable | Description |
|-------|------|----------|--------------|
| `id` | UUID | No | Primary key |
| `name` | String | No | Unique account name |
| `type` | enum (`CHECKING`/`SAVINGS`/`CREDIT_CARD`/`CASH`/`INVESTMENT`) | No | Account type |
| `currencies` | List\<String\> (ISO 4217) | No | Currencies this account can hold, ≥1 |
| `defaultCurrency` | String (ISO 4217) | No | Must be one of `currencies`; currency the derived balance is displayed in |
| `balance` | BigDecimal | Yes (defaults to 0) | **Starting balance** set at account creation — the pre-app-usage opening figure. Not mutated by this feature. |
| `institution` | String | Yes | Free-text bank/institution name |

**Derived, non-persisted field** (computed by `GetAccountServiceImpl`, not stored):

| Field | Type | Description |
|-------|------|--------------|
| `currentBalance` | BigDecimal | `balance` (starting) + Σ linked `income.amount` − Σ linked `bill.amount`. Returned in place of the raw `balance` column value on every read (`GET /accounts`, `GET /accounts/{id}`). |

**Validation rules** (unchanged, already enforced by `AddAccountServiceImpl.validate`):
- `name` required, non-blank, unique
- `type` required
- `currencies` non-empty; each must be a valid ISO 4217 code
- `defaultCurrency` must be one of `currencies`

**New computation rule** (this feature):
- `currentBalance` MUST be recalculated on every read — no caching, no write-time update, per
  Constitution Principle III (see `research.md` Decision 1).

---

### Bill (existing table `bill` — no schema change, OpenAPI contract change only)

| Field | Type | Nullable | Description |
|-------|------|----------|--------------|
| `id` | UUID | No | Primary key |
| `description` | String | Yes | |
| `amount` | BigDecimal | No | |
| `time` | OffsetDateTime | No | |
| `categoryId` | String | Yes | |
| `accountId` | String | Yes | **Already exists in `BillEntity`/`BillDto`; newly exposed on the OpenAPI `bill` schema by this feature.** References `account.id`; no FK constraint enforced at the DB level (matches the existing `categoryId` pattern — referential integrity is a Domain/application concern, not a DB one, in this codebase). |

**New validation rule**: None added — an absent/blank `accountId` continues to mean "no account
association," matching current behavior for every bill created before this feature (FR-016).

---

### Income (existing table `income` — no changes needed at all)

| Field | Type | Nullable | Description |
|-------|------|----------|--------------|
| `accountId` | String | Yes | Already fully wired end-to-end (Domain, Infrastructure, OpenAPI `createIncomeRequest`/`incomeResponse`). This feature only adds a frontend UI control to set it — no backend change. |

---

## Relationships

```
Account (1) ──── (0..*) Bill      via bill.account_id     (optional, no DB-level FK)
Account (1) ──── (0..*) Income    via income.account_id   (optional, no DB-level FK)
```

- An account may have zero or more linked bills and incomes.
- A bill or income has at most one linked account (or none).
- Deleting an account that is referenced by any bill/income is already blocked by the existing
  `DeleteAccountServiceImpl` (`isReferencedByTransaction` check) — unaffected by this feature,
  since account deletion is out of scope here.

## State Transitions

None — accounts, once created, are not edited or deleted by this feature. A bill/income's account
link is set once at creation time and never changed (bills/incomes have no update/delete
capability anywhere in the current system, per the Assumptions in spec.md).
