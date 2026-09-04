---

description: "Task list for 022 Idempotent Statement Ingestion"
---

# Tasks: Idempotent Statement Ingestion

**Input**: Design documents from `/specs/022-idempotent-statement-ingestion/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Generated, and not optional here. Principle VI is NON-NEGOTIABLE for financial logic, and
this feature *is* financial logic: identity derivation decides whether an operator's transaction is
recorded or silently discarded. Domain tests run locally; the database guarantees need Testcontainers.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Path Conventions

Multi-module Maven backend (`Domain/`, `Application/`, `Infrastructure/`, `Launcher/`, `Events/`,
`integration-tests/`) plus a Vite SPA in `frontend/`. Paths are repository-relative.

> **Environment reality**: no Docker daemon here. Unusually for this project, the *hardest* logic —
> identity derivation, occurrence indexing, CSV parsing — is pure Domain computation and **runs
> locally**. The migration, the unique constraint and the concurrency guarantee are CI-only. Report
> the two categories separately; never call a CI-only task verified because it compiled.

---

## Phase 1: Setup

**Purpose**: Dependencies and code generation, including the one unknown that must be resolved before
any controller is written.

- [X] T001 Add `org.apache.commons:commons-csv` version `1.12.0` to `Domain/pom.xml`. Declare it there rather than in the parent — Domain is where parsing lives (research R6), and the parent would put a CSV library on every module's classpath for nothing. Resolution through this environment's proxy was verified during planning.
- [X] T002 Copy `specs/022-idempotent-statement-ingestion/contracts/statement-ingestion-controller.yaml` to `Application/src/main/resources/swagger/statement/statement-ingestion-controller.yaml`.
- [X] T003 Add a `statement-ingestion` execution to the `openapi-generator-maven-plugin` in `Application/pom.xml`, mirroring the existing executions: `<apiPackage>${api-package}.statement</apiPackage>`, `<modelPackage>${model-package}</modelPackage>`, `skipOverwrite=true`, `delegatePattern=true`, `interfaceOnly=true`, `useSpringBoot3=true`.
- [X] T004 Run `./mvnw -pl Application clean generate-sources` and **read the generated `StatementIngestionApi` delegate interface before writing anything against it**. Every existing endpoint in this project is JSON-in/JSON-out; `multipart/form-data` under the delegate pattern is untried here and the generated parameter type is not predictable (`MultipartFile`, `Resource`, or a byte array). This is the same discipline that caught the `JsonNullable` wrapping in features 018 and 019 — minutes now, a rewrite later (research R10 risk 1). Commit the generated sources as the project does for every feature.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The schema change and the plumbing every story needs. Nothing about identity or
ingestion yet — just somewhere for identity to live.

**⚠️ CRITICAL**: T005–T011 block every user story.

- [X] T005 Create `Infrastructure/src/main/resources/db/migration/V2__add_external_transaction_identity.sql`: `ALTER TABLE bill ADD COLUMN external_id varchar(255)`, the same for `income`, and a **partial** unique index on each — `CREATE UNIQUE INDEX ... ON bill (account_id, external_id) WHERE external_id IS NOT NULL`. Partial because hand-entered and pre-existing rows keep a null identity and must not collide (research R4). **Do not touch `V1`** — Flyway checksums applied migrations, and editing the baseline breaks every instance that has run it. Add a header comment stating what the index guarantees and why it is partial.
- [X] T006 [P] Add `externalId` (`@Column(name = "external_id")`) to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java`. Mapping the column is what makes `ddl-auto=validate` confirm the migration ran.
- [X] T007 [P] Add the same field to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java`.
- [X] T008 [P] Add `private String externalId;` to `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java`.
- [X] T009 [P] Add the same field to `Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java`.
- [X] T010 Confirm `BillMapper` and `IncomeMapper` carry `externalId` through in both directions. MapStruct maps same-named fields automatically, so this is a verification, not an edit — but verify it rather than assume, because a silently unmapped identity would make every ingested row invisible to deduplication forever.
- [X] T011 Create the Domain transient types in `Domain/src/main/java/at/ymeri/my/finance/domain/data/ingestion/`: `StatementRow` (rowIndex, date, description, amount, direction, sourceTransactionId, externalId), `TransactionDirection` (BILL, INCOME), `RowStatus` (RECORDED, ALREADY_RECORDED, REJECTED, EXCLUDED), `RowOutcome`, `IngestionResult`. Per data-model §2–§3. Amounts are `BigDecimal` and always positive — direction carries the sign (Principle IV).
- [X] T012 Run `./mvnw clean install -pl '!integration-tests'` and confirm it is green. Nothing has changed behaviourally yet.

**Checkpoint**: The column exists, is mapped end to end, and nothing uses it.

---

## Phase 3: User Story 1 - Re-import an Overlapping Statement Without Double-Counting (Priority: P1)

**Goal**: Ingesting a statement twice records it once. The invariant the constitution calls
load-bearing.

**Independent Test**: Import a statement, record counts and balances, import the identical file
again, confirm nothing changed (quickstart scenario 1).

> **⚠️ This phase is a development increment, NOT a shippable one.** It delivers identity as a plain
> hash, which collapses genuinely repeated transactions — two coffees on the same day become one.
> That is silent financial data loss, and US2 fixes it. **The real MVP is US1 + US2 together**; do not
> deploy after this phase. Kept separate because the two stories fail differently and each deserves
> its own verification, not because either is optional.

### Implementation for User Story 1

- [X] T013 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/ingestion/ExternalIdentityFactory.java`: derive identity as the source-supplied transaction id when present, else a deterministic hash over **account id, calendar date, amount, description, and direction** in that order. Direction is in the hash because both tables store positive amounts — without it a refund collides with the charge it reverses (research R1). The calendar date is used rather than a timestamp because any time-of-day is invented by the parser, not stated by the statement.
- [X] T014 [P] [US1] Domain unit tests for T013 in `Domain/src/test/java/.../ingestion/ExternalIdentityFactoryTest.java`: same inputs give the same identity; a differing account, date, amount, description or direction each give a different one; a source-supplied id wins over the derived form; an empty description still yields a stable identity. **Runs locally.**
- [X] T015 [US1] Create `ParseStatementService` (Domain `api/`) and `ParseStatementServiceImpl` (Domain `service/ingestion/`): CSV text → `List<StatementRow>` using commons-csv, assigning `rowIndex` by file position and classifying unusable rows as REJECTED with a reason (unparseable date, missing or unparseable amount, non-positive amount, wrong column count). A rejected row must not block the rows around it (FR-011).
- [X] T016 [P] [US1] Domain unit tests for T015 in `Domain/src/test/java/.../ingestion/ParseStatementServiceImplTest.java`, covering quoted fields containing commas, escaped quotes, embedded newlines inside quoted fields, a rejected row surrounded by good ones, and sign-to-direction mapping. **Runs locally** — and the embedded-newline case is precisely what feature 017's line-oriented parser could not represent.
- [X] T017 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/ingestion/IngestTransactionsPersistencePort.java`: takes the rows to write and returns which ones actually landed. Shaped as "insert these, tell me which are new" rather than "does this exist?" — the port must not offer a check-then-write shape, because that is the shape FR-005 exists to rule out.
- [X] T018 [US1] Implement it in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/ingestion/IngestTransactionsPostgresAdapter.java` using `NamedParameterJdbcTemplate` (already on the classpath via `spring-boot-starter-data-jpa`): a single multi-row `INSERT ... ON CONFLICT (account_id, external_id) WHERE external_id IS NOT NULL DO NOTHING RETURNING id, external_id`. **The `WHERE` clause must be repeated in the conflict target** — inferring a partial index requires it, and omitting it raises *"no unique or exclusion constraint matching the ON CONFLICT specification"* at runtime, in CI, not at compile time (research R10 risk 2). Note that `batchUpdate` discards returned rows, so a multi-row `VALUES` list in one statement is required to get `RETURNING` back (risk 3). Raw SQL is permitted here and only here.
- [X] T019 [US1] Create `IngestTransactionsService` (Domain `api/`) and `IngestTransactionsServiceImpl` (Domain `service/ingestion/`): rows → identity → port → per-row `IngestionResult`. Statuses come from what the write returned, never from a lookup. Keep this service free of any parsing so a future `BookingConsumer` can call it with already-structured input without redesign (FR-016, research R6).
- [X] T020 [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/statement/StatementIngestionController.java` implementing the generated delegate, wiring `POST /statements/ingest` (preview comes in US4). Use the parameter shape T004 actually produced.
- [X] T021 [US1] Integration test `integration-tests/src/test/java/.../StatementIngestionIntegrationTest.java` for quickstart scenario 1: import a statement, then import the identical file, and assert the second call records nothing and changes no balance. Use the existing `@SpringBootTest(classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class})` shape. **CI-only.**
- [X] T022 [US1] Integration test `integration-tests/src/test/java/.../ConcurrentIngestionIntegrationTest.java` for SC-004: two threads, released by a latch so both issue their write in the same window, importing overlapping statements into the same account. Assert each shared transaction exists exactly once. **This must be genuinely concurrent** — two sequential calls pass whether or not the constraint works, which would make SC-004 falsely green while the guarantee is absent (research R10 risk 4). **CI-only, and there is no honest local substitute.**

**Checkpoint**: Re-import is a no-op, enforced by the database rather than by looking first.

---

## Phase 4: User Story 2 - Two Identical Purchases on the Same Day Both Survive (Priority: P1)

**Goal**: Repeated genuine transactions are each recorded, while re-import stays a no-op.

**Independent Test**: Import a statement with two byte-identical rows, confirm two transactions
exist, re-import, confirm still exactly two (quickstart scenario 3).

**Depends on**: US1. This phase completes the identity algorithm US1 started.

### Implementation for User Story 2

- [X] T023 [US2] Extend `ExternalIdentityFactory` (T013) with the occurrence index: identity becomes `<hash>:<occurrence>`, where occurrence counts how many earlier rows **in the same file** shared that exact five-tuple, counting from zero **within its own identity group** — not globally across the file. The group-scoped counting is what makes overlapping statements converge; research R1 works through both import orders.
- [X] T024 [P] [US2] Domain unit tests in `ExternalIdentityFactoryTest`: two identical rows get `:0` and `:1`; three get `:0`, `:1`, `:2`; interleaved identity groups each count independently; a file with one occurrence and a file with two produce identities that overlap on `:0` and differ on `:1`. **Runs locally, and this is the single most valuable test in the feature** — the failure it guards is quiet, and a history that dropped the second coffee still looks entirely plausible.
- [X] T025 [US2] Integration test in `StatementIngestionIntegrationTest` for quickstart scenarios 2 and 3, using the two fixture files from quickstart: import `overlap-a` then `overlap-b`, assert 5 transactions with 2 coffees; then, **from an empty database, import them in the opposite order and assert the identical outcome.** Order-independence is what SC-002 actually claims — an operator importing a year of monthly statements in download order must land on one history. **CI-only.**

**Checkpoint**: US1 + US2 together are the shippable MVP.

---

## Phase 5: User Story 3 - Upgrade With Existing History Intact (Priority: P1)

**Goal**: Existing transactions survive untouched and manual entry keeps working.

**Independent Test**: On an instance with pre-existing transactions, upgrade and confirm every one is
still present, still appears in balances, and that adding one by hand still works (quickstart
scenario 5).

**Depends on**: Phase 2 (the migration). No dependency on US1 or US2.

### Implementation for User Story 3

- [ ] T026 [US3] Integration test in `StatementIngestionIntegrationTest`: create several transactions through the ordinary create endpoints (no identity), then assert they are readable, appear in balances, and carry `external_id IS NULL`. Then create two hand-entered transactions with identical account, date, amount and description, and assert **both** persist — the partial index must not treat two nulls as a collision (research R4). **CI-only.**
- [ ] T027 [US3] Confirm the whole existing integration suite still passes against the migrated schema. This is the real US3 verification and it costs nothing extra: ~111 tests boot the app under `ddl-auto=validate` against a Flyway-migrated database, so a `V2` that is wrong in any way Hibernate checks fails all of them at context startup — exactly how `V1` was verified in feature 021. **CI-only.**

**Checkpoint**: An operator's existing history is provably unaffected.

---

## Phase 6: User Story 4 - See What an Import Will Do Before Committing (Priority: P2)

**Goal**: The operator reviews new vs already-recorded rows, adjusts, and confirms — with every
judgement coming from the server.

**Independent Test**: Choose a file mixing new and already-recorded rows and confirm the preview
marks each correctly before anything is saved (quickstart scenario 6).

**Depends on**: US1 and US2 (there must be something to preview against).

### Implementation for User Story 4

- [ ] T028 [US4] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/ingestion/SuggestCategoryService.java`, porting feature 017's `suggestCategory` rule unchanged in behaviour: reuse the category of a similar past transaction by normalised description. Same rule, server side, so the operator loses nothing in the move. Learning from corrections stays a separate feature.
- [ ] T029 [US4] Add `POST /statements/preview` to `StatementIngestionController`: parse, derive identity, read which identities already exist, and return per-row status without writing anything. Document in the method that these marks are **advisory** — they come from a read that can be stale by confirm time, and nothing depends on them being right because the constraint is what enforces the invariant (research R7).
- [ ] T030 [US4] Wire `excludedRowIndexes` through the ingest path. **Occurrence indices are assigned during parsing over the whole file, before exclusions are applied** — if exclusion renumbered, re-importing later would offer the row the operator *kept* and hide the one they *rejected*, exactly inverted (research R8).
- [ ] T031 [P] [US4] Add `StatementPreview`, `IngestionResult`, `StatementRowPreview`, `StatementRowOutcome`, `RowStatus` and `TransactionDirection` to `frontend/src/types/index.ts`.
- [ ] T032 [P] [US4] Add `previewStatement()` and `ingestStatement()` to `frontend/src/api/client.ts` using `FormData` — note these are the project's first multipart requests, so they must not set `Content-Type` manually (the browser sets the boundary). Attach the bearer token and the standard session-death handling as the other helpers do.
- [ ] T033 [US4] Rewrite `frontend/src/components/ImportTransactionsDialog.tsx` to upload the file to `/statements/preview`, render the returned rows, and post the file plus excluded indices to `/statements/ingest`. **In the same commit**, delete `parseImportFile`, `detectDuplicates` and `suggestCategory` from `frontend/src/utils/transactionImport.ts`. Doing the deletion earlier leaves the app with no duplicate detection at all; doing it later leaves two implementations that can disagree (plan, Phase Ordering Note 3). Remove the now-unused `allTransactions` prop threading if nothing else needs it.
- [ ] T034 [US4] Integration test in `StatementIngestionIntegrationTest` for quickstart scenario 6: preview a two-identical-row file (both new), ingest with `excludedRowIndexes=[0]`, preview again, and assert row 0 is offered as **new** while row 1 is **already recorded**. This is the observable consequence of T030 — get it backwards and the test fails loudly instead of the operator discovering it quietly. **CI-only.**

