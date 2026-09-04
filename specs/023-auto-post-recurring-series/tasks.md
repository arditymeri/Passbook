---

description: "Task list for 023 Auto-Post Confirmed Recurring Series"
---

# Tasks: Auto-Post Confirmed Recurring Series

**Input**: Design documents from `/specs/023-auto-post-recurring-series/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Generated, and not optional. Principle VI is NON-NEGOTIABLE for financial logic, and this
feature writes financial records nobody typed. Unusually, most of the risk is locally testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Path Conventions

Multi-module Maven backend (`Domain/`, `Application/`, `Infrastructure/`, `Launcher/`, `Events/`,
`integration-tests/`) plus a Vite SPA in `frontend/`. Paths are repository-relative.

> **Environment**: no Docker daemon here. But this feature's risk is calendar arithmetic and a
> matching rule — pure Domain logic that **runs locally**. The migration, the uniqueness refusal,
> reconciliation-on-import and the scheduler are CI-only.

---

## Phase 1: Setup

- [ ] T001 Copy `specs/023-auto-post-recurring-series/contracts/recurring-autopost-controller.yaml` to `Application/src/main/resources/swagger/recurring/recurring-autopost-controller.yaml`, joining the five `recurring-*` specs already there.
- [ ] T002 Add a `recurring-autopost` execution to the `openapi-generator-maven-plugin` in `Application/pom.xml`, mirroring the existing executions: `<apiPackage>${api-package}.recurring</apiPackage>`, `<modelPackage>${model-package}</modelPackage>`, `skipOverwrite=true`, `delegatePattern=true`, `interfaceOnly=true`, `useSpringBoot3=true`.
- [ ] T003 Run `./mvnw -pl Application clean generate-sources`, confirm `RecurringAutoPostApi` and the `PostingRunResult` / `RecurringSeriesState` models generated, and commit the generated sources as the project does for every feature. Watch for the `JsonNullable` wrapping that bit features 018 and 019 — the contract avoids `nullable: true` on scalars for that reason, so this should be a confirmation rather than a fix.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the column, the new status, and — critically — the real-occurrence filter, which must
exist *before* anything writes.

**⚠️ CRITICAL**: T011 blocks T018. Adding posting first and the filter second leaves an intervening
state where the app learns from its own guesses, which is the bug that looks fine (research R2).

- [ ] T004 Create `Infrastructure/src/main/resources/db/migration/V3__add_recurring_series_origin.sql`: `ALTER TABLE bill ADD COLUMN recurring_series_id varchar(255)`, the same for `income`, and an index on each (`recurring_series_id`) — reconciliation looks these up on every import. Header comment explaining the three-origin table from data-model §2 and why no `origin` enum was added. **Do not touch `V1` or `V2`** — Flyway checksums applied migrations.
- [ ] T005 [P] Add `recurringSeriesId` (`@Column(name = "recurring_series_id")`) to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java`.
- [ ] T006 [P] Add the same field to `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java`.
- [ ] T007 [P] Add `private String recurringSeriesId;` to `Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java`.
- [ ] T008 [P] Add the same field to `Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java`.
- [ ] T009 Verify `BillMapperImpl` and `IncomeMapperImpl` in `Infrastructure/target/generated-sources/` carry `recurringSeriesId` in **both** directions. MapStruct maps same-named fields automatically, so this is a check — but check it: a silently unmapped provenance field would make every auto-posted row indistinguishable, which is the whole of US3.
- [ ] T010 Add `STOPPED` to `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/RecurringSeriesStatus.java`. No migration needed — `RecurringSeriesEntity.status` is already a `varchar` column holding the enum name.
- [ ] T011 Add a **real-occurrence** view to `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/RecurringSeriesMembers.java`: the members of a series excluding rows that are auto-posted (`recurringSeriesId != null`) or reversals (`reversal == true`). **Add a method; do not change `membersOf`** — features 015 (forecast) and 018 (dashboard) read it, and for them an auto-posted row genuinely is an occurrence (research R2). Add Domain tests covering: an auto-posted row is excluded, a reversal is excluded, an ordinary imported or hand-entered row is included. **Runs locally.**
- [ ] T012 Run `./mvnw clean install -pl '!integration-tests'` and confirm green. Nothing has changed behaviourally.

**Checkpoint**: provenance can be stored, a series can be stopped, and predictions cannot become evidence.

---

## Phase 3: User Story 1 - Rent Appears Without Being Typed (Priority: P1) 🎯 MVP

**Goal**: a confirmed series records its own transactions when due, including everything missed while
the instance was off, each exactly once.

