---

description: "Task list for 021 Release Hardening"
---

# Tasks: Release Hardening

**Input**: Design documents from `/specs/021-release-hardening/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: The spec explicitly requires one — FR-012 demands the restore procedure "MUST have been
executed end-to-end successfully, not merely described", which is T017. No other test tasks are
generated: this feature adds no Domain logic, and the existing integration suite becomes the
migration verifier for free the moment `ddl-auto` flips to `validate` (plan, Principle VI note).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Path Conventions

Multi-module Maven backend (`Domain/`, `Application/`, `Infrastructure/`, `Launcher/`, `Events/`,
`integration-tests/`) plus a separate Vite SPA in `frontend/`. Paths below are repository-relative.

> **Environment reality, applies to every task**: there is **no Docker daemon** in this
> development environment. Nothing here can be validated against a live PostgreSQL locally.
> Tasks say explicitly whether they are locally verifiable or CI-verified. Do not report a task as
> verified on the strength of having written it.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Get the migration framework onto the classpath before anything depends on it.

- [X] T001 Add `org.flywaydb:flyway-core` **and** `org.flywaydb:flyway-database-postgresql` to `Infrastructure/pom.xml` (no `<version>` — Spring Boot 3.4.0 manages both at 10.20.1). Both are required: Flyway 10 split database support out of core, and `flyway-core` alone fails at startup with *"No database found to handle jdbc:postgresql"* (research R1). This is the single most likely cause of a red first build.
- [X] T002 Create the directory `Infrastructure/src/main/resources/db/migration/`. No `spring.flyway.locations` override is needed — Flyway's default `classpath:db/migration` is scanned across Launcher's assembled classpath, which is why migrations can live in Infrastructure beside the entities that define the schema (research R6).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Make the test fixtures independent of production configuration **before** production
configuration changes. Every task here is a no-op against today's code and prevents a red CI run
later.

**⚠️ CRITICAL**: T003–T005 must land before T009 and T011–T012. The plan's Phase Ordering Note
requires fixtures to never lag the change that needs them; landing them early is strictly safe,
because adding a test value for a property that is not yet required changes no behaviour.

- [X] T003 In `integration-tests/src/test/resources/application.yaml`, add top-level properties `POSTGRES_PASSWORD: integration-test-password` and `JWT_SECRET: integration-test-jwt-secret`. These resolve the `${...}` placeholders that T011 introduces into `Launcher/src/main/resources/application.properties`, which is on the integration-test classpath. They are test fixtures, not credentials — the database they name is a throwaway container on a random port.
- [X] T004 [P] In `Application/src/test/java/at/ymeri/my/finance/security/SecurityConfigTest.java`, change the annotation to `@WebMvcTest(controllers = AuthController.class, properties = "app.security.jwt-secret=slice-test-secret")`. This slice currently relies on `JwtTokenService`'s random-key fallback, which T012 replaces with an explicit failure; without this the slice test breaks.
- [X] T005 [P] In `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/TestConfig.java`, replace `.withPassword("FinanceDbPassword")` with a distinct test-only value (e.g. `"integration-test-password"`, matching T003). This file is a tracked file containing the exact string that is published in this repository's git history — SC-005 ("zero credential values across all tracked files") fails the grep in T015 until it is gone.

**Checkpoint**: `./mvnw clean install` still passes; nothing has changed behaviourally.

---

## Phase 3: User Story 1 - Upgrade an Existing Instance Without Losing Data (Priority: P1) 🎯 MVP

**Goal**: Replace Hibernate's inferred schema with an explicit, versioned, ordered migration, and
a baseline that an already-running instance adopts without losing a row.

**Independent Test**: Start an instance on the current version, enter data, upgrade, and confirm
every account, category, budget, bill, income, recurring series and savings goal is unchanged
(quickstart scenarios 1–3).

### Implementation for User Story 1

- [X] T006 [US1] Write a **temporary** probe class at `Infrastructure/src/test/java/at/ymeri/my/finance/infrastructure/SchemaExportProbe.java` using the exact recipe in `quickstart.md` ("Developer scenario") and run it with `./mvnw -q -pl Domain,Infrastructure -am test -Dtest=SchemaExportProbe -Dsurefire.failIfNoSpecifiedTests=false`. Two traps, both already hit once and recorded: `org.hibernate.tool.hbm2ddl.SchemaExport` **does not exist** in Hibernate 6.6 (use `SchemaManagementToolCoordinator.process`), and the physical naming strategy is Hibernate's `org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy`, **not** the Spring-packaged one, which is not on Infrastructure's classpath.
- [X] T007 [US1] Create `Infrastructure/src/main/resources/db/migration/V1__baseline_schema.sql` from T006's output. Copy the DDL **verbatim** — ten tables (`account`, `account_currency`, `admin_account`, `allocation_transfer`, `bill`, `budget`, `category`, `income`, `recurring_series`, `savings_goal`), the generated constraint names included. **Add nothing**: no foreign keys on `bill.account_id` / `income.category_id` / `savings_goal.account_id`, no tightened nullability, no indexes. A baseline that "improves" the schema can fail against an operator's existing data, which is the exact inverse of FR-003 (data-model §4). Add a header comment stating that this file is frozen by Flyway's checksum once any instance has run it, and that schema changes go in `V2`, never back into `V1`.
- [X] T008 [US1] Delete `Infrastructure/src/test/java/at/ymeri/my/finance/infrastructure/SchemaExportProbe.java`. It is a tool, not a test, and must not be committed.
- [X] T009 [US1] In `Launcher/src/main/resources/application.properties`: change `spring.jpa.hibernate.ddl-auto` from `update` to `validate`, and add `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=1`, `spring.flyway.validate-on-migrate=true`. Comment the dual semantics that make FR-003 and FR-004 one setting: an existing non-empty database gets a `BASELINE` history row and **never executes** `V1`; an empty database executes it normally, and both end at the same schema (research R3, data-model §1). State `validate-on-migrate` explicitly even though it is the default, so the "newer backup into older code" refusal (US3 scenario 3) is visible in configuration rather than inherited.
- [X] T010 [US1] Run `./mvnw clean install`. Locally this proves compilation and the Domain/Application tests; the ~109 integration tests need Docker and run in CI. **Report the local result and the CI dependency separately** — with `ddl-auto=validate`, any disagreement between `V1__baseline_schema.sql` and the JPA mappings fails every integration test at context startup, and that is the real verification of this story.

**Checkpoint**: Schema changes are explicit, ordered, recorded once applied, and drift fails
startup instead of being silently patched.

---

## Phase 4: User Story 2 - Run an Instance Without Credentials From the Repository (Priority: P1)

**Goal**: Every secret comes from the operator's environment; nothing shipped in the source grants
access to their database; the already-published password is called out for rotation.

**Independent Test**: Grep the repository for any credential value and find none; then start a
fresh instance supplying only environment-provided secrets (quickstart scenarios 6–7).

**Independence note**: US2 shares no file with US1 except `Launcher/src/main/resources/application.properties`
(T009 touches the JPA/Flyway block, T011 the datasource block). Either story can ship first.

### Implementation for User Story 2

- [ ] T011 [US2] In `Launcher/src/main/resources/application.properties`, replace the committed values: `spring.datasource.password=${POSTGRES_PASSWORD}` (**no default** — an unresolvable placeholder makes Spring fail at startup naming the variable, which is FR-009), `spring.datasource.username=${POSTGRES_USER:passbook}` and `spring.datasource.url=${DATABASE_URL:jdbc:postgresql://postgres:5432/myfinance}` (defaults kept — neither is a secret), and add `app.security.jwt-secret=${JWT_SECRET:}`. Delete the literal `FinanceDbPassword`.
- [ ] T012 [US2] In `Application/src/main/java/at/ymeri/my/finance/security/JwtTokenService.java`, replace the random-key fallback in `init()` with a thrown `IllegalStateException` naming `JWT_SECRET` and saying what it is for. The current fallback silently invalidates every session on restart — a built-in default in precisely the sense FR-009 prohibits. Update the class Javadoc, which currently documents the fallback as an acceptable local-dev default.
- [ ] T013 [US2] In `docker-compose.yaml`, replace `POSTGRES_PASSWORD: FinanceDbPassword` with `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env — copy .env.example}` and do the same for `PGADMIN_DEFAULT_PASSWORD`. Add `env_file: .env` plus a `POSTGRES_PASSWORD`/`JWT_SECRET` `environment:` block to the `finance-app` service so the backend receives them. Compose reads `.env` from the project directory automatically, so `docker compose up` stays one command (FR-008, spec US2 scenario 2).
- [ ] T014 [US2] Create `.env.example` at the repository root, tracked, containing **placeholders only** — `POSTGRES_PASSWORD=`, `POSTGRES_USER=passbook`, `JWT_SECRET=`, `PGADMIN_DEFAULT_PASSWORD=` — each with a comment saying what it does and how to generate a good value (e.g. `openssl rand -base64 32` for `JWT_SECRET`).
- [ ] T015 [US2] Confirm `.gitignore` already covers `.env` while allowing `.env.example` (it does — the `### Secrets & local environment ###` block), then run `git grep -nI -e 'FinanceDbPassword' -e 'PGADMIN_DEFAULT_PASSWORD: admin' -- . ':!specs/'` and confirm it returns nothing. **Locally verifiable — actually run it** (SC-005).

**Checkpoint**: A fresh clone contains no usable credential, and a missing secret fails startup by
name rather than falling back.

---

## Phase 5: User Story 3 - Back Up and Actually Restore (Priority: P2)

**Goal**: A documented backup that produces one artifact, and a restore that has been *executed*,
not merely described.

**Independent Test**: Take a backup of an instance containing data, destroy the database, restore,
and confirm the app runs against it with the same data (quickstart scenarios 4–5).

**Depends on**: US1 (T009) — the "newer backup into older code" refusal works because
`flyway_schema_history` travels inside the dump (data-model §3).

### Implementation for User Story 3

- [ ] T016 [US3] Create `docs/BACKUP.md`: the `pg_dump -Fc` backup command, the `pg_restore --clean --if-exists` restore command, a naming convention, and where the artifact should live. State explicitly that feature 019's sync export is **not** a backup — it omits the admin account and merges rather than replaces, so an operator who has seen the Sync page does not assume otherwise (FR-011 note, research R9). Also state that `flyway_schema_history` is inside the dump and that this is what makes a newer backup refuse to load into older code.
- [ ] T017 [US3] Create `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BackupRestoreIntegrationTest.java`: seed data through the API, run the documented `pg_dump` inside the container via Testcontainers' `execInContainer`, drop the schema, run the documented `pg_restore`, assert the data is back. Use the same `@SpringBootTest(classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class}, webEnvironment = RANDOM_PORT)` shape as the existing suite. **Design constraint**: the commands in this test and the commands in `docs/BACKUP.md` must be the same commands — a test that dumps and restores by some other route verifies something no operator will ever run, and FR-012 would be unmet in substance while appearing met. CI-verified only (no local Docker).

**Checkpoint**: "Back up before upgrading" is safe advice because restore is proven, in CI, on a
real PostgreSQL.

---

## Phase 6: User Story 4 - Know What Version You Run and What Changed (Priority: P3)

**Goal**: An operator can read their version off a running instance, find what changed, and follow
a documented upgrade sequence.

**Independent Test**: On a running instance, determine the version without reading source; then
find, for that version, both what changed and how to upgrade to it (quickstart scenario 8).

### Implementation for User Story 4

- [ ] T018 [US4] Bump `0.0.1-SNAPSHOT` → `0.1.0` across all 19 occurrences in the six POMs (`pom.xml`, `Application/pom.xml`, `Domain/pom.xml`, `Infrastructure/pom.xml`, `Launcher/pom.xml`, `Events/pom.xml`, `integration-tests/pom.xml`) **and** the jar path in `Dockerfile:14`. Dropping `-SNAPSHOT` matters: a version an operator reads off a running instance must identify a fixed thing (research R10).
- [ ] T019 [US4] Add `app.version=@project.version@` to `Launcher/src/main/resources/application.properties`. `spring-boot-starter-parent` already configures Maven resource filtering with `@…@` delimiters for `application*.properties`, so this resolves at build time with no new dependency or plugin configuration (research R7).
- [ ] T020 [US4] Copy `specs/021-release-hardening/contracts/system-version-controller.yaml` to `Application/src/main/resources/swagger/system/system-version-controller.yaml`.
- [ ] T021 [US4] Add a `system-version` execution to the `openapi-generator-maven-plugin` in `Application/pom.xml`, mirroring the existing executions: `<apiPackage>${api-package}.system</apiPackage>`, `<modelPackage>${model-package}</modelPackage>`, `skipOverwrite=true`, `delegatePattern=true`, `interfaceOnly=true`, `useSpringBoot3=true`. Because `skipOverwrite=true`, run `./mvnw -pl Application clean generate-sources` so the new sources are actually produced, and commit the generated files as the project does for every other feature.
- [ ] T022 [US4] Create `Application/src/main/java/at/ymeri/my/finance/controller/system/SystemVersionController.java` implementing the generated `SystemVersionApi` delegate, injecting `@Value("${app.version:unknown}")` and building the `SystemVersion` response directly. No MapStruct mapper and no Domain service — document why in a class comment: a build constant is not business logic and crosses no external boundary, so there is no port for Principle VIII to require (plan, Constitution Check). Leave `buildTime` absent when unavailable rather than failing.
- [ ] T023 [P] [US4] Add a `SystemVersion` type (`version: string; buildTime?: string`) to `frontend/src/types/index.ts`.
- [ ] T024 [P] [US4] Add `fetchSystemVersion()` to `frontend/src/api/client.ts` using the existing `request<T>()` helper, so it carries the bearer token and the standard session-death handling.
- [ ] T025 [US4] Render the version in `frontend/src/components/SourceFooter.tsx`. This one file already appears on every view, so no `App.tsx` change is needed. It also strengthens the existing AGPL §13 wording — "the source of the version they are using" becomes checkable once the version is stated. Handle the fetch failing by rendering the footer without a version, never by breaking it.
- [ ] T026 [US4] Create `CHANGELOG.md` in Keep-a-Changelog form with a `0.1.0` entry covering this feature and, honestly, the recent features that shipped before versioning existed. State plainly under an **operator action required** heading that upgrading to `0.1.0` requires creating `.env` and that the previously published database password must be **rotated** — removal from the working tree is not rotation, since it remains in published git history (FR-010, FR-014).
- [ ] T027 [US4] Create `docs/UPGRADING.md`: the ordered upgrade sequence with **taking a backup as step one**, linking `docs/BACKUP.md`. No step may be a hand-written database command (FR-015). Include what an operator should see in `flyway_schema_history` afterwards, and note that Flyway locks the database during migration so two instances starting at once is safe (research R5).

**Checkpoint**: Version, release notes and upgrade path are all discoverable, and the three
statements of the version (POM, CHANGELOG heading, `GET /system/version`) agree.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T028 Rewrite the "Before you self-host" section and the Database warning in `README.md`. Both are now stale in four places: *"No authentication"* (closed by feature 020), *"No schema migrations"*, *"No secrets management"*, *"No backup/restore tooling or versioned releases"* (all closed here), and *"Integration tests are currently disabled (WIP)"* — which is simply wrong, nine of ten integration test classes are active and green in CI. Replace the hardcoded-credentials paragraph with the `.env` setup, and add the `.env` step to both "Running the Full Stack" options. Link `CHANGELOG.md`, `docs/UPGRADING.md` and `docs/BACKUP.md`.
- [ ] T029 [P] Update `SECURITY.md`: "Known and Accepted Limitations" still lists *"No authentication or authorisation"* and *"Default credentials in `docker-compose.yaml`"* and *"`spring.jpa.hibernate.ddl-auto=update`"*, all three now untrue; "Supported Versions" still says no released versions exist. Add the rotation notice for the historically published password.
- [ ] T030 Decide `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/BillGetControllerIntegrationTest.java`: make it pass or delete it. The constitution's Self-Hosting Obligations require integration tests "enabled and green", and an indefinitely `@Disabled` test is a claim of coverage that does not exist. **This is outside the spec's four user stories and is flagged rather than assumed** — if scope should stay exactly as specified, this is the task to drop; nothing else depends on it.
- [ ] T031 Run `./mvnw clean install` and `cd frontend && npm run build`. Report both results factually, and state explicitly that the integration suite (T010's real verifier and T017) runs in CI, not here.
- [ ] T032 Walk `specs/021-release-hardening/quickstart.md` against the delivered files and correct any command that drifted — particularly that `docs/BACKUP.md`'s commands and `BackupRestoreIntegrationTest`'s commands match each other and match the quickstart.
- [ ] T033 Mark completed tasks `[X]` in this file, then commit and push to `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup only in ordering, not in content. **Blocks T009 and T011–T012** — the two changes that make previously-optional configuration required.
- **US1 (Phase 3)** and **US2 (Phase 4)**: both P1, both independent of each other. Either may ship first.
- **US3 (Phase 5)**: depends on US1's T009 for the Flyway history that gives a backup its schema provenance.
- **US4 (Phase 6)**: independent of all three; can be done at any point after Setup.
- **Polish (Phase 7)**: T028–T029 depend on US1, US2, US3 and US4 all being done, since they describe the finished state.

### User Story Dependencies

- **US1 (P1)**: independent. The MVP.
- **US2 (P1)**: independent. Shares only `Launcher/src/main/resources/application.properties` with US1, in a different block.
- **US3 (P2)**: needs US1.
- **US4 (P3)**: independent.

**These four stories are workstreams, not layers** — unusual for this template, and worth stating
so nobody looks for a dependency that is not there.

### Parallel Opportunities

- T004 and T005 (Foundational) — different files.
- T023 and T024 (US4 frontend) — different files; T025 depends on both.
- T029 (SECURITY.md) runs alongside T028 (README.md).
- With more than one person: US1, US2 and US4 can proceed simultaneously once Phase 2 is done.

---

## Parallel Example: Phase 2 Foundational

```bash
Task: "Add jwt secret property to SecurityConfigTest in Application/src/test/java/at/ymeri/my/finance/security/SecurityConfigTest.java"
Task: "Replace hardcoded password in integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/TestConfig.java"
```

## Parallel Example: User Story 4 frontend

```bash
Task: "Add SystemVersion type in frontend/src/types/index.ts"
Task: "Add fetchSystemVersion() in frontend/src/api/client.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup (T001–T002)
2. Phase 2 Foundational (T003–T005)
3. Phase 3 US1 (T006–T010)
4. **STOP and VALIDATE**: `./mvnw clean install` locally, then let CI run the integration suite —
   that is where the baseline is actually proven.

US1 alone is a coherent, shippable increment: it is the single highest-risk thing about handing
this app to anyone, and it is worth landing and watching CI before touching secrets.

### Incremental Delivery

1. Setup + Foundational → nothing has changed behaviourally, CI still green
2. US1 → explicit migrations, drift fails loudly → **MVP**
3. US2 → no credentials in the repository
4. US3 → backup and restore, proven rather than described
5. US4 → versions, release notes, upgrade path
6. Polish → documentation matches reality

### Notes

- Commit after each phase; the baseline (T007) deserves its own commit and its own read-through.
- **Never edit `V1__baseline_schema.sql` after it is applied anywhere** — Flyway checksums it.
- Do not claim any Docker-dependent task as verified locally. Say what ran, what did not, and why.
