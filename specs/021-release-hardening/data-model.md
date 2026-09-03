# Phase 1 Data Model: Release Hardening

**Feature**: 021-release-hardening | **Date**: 2026-09-03

This feature adds **no domain entities**. It adds one Flyway-managed bookkeeping table, freezes
the existing schema into a reviewable artifact, and introduces two non-persisted concepts
(a release and a backup artifact). Each of the spec's three Key Entities is mapped below to the
concrete thing it becomes.

---

## 1. Schema version history → `flyway_schema_history`

Created and owned entirely by Flyway. **No JPA entity, no repository, no adapter** — nothing in
Infrastructure maps it, and Domain never sees it. Recorded here only so that an operator reading
their database, or a contributor debugging a failed startup, knows what it is and that it is not
theirs to edit.

| Column | Type | Meaning |
|---|---|---|
| `installed_rank` | `integer` (PK) | Application order |
| `version` | `varchar(50)` | `1` for the baseline |
| `description` | `varchar(200)` | From the filename after `__` |
| `type` | `varchar(20)` | `SQL`, or `BASELINE` for the adopted-instance marker |
| `script` | `varchar(1000)` | Filename |
| `checksum` | `integer` | Guards against a migration being edited after it was applied |
| `installed_by` | `varchar(100)` | Database user |
| `installed_on` | `timestamp` | When |
| `execution_time` | `integer` | Milliseconds |
| `success` | `boolean` | |

**The two rows that matter for this feature:**

- On an **already-running instance**, the first startup after upgrading writes a single row with
  `type = BASELINE`, `version = 1`. `V1__baseline_schema.sql` is marked applied and is **not
  executed**. This is the row that makes FR-003 true.
- On a **new empty database**, the first startup writes a row with `type = SQL`, `version = 1`,
  because `V1` really did run. This is FR-004: different rows in this table, identical schema.

**Invariant a contributor must not break**: `checksum` means an applied migration file is frozen.
Editing `V1__baseline_schema.sql` after any instance has run it turns every subsequent startup
into a validation failure. Schema changes go in `V2`, `V3`, … — never back into `V1`.

---

## 2. Release → `CHANGELOG.md` entry + POM version + `GET /system/version`

Not persisted anywhere. A release exists in three places that must agree:

| Facet | Where it lives | Notes |
|---|---|---|
| Identifier | `<version>` in the six POMs | `0.1.0`; also the jar name in `Dockerfile` |
| What changed | `CHANGELOG.md` | Keep a Changelog format, newest first |
| Operator action required | `CHANGELOG.md` entry + `docs/UPGRADING.md` | FR-014's "whether it requires operator action" |
| Runtime-observable value | `GET /system/version` response | Sourced from `app.version=@project.version@` |

**Fields of the version response** (contract in `contracts/system.yaml`):

| Field | Type | Required | Source |
|---|---|---|---|
| `version` | string | yes | Maven `${project.version}`, filtered into `application.properties` at build time |
| `buildTime` | string (`date-time`) | no | Build timestamp; absent when running from an unfiltered classpath (e.g. an IDE run) |

`buildTime` is optional on purpose: making it required would mean a developer running from an IDE
gets a 500 from an endpoint whose entire job is to be readable.

**Consistency rule**: the POM version, the newest `CHANGELOG.md` heading, and what
`GET /system/version` returns are the same string. Bumping one without the others produces an
instance that misreports itself — which is precisely the failure FR-013 exists to prevent.

---

## 3. Backup artifact → a `pg_dump` custom-format file

Not a project data structure; a file the operator holds.

| Property | Value |
|---|---|
| Format | PostgreSQL custom (`pg_dump -Fc`) — compressed, single file, restorable with `pg_restore` |
| Contents | The entire `passbook` database: all ten tables **plus** `flyway_schema_history` |
| Naming convention (documented, not enforced) | `passbook-<ISO date>.dump` |
| Restore target | An empty database, via `pg_restore --clean --if-exists` |

**Why `flyway_schema_history` being inside the dump matters**: it is what makes US3 scenario 3
work. Restoring a newer instance's backup into older code puts a migration version in the history
that the older code cannot resolve, and Flyway refuses to start rather than letting old code write
against a newer schema. The backup carries its own schema provenance.

**What a backup is not**: feature 019's sync export. That artifact deliberately omits
instance-level configuration (the admin account) and merges on import rather than replacing. An
operator who restores a sync export expecting a backup gets a subtly different instance. The
documentation states this explicitly (FR-011 note, research R9).

---

## 4. The baseline schema itself

`V1__baseline_schema.sql` is not an entity but it is this feature's most important artifact. It
freezes ten tables, generated from the JPA mappings (research R2):

| Table | Backing entity | Money columns |
|---|---|---|
| `account` | `AccountEntity` | `balance numeric(38,2)` |
| `account_currency` | `AccountEntity.currencies` (`@ElementCollection`) | — |
| `admin_account` | `AdminAccountEntity` | — |
| `allocation_transfer` | `AllocationTransferEntity` | `amount numeric(38,2)` |
| `bill` | `BillEntity` | `amount numeric(38,2)` |
| `budget` | `BudgetEntity` | `limit_amount numeric(38,2)` |
| `category` | `CategoryEntity` | — |
| `income` | `IncomeEntity` | `amount numeric(38,2)` |
| `recurring_series` | `RecurringSeriesEntity` | — |
| `savings_goal` | `SavingsGoalEntity` | `target_amount numeric(38,2)` |

**Constraints present**: primary keys on every table except `account_currency`; `unique` on
`account.name` and `category.name`; composite `unique (category_id, year, month)` on `budget`;
one foreign key, `account_currency.account_id → account`.

**Constraints deliberately *not* added**: cross-entity references (`bill.account_id`,
`income.category_id`, `savings_goal.account_id`, `bill.corrects_transaction_id`, …) are
`varchar(255)` columns with no foreign keys, because the entities model them as `String`. Adding
foreign keys here would be a schema change dressed as a baseline — it could fail against an
existing operator's data that violates the new constraint, which is the exact opposite of what
FR-003 requires. **The baseline reproduces the current schema and changes nothing.** If those
references should become real foreign keys, that is a later migration with its own spec.

**Principle IV check**: every monetary column above is `numeric(38,2)`. No `float`, `real`, or
`double precision` appears anywhere in the baseline.
