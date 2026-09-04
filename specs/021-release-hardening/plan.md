# Implementation Plan: Release Hardening

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-release-hardening/spec.md`

## Summary

Close the four remaining Self-Hosting Obligations so an instance can be handed to someone other
than its author without risking their financial history: replace `ddl-auto=update` with Flyway
migrations plus a baseline that *adopts* an already-running database instead of rebuilding it,
move every credential out of version control into the environment, document and actually execute
a backup/restore procedure, and cut a real version with release notes and an upgrade path.

The technical core is one decision: **generate the baseline DDL offline from the JPA mappings, and
let the CI integration suite verify it.** This was the acknowledged risk when the spec was written
— there is no Docker in this development environment, so the baseline cannot be checked against a
live PostgreSQL here. A probe run in this repository proved the offline generation works and
produced the complete ten-table schema (research R2). Verification then comes for free: once
`ddl-auto` flips to `validate`, the ~109 existing integration tests boot a real PostgreSQL
container in CI against a Flyway-created schema, and any disagreement between the baseline and the
mappings fails every one of them at context startup.

Everything else follows from that: `baseline-on-migrate` makes an existing instance adopt the
baseline while an empty database executes it (research R3), and `validate` plus Flyway's history
checksums turn silent inference into loud, early refusal (research R4).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5 / React 18 (frontend)

**Primary Dependencies**: Spring Boot 3.4.0; **Flyway 10.20.1** — `flyway-core` **and**
`flyway-database-postgresql`, both required (Flyway 10 split database support out of core; core
alone fails at startup with *"No database found to handle jdbc:postgresql"*); Hibernate 6.6.2;
OpenAPI Generator 7.0.1 (delegate pattern); MapStruct 1.5.5

**Storage**: PostgreSQL, single database, single instance. Schema frozen as
`V1__baseline_schema.sql` — ten tables, all money columns `numeric(38,2)`.

**Testing**: JUnit 5 for Domain; Testcontainers PostgreSQL for `integration-tests` (real database,
per constitution Principle VI); no frontend test runner exists in this repository, so the frontend
change is verified by `npm run build` type-checking.

**Target Platform**: Linux/macOS self-hosted, Docker Compose

**Project Type**: Multi-module Maven backend (hexagonal) + separate Vite SPA

**Performance Goals**: Not a performance feature. The one constraint worth stating: migration on
startup must not meaningfully delay boot — the baseline is a single `CREATE TABLE` batch, and on
an existing instance it does not execute at all.

**Constraints**:

- **No Docker daemon in this development environment.** Anything requiring a live PostgreSQL is
  CI-verified, not locally verified. This is stated in `quickstart.md` per scenario rather than
  glossed.
- **The baseline must change nothing.** No added foreign keys, no tightened nullability, no
  renamed constraints. A baseline that "improves" the schema can fail against an operator's
  existing data — the exact opposite of FR-003.
- **the password this project used to ship is in published git history.** Removal from the working tree is not
  rotation; the documentation must say so plainly (FR-010).
- **Migrations are immutable once applied.** Flyway checksums them; `V1` is frozen the moment any
  instance runs it.

**Scale/Scope**: Single household, single instance. Six POMs, ~15 source/config files, three
documentation files, one new endpoint, one new integration test.

## Constitution Check

*Constitution v2.1.0. Gate evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | Verdict | Reasoning |
|---|---|---|
| **I. Transaction Immutability** | ✅ Upheld | No transaction logic changes. The baseline reproduces `reversal` and `corrects_transaction_id` on `bill` and `income` verbatim; correction history is preserved column-for-column. |
| **II. Ingestion Is Idempotent** | ✅ N/A (and echoed) | No ingestion path touched. Worth noting the parallel: FR-001's "never applied twice" is the same invariant one level down, and Flyway's history table is what provides it. |
| **III. Balance Derivation** | ✅ Upheld | `account.balance` stays the opening balance. No materialised current balance is introduced. |
| **IV. Currency Precision** | ✅ Upheld — verified | Every monetary column in the generated baseline is `numeric(38,2)`. No `float`, `real`, or `double precision` appears anywhere (data-model §4). The baseline is the first artifact that states this in SQL rather than inferring it. |
| **V. Audit Trail** | ✅ Strengthened | `flyway_schema_history` adds a durable, timestamped record of every schema change applied to a given database — an audit trail that does not exist today. |
| **VI. Test-First** | ⚠️ Adapted, see below | |
| **VII. API Contract Stability** | ✅ Upheld | `GET /system/version` is additive and specified in OpenAPI **before** implementation (`contracts/system-version-controller.yaml`). No existing endpoint changes. This is why Actuator's `/actuator/info` was rejected despite being the conventional answer — the frontend would depend on a framework-owned shape Principle VII does not cover (research R7). |
| **VIII. Hexagonal Architecture** | ✅ Upheld | Domain is untouched: no new Domain class, no new port. Flyway lives in Infrastructure (where the entities that define the schema live — research R6); configuration lives in Launcher. The `/system/version` controller reads a build property with no Domain service behind it, which is correct rather than a shortcut: a build constant is not business logic and crosses no external boundary, so there is nothing for a port to mediate. |

**Principle VI (Test-First) — how it applies to a feature that is mostly SQL, configuration and
prose.** The principle mandates unit tests for Domain financial logic and integration tests against
a real database. This feature adds no Domain logic, so there is nothing to unit-test; writing
tests for a `.sql` file or a `.env.example` would be theatre. What it does add is squarely inside
the second half of the principle:

- The **backup/restore integration test** is written to run the documented commands against a real
  PostgreSQL container (research R9). It is the mechanism by which FR-012's *"MUST have been
  executed end-to-end successfully, not merely described"* is met at all, given no local Docker.
- The **existing integration suite becomes the migration verifier** the moment `ddl-auto=validate`
  lands. Constitution: *"Integration tests … are the only guard on other people's migrations."*
  That sentence describes exactly this arrangement.

**Self-Hosting Obligations status.** Of the five: authentication was closed by feature 020;
migrations, secrets, and versioned releases + backup/restore are closed here. The fifth —
*"Integration tests MUST be enabled and green"* — is **all but met**: nine of ten integration test
classes run green in CI, and `BillGetControllerIntegrationTest` carries `@Disabled`.

This is not one of the spec's four user stories and is **not** being folded into them silently. It
is raised here as a gate observation with one Polish task: look at that class and either make it
pass or delete it, since an indefinitely disabled test is a claim of coverage that does not exist.
If the scope should stay exactly as specified, that is the task to drop — nothing else depends on
it.

**Gate result: PASS.** No violations requiring justification; the Complexity Tracking table below
is therefore empty and omitted.

## Project Structure

### Documentation (this feature)

```text
specs/021-release-hardening/
├── plan.md                                  # This file
├── spec.md                                  # Feature specification
├── research.md                              # Phase 0 — 11 decisions, incl. the validated DDL recipe
├── data-model.md                            # Phase 1 — flyway_schema_history, release, backup artifact
├── quickstart.md                            # Phase 1 — 8 operator scenarios + the DDL regeneration recipe
├── contracts/
│   └── system-version-controller.yaml       # Phase 1 — GET /system/version
├── checklists/
│   └── requirements.md                      # From /speckit-specify — all items pass
└── tasks.md                                 # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
Infrastructure/
├── pom.xml                                  # + flyway-core, flyway-database-postgresql
└── src/main/resources/db/migration/
    └── V1__baseline_schema.sql              # NEW — the ten-table baseline (research R2)