**Checkpoint**: The operator sees what an import will do, and the numbers match what happens.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T035 Integration test for quickstart scenario 7: a statement containing one unparseable row imports the good rows and reports the bad one as `REJECTED` with a reason; a file that is not a statement at all fails the whole request and records **nothing** — never a partial import (FR-015). **CI-only.**
- [ ] T036 Update `README.md`: the API Overview table gains the two `/statements/*` endpoints, and the Roadmap's "Statement import" item is now partly delivered — CSV server-side with idempotent ingestion, with CAMT.053/MT940 still ahead. Do not overstate it.
- [ ] T037 Add a `CHANGELOG.md` entry under `[Unreleased]`, noting that imports are now idempotent, that feature 017's client-side duplicate detection is gone, and — for operators who imported with the old client-side dialog — that previously imported transactions carry no identity, so a re-import of an old statement will offer them as new. That last point is a genuine operator surprise and must not be discovered rather than read.
- [ ] T038 Run `./mvnw clean install -pl '!integration-tests'` and `cd frontend && npm run build`. Report the local result and the CI dependency **separately**: the migration, the constraint and the concurrency guarantee are verified only in CI.
- [ ] T039 Walk `quickstart.md` against what was delivered and correct any drift — particularly that the fixture CSVs and the endpoint shapes match the implementation.
- [ ] T040 Mark completed tasks `[X]` in this file, add an Implementation Outcome section recording any divergence from the plan, then commit and push to `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T004 gates T020 and T029.
- **Foundational (Phase 2)**: blocks all four stories.
- **US1 (Phase 3)**: after Foundational. **Not shippable alone** — see the phase warning.
- **US2 (Phase 4)**: after US1. Completes the identity algorithm.
- **US3 (Phase 5)**: after Foundational only. Independent of US1/US2 and can run in parallel with them.
- **US4 (Phase 6)**: after US1 and US2.
- **Polish (Phase 7)**: after all stories.

### User Story Dependencies

- **US1 (P1)** → **US2 (P1)**: one algorithm, split across two phases because they fail differently.
  Ship them together.
- **US3 (P1)**: independent. Its verification is nearly free — the existing suite is the test.
- **US4 (P2)**: needs US1 + US2.

### Parallel Opportunities

- T006/T007/T008/T009 (Foundational) — four different files.
- T014 and T016 (US1 Domain tests) — different test classes.
- T031 and T032 (US4 frontend) — different files; T033 depends on both.
- US3 can proceed alongside US1/US2 with a second person.

---

## Parallel Example: Phase 2 Foundational

```bash
Task: "Add externalId to Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java"
Task: "Add externalId to Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java"
Task: "Add externalId to Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java"
Task: "Add externalId to Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java"
```

---

## Implementation Strategy

### The MVP is US1 + US2, not US1 alone

This departs from the usual "US1 is the MVP" and the reason matters: US1 alone derives identity as a
plain hash, which collapses two genuine coffees into one. Shipping that would trade a duplication bug
for a silent-data-loss bug, and the second is worse — duplication is visible in a balance, loss is
not.

1. Phase 1 Setup — resolve the multipart unknown (T004) before anything depends on it
2. Phase 2 Foundational — the column exists, nothing uses it
3. Phase 3 US1 + Phase 4 US2 — **stop here and let CI run.** This is where the design is either
   right or wrong, and CI is the only place that can tell you.
4. Phase 5 US3 — nearly free; the existing suite is the verification
5. Phase 6 US4 — preview and the frontend swap
6. Phase 7 Polish

### Notes

- Commit per phase. T013+T023 (the identity algorithm) deserves its own commit and its own careful read.
- **Never edit `V1`.** Schema changes go in `V2`, and after this feature, `V3`.
- Do not report a Docker-dependent task as locally verified. Say what ran, what did not, and why.
