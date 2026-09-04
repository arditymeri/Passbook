# Implementation Plan: Auto-Post Confirmed Recurring Series

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-auto-post-recurring-series/spec.md`

## Summary

Make confirming a recurring series mean something. `ConfirmRecurringSeriesServiceImpl` sets a status
flag and writes nothing; a confirmed series should record its own transactions so rent, salary and
subscriptions stop being typed.

Three decisions carry the design.

**Posting is stateless.** Every run recomputes the whole set of currently-due occurrences and
attempts each. There is no cursor, no "last run" timestamp, no separate catch-up path — three weeks
of downtime and a run an hour ago take the identical code path, because feature 022's uniqueness
guarantee makes a repeat attempt a no-op. The alternative is progress state that can be
written-but-not-committed, or reset by a restore from backup, each silently skipping or repeating
someone's rent (research R1).

**A prediction is never evidence.** `RecurringSeriesMembers.membersOf` filters nothing beyond the
series' grouping, so this feature's own output would become "occurrences" of the series the moment it
starts writing. Left alone that gives a system learning from its own guesses: the anchor drifts onto
invented dates, and a rent increase is never picked up because each month copies the last month's
prediction. The anchor and derived values come from a filtered view excluding auto-posted rows and
reversals (research R2). **This is the bug that would have shipped looking fine.**

**Supersession reuses the correction path.** When an import brings the bank's own rent, the
prediction is corrected away exactly as a manual correction does it — a compensating entry, the
original untouched. Principle I holds by construction and existing history views already explain it.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5 / React 18 (frontend)

**Primary Dependencies**: Spring Boot 3.4.0; Flyway 10.20.1; Hibernate 6.6.2; OpenAPI Generator
7.0.1 (delegate pattern); MapStruct 1.5.5. **No new dependency** — `@Scheduled` is Spring core, and
`@EnableScheduling` is new to this codebase but not new to the framework.

**Storage**: PostgreSQL. One nullable, indexed `varchar(255)` column added to `bill` and `income`.
`RecurringSeriesStatus` gains a `STOPPED` value — a string column already, so no schema change for it.

**Testing**: JUnit 5 in Domain for occurrence generation, the confirmation bound, the real-occurrence
filter and the supersession matching rule — all pure calendar and comparison logic, all runnable
here. Testcontainers PostgreSQL in `integration-tests` for the migration, uniqueness refusing a
repeat, reconciliation inside the import path, and stopping. No frontend test runner; `npm run build`
type-checks.

**Target Platform**: Linux/macOS self-hosted, Docker Compose

**Project Type**: Multi-module Maven backend (hexagonal) + Vite SPA

**Performance Goals**: Not a performance feature. One shape worth stating: a series a year past its
last real occurrence re-attempts twelve inserts per daily run. That is deliberate and cheap
(research R1); optimising it away would mean introducing the state the design exists to avoid.

**Constraints**:

- **The posting service must take "today" as a parameter.** Everything interesting here is calendar
  behaviour, and none of it is testable if the clock is read inside the logic. This is the difference
  between most of the feature being verifiable in this sandbox and almost none of it being.
- **The real-occurrence filter must be additive.** `membersOf` is shared with feature 015's forecast
  and 018's dashboard, and for those an auto-posted row genuinely *is* an occurrence. A new method,
  not a changed one.
- **The scheduler must not fire during tests.** Every integration test boots the full application; a
  live schedule would post against the shared database and make unrelated features' assertions flaky
  in ways that look nothing like the cause.
- **No Docker daemon here.** As in 021 and 022 — but this time most of the risk is locally testable.

**Scale/Scope**: Single household, single instance. Roughly: 1 migration, 1 new status value, 3 new
Domain services, 1 reconciliation service hooked into 022's ingestion path, 2 endpoints, a scheduled
trigger, and a small frontend surface for stopping a series and marking auto-posted rows.

## Constitution Check

*Constitution v2.1.0. Evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | Verdict | Reasoning |
|---|---|---|
| **I. Transaction Immutability** | ✅ Upheld — and load-bearing | Supersession writes a compensating entry through feature 008's existing path; the prediction is never modified or deleted. This is the principle that dictated the *design* rather than merely being satisfied by it: "delete the prediction when the real one arrives" is the obvious approach and Principle I forbids it. |
| **II. Ingestion Is Idempotent** | ✅ Upheld | Auto-posted transactions arrive from outside the UI and carry a stable external identity — `recurring:<seriesId>:<date>` — making this the second producer to honour the principle, on 022's machinery. FR-004's guarantee *is* Principle II applied to a second source. |
| **III. Balance Derivation** | ✅ Upheld | Nothing is stored as a running total. A superseded prediction and its reversal net to zero at read time, which is exactly why the balance ends at the bank's figure without anything being recomputed or cached. |
| **IV. Currency Precision** | ✅ Upheld | Amounts are copied `BigDecimal`-to-`BigDecimal` from a past occurrence; no arithmetic is performed on them, so there is nothing to round. Tolerance comparisons reuse `RecurringMatching`'s existing `BigDecimal` constants. |
| **V. Audit Trail** | ✅ Upheld — and the reason for the column | The principle says provenance matters *more* once rows arrive automatically. `recurring_series_id` is what makes "why is this row here?" answerable for a transaction nobody typed, and US3 exists to enforce it. |
| **VI. Test-First** | ✅ Upheld, and well-served | Occurrence generation, the confirmation bound, the real-occurrence filter and the matching rule are all pure Domain logic under plain JUnit. The database guarantees get Testcontainers integration tests against real PostgreSQL, as the principle requires. |
| **VII. API Contract Stability** | ✅ Upheld | Two additive endpoints, specified in OpenAPI before implementation. `RecurringSeriesStatus` gains a value, which is additive for clients that pass it through and worth noting for any that switch exhaustively on it — the frontend does not. |
| **VIII. Hexagonal Architecture** | ✅ Upheld | All logic in Domain; the scheduled trigger is an inbound driver in Launcher (alongside `DemoDataSeeder`, the existing precedent for a non-HTTP driver); persistence through ports. |

**Pipeline-first bias — this is the second producer in two features.** The Development Workflow says
a feature consuming transaction data should state how that data arrives without manual entry, and
that the ratio is watched. 022 made statements arrive safely; this makes the transactions an operator
already told the app to expect arrive on their own. Together they are the first real movement on the
constitution's central claim that the pipeline should fill itself.

**One tension worth naming at gate time.** Auto-posting writes financial records that no human
entered and that may never be confirmed by a bank — for an account the operator does not import,
nothing ever supersedes a prediction, and a cancelled subscription keeps posting until they notice
and stop it. The feature's honest position is that this is the operator's own confirmed expectation
being honoured, that US3 makes every such row identifiable, and that US4 gives them the stop. It is
not a violation, but it is the first time the app writes a financial record on a prediction rather
than on a fact, and that deserved saying out loud rather than being discovered in the diff.

**Gate result: PASS.** No violations requiring justification; Complexity Tracking is omitted.

## Project Structure

### Documentation (this feature)

```text
specs/023-auto-post-recurring-series/
├── plan.md                                    # This file
├── spec.md                                    # Feature specification
├── research.md                                # Phase 0 — 8 decisions, incl. the R2 feedback loop
├── data-model.md                              # Phase 1 — one column, one status, and what is not modelled
├── quickstart.md                              # Phase 1 — 10 scenarios, most runnable locally
├── contracts/
│   └── recurring-autopost-controller.yaml     # Phase 1 — POST /recurring-series/{id}/stop, /post-due
├── checklists/requirements.md                 # From /speckit-specify — all items pass
└── tasks.md                                   # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
Infrastructure/
├── src/main/resources/db/migration/
│   └── V3__add_recurring_series_origin.sql    # NEW — nullable indexed column on bill and income
├── src/main/java/.../entity/
│   ├── BillEntity.java                        # + recurringSeriesId
│   └── IncomeEntity.java                      # + recurringSeriesId
└── src/main/java/.../adapter/postgres/recurring/
    └── AutoPostedTransactionsPostgresAdapter.java  # NEW — find a series' auto-posted rows

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/recurring/
│   ├── RecurringSeriesStatus.java             # + STOPPED
│   └── PostingRunResult.java                  # NEW — what a run did
├── api/
│   ├── PostDueOccurrencesService.java         # NEW — takes `today`; see Constraints
│   ├── StopRecurringSeriesService.java        # NEW
│   └── ReconcileAutoPostedService.java        # NEW — called from the ingestion path
├── service/recurring/
│   ├── OccurrenceSchedule.java                # NEW — which dates are due. Pure calendar logic.
│   ├── RecurringSeriesMembers.java            # + a real-occurrence view (ADDITIVE — see Constraints)
│   ├── PostDueOccurrencesServiceImpl.java     # NEW
│   ├── StopRecurringSeriesServiceImpl.java    # NEW
│   └── ReconcileAutoPostedServiceImpl.java    # NEW — matching + supersession
├── service/ingestion/
│   └── IngestTransactionsServiceImpl.java     # calls reconciliation after recording
└── spi/recurring/
    └── GetAutoPostedTransactionsPersistencePort.java  # NEW