Launcher/
└── src/main/resources/application.properties
                                             # ddl-auto: update → validate
                                             # spring.flyway.* (baseline-on-migrate, baseline-version,
                                             #   validate-on-migrate)
                                             # datasource password/username/url → ${ENV}
                                             # app.security.jwt-secret → ${JWT_SECRET:}
                                             # app.version=@project.version@

Application/
├── pom.xml                                  # + openapi-generator execution: system-version
├── src/main/resources/swagger/system/
│   └── system-version-controller.yaml       # NEW — copied from contracts/
├── src/main/java/at/ymeri/my/finance/
│   ├── controller/system/SystemVersionController.java   # NEW — implements generated delegate
│   └── security/JwtTokenService.java         # random-key fallback → explicit failure
└── src/test/java/at/ymeri/my/finance/security/
    └── SecurityConfigTest.java               # must now supply a JWT secret

integration-tests/
├── src/test/resources/application.yaml       # + test values for POSTGRES_PASSWORD / JWT_SECRET
└── src/test/java/at/ymeri/my/finance/integration/tests/
    └── BackupRestoreIntegrationTest.java     # NEW — executes the documented pg_dump/pg_restore

frontend/
├── src/api/client.ts                         # + fetchSystemVersion()
├── src/types/index.ts                        # + SystemVersion
└── src/App.tsx                               # version in the footer, beside the source link

# Repository root
pom.xml, Application/pom.xml, Domain/pom.xml, Infrastructure/pom.xml,
Launcher/pom.xml, Events/pom.xml, integration-tests/pom.xml
                                              # 0.0.1-SNAPSHOT → 0.1.0
Dockerfile                                    # jar path follows the version
docker-compose.yaml                           # credentials → ${…:?} substitution + env_file
.env.example                                  # NEW — tracked, placeholders only
CHANGELOG.md                                  # NEW
docs/UPGRADING.md                             # NEW
docs/BACKUP.md                                # NEW
README.md                                     # "Before you self-host" is stale in four places
SECURITY.md                                   # "Known limitations" is stale in three places
```

**Structure Decision**: The existing hexagonal Maven layout is unchanged — no new module, no new
package beyond `controller/system`. Two placement decisions are load-bearing and were made
deliberately rather than by default:

1. **Migrations live in `Infrastructure`, not `Launcher`** (research R6). The schema is defined by
   the JPA entities, which live in Infrastructure; a migration belongs beside the thing that
   determines it, so a contributor changing an entity finds the migrations in the same module.
   Flyway's default `classpath:db/migration` is scanned across the assembled classpath, so Launcher
   picks them up with no extra wiring.
2. **Configuration stays in `Launcher`**, which already owns `application.properties`. Flyway's
   behavioural settings are deployment configuration, not schema.

The frontend change is deliberately three lines of plumbing plus a footer render. There is no test
runner in this repository, so `npm run build` (which type-checks) is the only available gate —
another reason to keep the frontend surface minimal.

## Phase Ordering Note

`/speckit-tasks` will sequence this, but one ordering constraint is not obvious from the user
story priorities and should survive into `tasks.md`:

**The secrets change (US2) and the migration change (US1) both alter how the app boots, and both
break the integration suite until their test-side configuration lands.** The test fixtures
(`integration-tests/src/test/resources/application.yaml`, `SecurityConfigTest`'s property) must be
part of the same increment as the change that requires them, not a later polish step — otherwise
CI goes red for reasons unrelated to whichever story is being validated.
