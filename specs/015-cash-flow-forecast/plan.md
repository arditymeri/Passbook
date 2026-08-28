# Implementation Plan: Cash Flow Forecast

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-08-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-cash-flow-forecast/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Project each account's balance forward through a near-future window using the confirmed
recurring bill/income series the app already recognizes, so a user sees a clear warning when an
account is on track to run low or negative, plus the day-by-day timeline that explains why.
Unlike features 012-014, this requires genuinely new Domain logic — multi-occurrence recurring
prediction and per-account running-balance projection — behind a new read-only REST endpoint,
since no existing endpoint predicts more than one future occurrence per series or projects a
balance over a sequence of dated events.

## Technical Context

**Language/Version**: Java 21 (Domain/Application/Infrastructure/Launcher); TypeScript 5 / React 18 (frontend, Vite)

**Primary Dependencies**: Spring Boot 3.4.0, MapStruct (Application DTO↔API mapping), OpenAPI Generator (Application, delegate pattern), Spring Data JPA (Infrastructure — unused by this feature, no new persistence), MUI (frontend)

**Storage**: PostgreSQL — no new tables/columns. Forecast is fully derived at read time from existing `accounts`, `bills`, `incomes`, and `recurring_series` data (Constitution Principle III).

**Testing**: JUnit 5 for new Domain logic (Constitution Principle VI, Test-First — applies here, unlike 012/013/014, because this feature introduces real Domain business logic). No frontend test runner exists anywhere in this repo (confirmed across every prior feature); this feature does not introduce one, consistent with that pre-existing, non-worsened gap.

**Target Platform**: Linux server (Docker Compose: Postgres + Kafka + app) + browser SPA

**Project Type**: Web application — existing hexagonal Maven multi-module backend (Domain/Application/Infrastructure/Launcher) + Vite React SPA frontend

**Performance Goals**: Forecast recompute on a window change completes within a couple of seconds (SC-003) — trivially satisfied: personal-finance scale (few accounts, tens of confirmed series at most) computed synchronously with no external calls beyond existing in-process reads.

**Constraints**: Strictly read-only (FR-008) — no new write paths. Must read bill/income history through the existing correction-aware `GetBillService`/`GetIncomeService.getAll()` (never the raw SPI ports) so corrections are always reflected (FR-007). Must reuse the existing single-step `RecurringMatching.predictNextDate` primitive for every predicted date rather than reimplementing cadence math, so the recurring dashboard's "next occurrence" and the forecast's occurrences can never diverge.

**Scale/Scope**: Personal-finance scale — a handful of accounts, tens of confirmed recurring series, a forecast window of 2-12 weeks. No pagination or streaming required; one aggregate endpoint returns every account's forecast in a single response.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Transaction Immutability | PASS | Feature is entirely read-only (FR-008); no bill/income/account/series record is created, modified, or reversed by this feature. |
| II. Double-Entry Accounting | N/A | No new journal lines are produced — nothing is persisted. |
| III. Account Integrity & Balance Derivation | PASS | The forecast's starting point is the existing `GetAccountService`-derived `balance` (never a separately stored value); the projection itself is never cached or persisted, recomputed fresh on every request. |
| IV. Currency Precision | PASS | All new DTO amount fields (`ForecastEntryDto.amount`, `AccountForecastDto.currentBalance`, `projectedBalance`) are `BigDecimal` end-to-end through Domain/Application. Frontend continues the established, previously-accepted precedent (012-014) of JS `number` for display only — not a new deviation introduced by this feature. |
| V. Audit Trail & Observability | N/A | No state-changing operation exists in this feature to audit. |
| VI. Test-First Development | PASS (applies, unlike 012-014) | New Domain logic — `RecurringMatching.predictOccurrencesWithinWindow` and `GetCashFlowForecastServiceImpl` — gets JUnit tests written alongside implementation per user story, covering: single/multiple occurrences within window, overdue-series "due now" handling, correction-reflecting amounts, already-negative accounts, and no-confirmed-series flat forecasts. |
| VII. API Contract Stability | PASS | New endpoint `GET /cash-flow-forecast` defined in OpenAPI YAML first (`contracts/forecast-api.yaml`, `contracts/forecast-model.yaml`), purely additive — no existing contract changes. |
| VIII. Hexagonal Architecture Compliance | PASS | New prediction/projection logic lives in Domain (`GetCashFlowForecastServiceImpl`, extended `RecurringMatching`), with zero Spring/JPA/Kafka dependency; Application only adds a thin controller + MapStruct mapper translating the Domain DTOs to the generated API model. |

No violations requiring justification — Complexity Tracking table is empty/omitted.

## Project Structure

### Documentation (this feature)

```text
specs/015-cash-flow-forecast/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── forecast-api.yaml
│   └── forecast-model.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/forecast/
│   ├── ForecastEntryDto.java            # NEW
│   └── AccountForecastDto.java          # NEW (+ CashFlowForecastResult.java wrapper)
├── api/forecast/
│   └── GetCashFlowForecastService.java  # NEW interface (port)
└── service/
    ├── forecast/
    │   └── GetCashFlowForecastServiceImpl.java  # NEW
    └── recurring/
        ├── RecurringMatching.java                    # MODIFIED: + predictOccurrencesWithinWindow
        ├── RecurringSeriesMembers.java                # NEW: membersOf() extracted here, shared
        └── GetUpcomingRecurringServiceImpl.java       # MODIFIED: delegates to RecurringSeriesMembers

Domain/src/test/java/at/ymeri/my/finance/domain/
├── service/forecast/GetCashFlowForecastServiceImplTest.java  # NEW
└── service/recurring/RecurringMatchingTest.java               # MODIFIED: + multi-occurrence cases

Application/src/main/resources/swagger/forecast/
├── forecast-get-controller.yaml   # NEW: GET /cash-flow-forecast
└── forecast-model.yaml            # NEW: cashFlowForecastResponse, accountForecast, forecastEntry

Application/src/main/java/at/ymeri/my/finance/application/
├── controller/forecast/CashFlowForecastController.java  # NEW: implements generated delegate
└── mapper/forecast/CashFlowForecastMapper.java           # NEW: MapStruct, Domain DTO -> API model

frontend/src/
├── types/index.ts                       # MODIFIED: + CashFlowWindowWeeks, ForecastEntry, AccountForecast, CashFlowForecastResponse
├── api/client.ts                        # MODIFIED: + fetchCashFlowForecast(weeks)
├── components/CashFlowForecastCard.tsx  # NEW: per-account cards, window ToggleButtonGroup, timeline list, at-risk styling
└── App.tsx                              # MODIFIED: mount CashFlowForecastCard near NetWorthCard
```

**Structure Decision**: Existing hexagonal web-application layout (Option 2 pattern, already
established by this repo as Domain/Application/Infrastructure/Launcher + `frontend/`). No new
top-level module — Infrastructure and Launcher need no changes since nothing new is persisted
and no new Spring bean requires manual wiring beyond `@Service`/`@Component` scanning already in
place for every other Domain service and Application controller.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
