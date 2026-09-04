# Phase 1 Quickstart: Idempotent Statement Ingestion

**Feature**: 022-idempotent-statement-ingestion | **Date**: 2026-09-04

Validation scenarios, each mapped to a user story. Every scenario says whether it is **locally
executable** or **CI-verified** — this sandbox has no Docker daemon, so nothing touching PostgreSQL
runs here, and pretending otherwise would be the one thing that makes these documents useless.

The good news for this feature: the interesting logic — identity derivation, occurrence indexing,
CSV parsing — is pure Domain computation under plain JUnit, so the *hardest* part is locally
testable. What needs a database is the constraint and the concurrency guarantee.

---

## Fixture: the statements these scenarios use

`overlap-a.csv` — 1–31 January, one coffee on the 15th:

```csv
date,description,amount
2026-01-10,SUPERMARKET,-54.20
2026-01-15,COFFEE BAR,-3.40
2026-01-28,SALARY,2400.00
```

`overlap-b.csv` — 15 January – 15 February, **two** coffees on the 15th:

```csv
date,description,amount
2026-01-15,COFFEE BAR,-3.40
2026-01-15,COFFEE BAR,-3.40
2026-01-28,SALARY,2400.00
2026-02-03,PHARMACY,-18.90
```

Between them these two files exercise US1 and US2 simultaneously, in both import orders.

---

## Scenario 1 — Re-importing the identical file records nothing (US1, FR-004)

```bash
TOKEN=...   # from POST /api/v1/auth/login
ACCOUNT=... # an existing account id

curl -s -H "Authorization: Bearer $TOKEN" -F file=@overlap-a.csv -F accountId=$ACCOUNT \
  http://localhost:8080/api/v1/statements/ingest | jq '{recordedCount, alreadyRecordedCount}'
# → {"recordedCount": 3, "alreadyRecordedCount": 0}

curl -s -H "Authorization: Bearer $TOKEN" -F file=@overlap-a.csv -F accountId=$ACCOUNT \
  http://localhost:8080/api/v1/statements/ingest | jq '{recordedCount, alreadyRecordedCount}'
# → {"recordedCount": 0, "alreadyRecordedCount": 3}
```

**Expected**: the second call writes nothing, and the account's balance is identical before and
after it.

**Status**: CI-verified (needs PostgreSQL).

---

## Scenario 2 — Overlap in both directions converges (US1 + US2, SC-002)

The scenario that motivated research R1. Run it **twice, from an empty database each time**, once in
each order:

| Order | Expected coffee rows on 15 Jan | Expected total |
|---|---|---|
| `overlap-a` then `overlap-b` | 2 | 5 transactions |
| `overlap-b` then `overlap-a` | 2 | 5 transactions |

Five: supermarket, two coffees, one salary (the 28 Jan salary appears in both files and must not
double), pharmacy.

**The point is that both orders give the same answer.** An operator importing a year of monthly
statements in whatever order they were downloaded must land on one history.

**Status**: CI-verified.

---

## Scenario 3 — Two identical coffees both survive (US2, SC-003)

Import `overlap-b.csv` into an empty account.

**Expected**: two separate coffee transactions on 15 January, not one. Re-import: still two.

**Why this is the scenario to run first when something feels wrong** — it fails in the quietest
possible way. A history that collapsed the second coffee still looks entirely plausible; it is just
€3.40 wrong, forever, with nothing to indicate it.

**Status**: CI-verified. The *identity derivation* underneath it — that these two rows get
`H:0` and `H:1` — is a plain Domain unit test and **runs locally**.

---

## Scenario 4 — Simultaneous overlapping imports (US1 scenario 3, SC-004)

```
Thread A: ingest overlap-a.csv  ─┐
                                 ├─ started together, against the same account
Thread B: ingest overlap-b.csv  ─┘
```

**Expected**: each shared transaction exists exactly once, whichever thread wins any given row.

**This test must be genuinely concurrent.** Two sequential calls pass whether or not the constraint
works — they would make SC-004 falsely green while the guarantee it claims is absent (research R10).
Use a latch so both threads issue their write in the same window.

**Status**: CI-verified. There is no honest local substitute: the whole claim is about what the
database does when two writers race.

---

## Scenario 5 — Existing history is untouched by the upgrade (US3, FR-008)

**Setup**: an instance with transactions entered by hand before this version.

```bash
# Before upgrading — record the counts
docker compose exec -T postgres psql -U diti -d myfinance -c \
  "SELECT count(*) FROM bill UNION ALL SELECT count(*) FROM income;"

# Upgrade, start, then confirm nothing moved and nothing gained an identity
docker compose exec -T postgres psql -U diti -d myfinance -c \
  "SELECT count(*) FROM bill WHERE external_id IS NOT NULL;"
# → 0
```

**Expected**: counts unchanged; every pre-existing row still has `external_id IS NULL`; the app
starts (which means Hibernate's `validate` accepted the migrated schema); manual entry still works
and never trips the uniqueness rule.

**Status**: CI-verified for the schema half — every integration test boots against a Flyway-migrated
database under `ddl-auto=validate`, so a broken `V2` fails all of them at context startup, exactly as
`V1` did in feature 021.

---

## Scenario 6 — An excluded row comes back as new (US4, FR-014)

1. Preview `overlap-b.csv`. Two coffee rows appear, both marked new (`rowIndex` 0 and 1).
2. Ingest with `excludedRowIndexes=[0]`.
3. Preview the same file again.

**Expected at step 3**: row 0 is offered as **new**; row 1 is **already recorded**.

**Why this exact assertion**: it is the observable consequence of research R8 — occurrence indices
are assigned before exclusions. If exclusion renumbered, the surviving row would have taken `H:0`,
and this second preview would offer the row the operator *kept* while hiding the one they
*rejected*. Precisely inverted, and quiet.

**Status**: CI-verified end to end. The numbering rule underneath **runs locally** as a Domain test.

---

## Scenario 7 — A bad row is reported, not swallowed (FR-011, FR-015)

```csv
date,description,amount
2026-01-10,SUPERMARKET,-54.20
not-a-date,BROKEN ROW,-1.00
2026-01-28,SALARY,2400.00
```

**Expected**: two rows recorded, one `REJECTED` carrying a reason naming the unparseable date. The
bad row does not block the rows around it.

Separately, a file that is not a statement at all (an image, a truncated download) must fail the
whole request with an explanation and record **nothing** — never a partial import.

**Status**: parsing and rejection classification **run locally** as Domain tests; the
record-nothing-on-total-failure half is CI-verified.

---

## What runs locally, in one place

| Runs here | Needs CI |
|---|---|
| CSV parsing, including quoted fields and embedded newlines | The `V2` migration |
| Identity derivation from account/date/amount/description/direction | The partial unique index |
| Occurrence indexing, including exclusion stability (R8) | `ON CONFLICT … DO NOTHING` behaviour |
| Row rejection and its reasons | Concurrency (SC-004) |
| Category suggestion | Every end-to-end scenario above |

Report these two categories separately. A green local build is evidence about the first column only.