**Independent Test**: confirm a series with an occurrence due, run posting, and find the transaction
(quickstart scenarios 1–3, 6–8).

### Implementation for User Story 1

- [ ] T013 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/recurring/OccurrenceSchedule.java` — pure calendar logic, no I/O: given a series' cadence, its latest real occurrence date, its confirmation date and a `today`, return every occurrence date due. Bounds: strictly after the latest real occurrence, **on or after the confirmation date** (research R4 — consent is not retroactive), and not after `today`. **Step with calendar arithmetic (`LocalDate.plusMonths`/`plusWeeks`/`plusYears`), NOT with `RecurringMatching.nominalInterval`** — that maps `MONTHLY` to `Duration.ofDays(30)`, which is right for a tolerance window and wrong for stepping: rent due on the 1st would wander to the 2nd, then the 3rd. Two different notions for two different jobs; do not unify them.
- [ ] T014 [P] [US1] Domain tests in `Domain/src/test/java/.../recurring/OccurrenceScheduleTest.java` for quickstart scenarios 3, 7 and 10: three weeks of downtime on a weekly series yields exactly three dates (not one, not twenty-one); nothing before the confirmation date is ever returned; a monthly series anchored on the 31st resolves February to the 28th/29th and returns to the 31st in March; a series with an occurrence today is due; a future date is not. **Runs locally — write this first, it is the feature's whole risk surface.**
- [ ] T015 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/recurring/PostingRunResult.java` — posted/already-posted/skipped-series counts plus the posted occurrences, per the contract.
- [ ] T016 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/recurring/GetAutoPostedTransactionsPersistencePort.java`: find a series' auto-posted transactions, for reconciliation and for FR-010's already-superseded check.
- [ ] T017 [US1] Implement it in `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/recurring/AutoPostedTransactionsPostgresAdapter.java`, querying on the indexed `recurring_series_id`.
- [ ] T018 [US1] Create `PostDueOccurrencesService` (Domain `api/`) and `PostDueOccurrencesServiceImpl` (Domain `service/recurring/`). **The method takes `LocalDate today` as a parameter** — reading the clock inside makes every scenario in quickstart untestable (research R7). For each `CONFIRMED` series: take its real occurrences (T011), derive amount and account from the latest one and category from the series' group key (research R3), ask `OccurrenceSchedule` what is due, and write each through feature 022's `IngestTransactionsPersistencePort` with identity `recurring:<seriesId>:<ISO date>` and `recurringSeriesId` set. **A series with no real occurrences is skipped and counted as skipped** — it has no account or amount, and FR-006 forbids inventing them.
- [ ] T019 [P] [US1] Domain tests in `.../recurring/PostDueOccurrencesServiceImplTest.java` for quickstart scenarios 1, 2, 6 and 8: a due occurrence is posted with values from the latest real occurrence; **the feedback-loop test — after posting February, March still derives its amount from the January real occurrence, not from February's prediction**; a series with no real occurrences is skipped, not posted; a `PROPOSED`, `DISMISSED` or `STOPPED` series posts nothing. **Runs locally. Scenario 6 is the highest-value test in the feature.**
- [ ] T020 [US1] Add `@EnableScheduling` to `Launcher/src/main/java/at/ymeri/my/finance/MyFinanceApplication.java` and create `Launcher/src/main/java/at/ymeri/my/finance/schedule/RecurringPostingScheduler.java` — a daily `@Scheduled` call passing `LocalDate.now()`, following `DemoDataSeeder` as the precedent for a non-HTTP driver here. **Gate it behind a property defaulting to on, and set that property off in `integration-tests/src/test/resources/application.yaml` in this same task** — every integration test boots the full app, and a live schedule would post against the shared container and make unrelated features' assertions flaky in ways that look nothing like the cause (research R8, plan Phase Ordering Note 3).
- [ ] T021 [US1] Create `Application/src/main/java/at/ymeri/my/finance/controller/recurring/RecurringAutoPostController.java` implementing the generated delegate, wiring `POST /recurring-series/post-due` (stop comes in US4).
- [ ] T022 [US1] Integration test `integration-tests/src/test/java/.../AutoPostIntegrationTest.java`: post-due records a due occurrence; calling it again records nothing (the uniqueness refusal, which only a real database can demonstrate); a proposed series is untouched. Use the existing `@SpringBootTest(classes = {MyFinanceApplication.class, TestConfig.class, TestDataSourceConfig.class, TestSecurityConfig.class})` shape. **CI-only.**

**Checkpoint**: confirmed series post themselves, catch up after downtime, and never post twice.

---

## Phase 4: User Story 2 - The Bank's Version Wins (Priority: P1)

**Goal**: an imported transaction matching an auto-posted one supersedes it, so the balance counts
the bank's figure once and the prediction stays visible.

**Independent Test**: auto-post an occurrence, import a matching statement, and check the balance
(quickstart scenarios 4–5).

**Depends on**: US1 — there is nothing to supersede until posting works. The one hard ordering
dependency between stories.

### Implementation for User Story 2

- [ ] T023 [US2] Create `ReconcileAutoPostedService` (Domain `api/`) and `ReconcileAutoPostedServiceImpl` (Domain `service/recurring/`): for a newly recorded transaction, find an auto-posted transaction on the same account and series whose date is within the cadence tolerance and whose amount is within the amount tolerance, and supersede it through feature 008's existing correction path — a compensating entry referencing the original, never a delete or an update. **Reuse `RecurringMatching.isWithinCadenceTolerance` and its 5%/€2.00 amount band** rather than inventing a second notion of "close enough" (research R5). Ambiguity resolves to the closest date, ties to the earlier period (FR-011); an entry that already has a reversal referencing it is not eligible (FR-010).
- [ ] T024 [P] [US2] Domain tests in `.../recurring/ReconcileAutoPostedServiceImplTest.java` for quickstart scenarios 4 and 5: a matching import supersedes; an amount 28% out does not; a date far outside the cadence window does not; two candidate predictions resolve to the closer one, deterministically; an already-superseded entry is not superseded again. **Runs locally.**
- [ ] T025 [US2] Call reconciliation from `Domain/src/main/java/at/ymeri/my/finance/domain/service/ingestion/IngestTransactionsServiceImpl.java` after rows are recorded — **from the service, not the controller**, so a future producer that ingests (the `BookingConsumer` stub) gets it without rewiring (plan, Structure Decision 3). Do **not** reconcile on manual entry: the operator typing is looking at their own history where the auto-posted row is visible and marked (research R5). Keep this additive — rows that supersede something are still reported `RECORDED`, and 022's existing ingestion tests must stay green.
- [ ] T026 [US2] Integration test `integration-tests/src/test/java/.../ReconciliationIntegrationTest.java` for quickstart scenario 4: auto-post rent, import a matching statement, and assert **the account balance counts it once** — the balance is the assertion that matters, not the row count, since three rows (prediction, reversal, import) is the correct outcome of a design that never deletes. Also assert the prediction is still present and unmodified. **CI-only.**

**Checkpoint**: auto-posting is safe alongside statement import. US1 + US2 is the shippable MVP.

---

## Phase 5: User Story 3 - See What the App Wrote On Your Behalf (Priority: P1)

**Goal**: auto-posted transactions are identifiable and traceable to their series.

**Independent Test**: with a mixed history, identify every transaction the app created, without
inspecting the database.

**Depends on**: US1 (there must be auto-posted rows to mark).

### Implementation for User Story 3

- [ ] T027 [US3] Add `recurringSeriesId` to the `bill` and `income` response models in `Application/src/main/resources/swagger/bill/` and `.../income/`, regenerate, and carry it through the response mappers. Additive and optional, so no existing client breaks (Principle VII).
- [ ] T028 [P] [US3] Add `recurringSeriesId?: string` to the transaction types in `frontend/src/types/index.ts`.
- [ ] T029 [US3] Mark auto-posted transactions in the transaction list in `frontend/src/components/RecentTransactions.tsx` (and wherever else transactions are listed) — a chip or similar, naming the series. The three origins are distinguished exactly as data-model §2 sets out; do not invent a fourth encoding in the frontend.

**Checkpoint**: "why is this row here?" is answerable for a row nobody typed.

---

## Phase 6: User Story 4 - Stop a Series (Priority: P2)

**Goal**: an operator ends a series' posting without losing its history.

**Independent Test**: stop a series, run posting, confirm nothing new and nothing lost
(quickstart scenario 9).

### Implementation for User Story 4

- [ ] T030 [US4] Create `StopRecurringSeriesService` (Domain `api/`) and `StopRecurringSeriesServiceImpl` (Domain `service/recurring/`): `CONFIRMED → STOPPED` only, rejecting other states as `ConfirmRecurringSeriesServiceImpl` already rejects non-`PROPOSED`. Already-posted transactions are untouched. Add Domain tests. **Runs locally.**
- [ ] T031 [US4] Wire `POST /recurring-series/{id}/stop` in `RecurringAutoPostController`, mapping the unknown-series and wrong-state cases to 404 and 409 as the contract states.
- [ ] T032 [P] [US4] Add `stopRecurringSeries()` and `postDueOccurrences()` to `frontend/src/api/client.ts`, and a Stop action on a confirmed series in the recurring series UI. A stopped series must remain listed and stay visually distinct from a dismissed one.
- [ ] T033 [US4] Integration test in `AutoPostIntegrationTest`: stopping ends posting within one run; the series is still listed with its history; already-posted transactions are unchanged; stopping a non-confirmed series is rejected. **CI-only.**

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T034 Update `README.md`: the API Overview gains the two `/recurring-series/*` endpoints, and the Roadmap's item 3 ("auto-posting confirmed recurring series — detection already exists and stops at detection") is now delivered. Say what it does *and* that a series posting to an account you never import will keep posting until you stop it — that is the honest shape of the feature, not a caveat to hide.
- [ ] T035 Add a `CHANGELOG.md` entry under `[Unreleased]`: confirmed series now post themselves, imported transactions supersede matching predictions, auto-posted rows are marked, and a series can be stopped. Note under operator action that **confirming a series now has a consequence it did not have before** — anyone with series confirmed under an older version will see posting begin, bounded to occurrences on or after confirmation.
- [ ] T036 Run `./mvnw clean install -pl '!integration-tests'`, `./mvnw -pl integration-tests test-compile`, and `cd frontend && npm run build`. Report the local result and the CI dependency **separately**.
- [ ] T037 Walk `quickstart.md` against what was delivered and correct any drift, particularly that the ten scenarios match the implemented behaviour and that the locally-executable ones were actually executed.
- [ ] T038 Mark completed tasks `[X]` in this file, add an Implementation Outcome section recording any divergence from the plan, then commit and push to `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T003 gates T021 and T031.
- **Foundational (Phase 2)**: blocks all stories. **T011 specifically blocks T018.**
- **US1 (Phase 3)**: after Foundational. Shippable alone in the narrow sense that it posts correctly —
  but see the MVP note below.
- **US2 (Phase 4)**: after US1. Nothing to supersede otherwise.
- **US3 (Phase 5)**: after US1. Independent of US2 and can run alongside it.
- **US4 (Phase 6)**: after Foundational (needs `STOPPED`) and US1 (needs posting to stop).
- **Polish (Phase 7)**: after all stories.

### User Story Dependencies

- **US1 (P1)**: the core. Everything else depends on it.
- **US2 (P1)**: needs US1.
- **US3 (P1)**: needs US1; independent of US2.
- **US4 (P2)**: needs US1.

Unlike 022, these stories are genuinely layered rather than parallel workstreams — US1 produces the
thing the other three act on.

### Parallel Opportunities

- T005–T008 (Foundational) — four different files.
- T014 and T019 (US1 Domain tests) — different test classes, though T019 needs T018.
- US2 and US3 can proceed simultaneously once US1 is done.
- T028 and T032 (frontend) — different concerns in different files.

---

## Parallel Example: Phase 2 Foundational

```bash
Task: "Add recurringSeriesId to Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/BillEntity.java"
Task: "Add recurringSeriesId to Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/IncomeEntity.java"
Task: "Add recurringSeriesId to Domain/src/main/java/at/ymeri/my/finance/domain/data/bill/BillDto.java"
Task: "Add recurringSeriesId to Domain/src/main/java/at/ymeri/my/finance/domain/data/income/IncomeDto.java"
```

---

## Implementation Strategy

### The MVP is US1 + US2

US1 alone posts correctly, and on an instance that never imports statements it is genuinely
complete. But on one that does — which 022 just made the normal case — US1 alone double-counts every
rent the bank also reports. Ship them together.

1. Phase 1 Setup — confirm the generated delegate (T003)
2. Phase 2 Foundational — **including T011's filter**, which is not optional groundwork
3. Phase 3 US1 — **write T014 before T013 if you prefer**; the schedule is where this feature is
   either right or wrong, and it needs no database
4. Phase 4 US2 — then let CI run: the balance assertion in T026 is the one that proves the feature
5. Phase 5 US3 and Phase 6 US4 — can proceed in either order
6. Phase 7 Polish

### Notes

- Commit per phase. T013 + T014 (the schedule) deserve their own commit and a careful read.
- **Never edit `V1` or `V2`.** Schema changes go in `V3`, and after this, `V4`.
- Do not report a Docker-dependent task as locally verified. Say what ran, what did not, and why.