Application/
├── pom.xml                                    # + openapi-generator execution: recurring-autopost
├── src/main/resources/swagger/recurring/
│   └── recurring-autopost-controller.yaml     # NEW — copied from contracts/
└── src/main/java/.../controller/recurring/
    └── RecurringAutoPostController.java       # NEW

Launcher/src/main/java/at/ymeri/my/finance/
├── MyFinanceApplication.java                  # + @EnableScheduling
└── schedule/
    └── RecurringPostingScheduler.java         # NEW — daily trigger; disabled in tests

integration-tests/src/test/java/.../
├── AutoPostIntegrationTest.java               # NEW — posting, stopping, uniqueness
└── ReconciliationIntegrationTest.java         # NEW — import supersedes a prediction; balances

frontend/src/
├── types/index.ts                             # + STOPPED, PostingRunResult
├── api/client.ts                              # + stopRecurringSeries, postDueOccurrences
└── components/                                # auto-posted marker; Stop action on a series
```

**Structure Decision**: the existing hexagonal layout, unchanged. Four placements were deliberate:

1. **`OccurrenceSchedule` is its own pure class**, separate from the service that uses it. It is the
   calendar arithmetic — cadence stepping, month-end resolution, the confirmation bound — and
   isolating it is what makes quickstart scenarios 3, 7 and 10 plain JUnit tests rather than
   integration tests.
2. **The real-occurrence view is added to `RecurringSeriesMembers`, not substituted into it.** Two
   other features read that class and, for them, an auto-posted row *is* an occurrence.
3. **Reconciliation is called from `IngestTransactionsServiceImpl`**, not from the controller, so
   any future producer that ingests — the `BookingConsumer` stub 022 kept the door open for — gets it
   without rewiring.
4. **The scheduler lives in Launcher**, following `DemoDataSeeder`. Launcher is where this project
   already puts drivers that are neither HTTP nor persistence.

## Phase Ordering Note

`/speckit-tasks` will sequence this; four constraints are not obvious from the story priorities.

1. **`OccurrenceSchedule` and its tests come before anything that writes.** It is the feature's whole
   risk surface and it is testable with no database. Getting it right first means every later failure
   is a wiring failure rather than a logic one.
2. **The real-occurrence filter (R2) must land with the first posting code, not after.** Add posting
   first and the filter second, and the intervening state is a system that learns from its own
   guesses — which is exactly the bug that looks fine.
3. **Disable the schedule in tests in the same increment that enables it.** Otherwise the next
   integration run posts against the shared container and the resulting flakiness surfaces in
   whichever feature's test happens to read a balance next.
4. **US2's reconciliation needs US1's posting to exist**, so there is nothing to supersede until
   posting works. It is the one hard ordering dependency between stories.
