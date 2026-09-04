# Phase 0 Research: Idempotent Statement Ingestion

**Feature**: 022-idempotent-statement-ingestion | **Date**: 2026-09-04

Decisions reached against the actual codebase. Where a design has a failure mode, it is named
rather than left for someone to discover in production.

---

## R1: Does an occurrence index actually survive overlapping statements?

This was the open question flagged at the end of spec-writing: the occurrence index is derived
*within one statement*, but uniqueness is enforced *in the database across all statements*. Those
are different scopes, and it was not obvious they reconcile.

**Decision**: they do, provided the index counts occurrences **within its own identity group**
(same account, date, amount, description, direction), numbered from zero in file order — not
globally across the file.

**Worked through, because the reasoning is the whole feature:**

| Case | Sequence | Result |
|---|---|---|
| Same file twice | F = [coffee, coffee] → `H:0`, `H:1` inserted. Re-import F → both exist | 2 rows. ✅ |
| Overlap, fewer first | A = [coffee] → `H:0`. B = [coffee, coffee] → `H:0` skipped, `H:1` inserted | 2 rows. ✅ |
| Overlap, more first | B = [coffee, coffee] → `H:0`, `H:1`. A = [coffee] → `H:0` skipped | 2 rows. ✅ |
| Three-way overlap | Any order of A, B, C covering the same day | Converges to max occurrences seen. ✅ |

The order-independence in rows 2 and 3 is what SC-002 needs: an operator importing a year of
monthly statements in any order lands on the same history.

**Why the identity group must include *direction* (bill vs income)**: `bill.amount` and
`income.amount` are both stored as positive `BigDecimal`; the sign lives in which table the row
goes to. Without direction in the hash, a €50 refund and a €50 charge on the same day with the
same description collide. This is a real and unremarkable pattern (a purchase and its refund often
carry the same merchant string).

**Why identity uses the calendar date, not the full timestamp**: statements supply dates; any
time-of-day is something we invent during parsing. Hashing an invented value would make identity
depend on parser behaviour rather than on the statement.

---

## R2: The failure mode this design has — stated, not buried

**A bank that splits a single calendar day across two statements loses a transaction.**

Statement A ends midday on 15 January containing the first coffee → `H:0`. Statement B begins
midday on 15 January containing the second coffee → also `H:0`, which already exists, so it is
skipped as already-seen. **The second coffee is silently never recorded.**

This is the exact inverse of US2, arrived at from the other direction, and honesty requires
stating it:

- **Why it is nonetheless the right design**: every alternative is worse. Strict hashing without an
  occurrence index loses *every* repeated transaction rather than a rare one. Asking the operator
  puts data-entry work back in front of them, which is the problem the pipeline exists to remove.
- **Why it is rare in practice**: consumer statement boundaries are day-granular. It requires a
  bank that cuts mid-day *and* an operator with two identical transactions straddling that cut.
- **Why it is avoidable entirely**: FR-001 prefers a source-supplied transaction identifier
  whenever the statement carries one, and derived identity is only the fallback. Most modern
  exports carry one.

**Recommended mitigation, deliberately NOT built here**: let the operator force-include a row the
preview marked already-recorded, which would assign it the next free occurrence index. It is cheap
and it makes this case recoverable. It is not in the spec's requirements, so adding it silently
would be scope expansion — it is raised for the operator to decide, and is a small follow-up
either way.

---

## R3: Enforcing uniqueness without check-then-write

**Decision**: a single `INSERT … ON CONFLICT (account_id, external_id) WHERE external_id IS NOT
NULL DO NOTHING RETURNING id, external_id`, issued from an Infrastructure adapter.

The `RETURNING` clause names exactly the rows that actually landed; everything submitted and not
returned was already present. That yields FR-011's per-row outcome **from the write itself**,
rather than from a lookup that could disagree with it.

**Why not the obvious approach** — insert each row, catch the unique-violation exception, continue:

1. In PostgreSQL a constraint violation aborts the enclosing transaction. Recovering means a
   transaction per row or a savepoint per row.
2. It is *N* round trips for an *N*-row statement.
3. It is check-then-write wearing a different hat, and FR-005 exists to rule that family out.

**Constitutional note**: this is raw SQL, which the Development Workflow permits — *"raw SQL/JPQL
belongs exclusively in Infrastructure adapters"* — and forbids in Domain. `spring-jdbc` 6.2.0 is
already on Infrastructure's classpath transitively via `spring-boot-starter-data-jpa`, so
`NamedParameterJdbcTemplate` needs no new dependency.

**Concurrency claim being made**: two simultaneous imports of overlapping statements cannot both
insert the same identity, because the unique index — not application code — arbitrates. SC-004
asks for this in 100% of attempts, so it needs a genuinely concurrent integration test (two
threads, real PostgreSQL), not a sequential one that merely looks concurrent.

---

## R4: Tolerating rows that have no identity at all

**Decision**: `external_id` is nullable, and the unique index is **partial** —
`WHERE external_id IS NOT NULL`.

Two populations must coexist (FR-007, FR-008): transactions typed by hand, which have no external
identity by definition, and transactions recorded before this feature existed, which must not be
retrofitted with invented identities — doing so would assert a provenance that never happened.

PostgreSQL treats NULLs as distinct in a unique index, so a plain unique constraint would already
tolerate many NULL rows. The partial predicate is still preferable: it states the intent, and it
keeps the index to just the ingested rows rather than indexing every manual entry ever made.

**Known hole, and why it is unreachable**: `bill.account_id` is nullable in the current schema
(baseline `V1`), and a row with a NULL `account_id` and a non-NULL `external_id` would be treated
as distinct from another such row. Ingestion always sets the account — the operator chooses it as
part of the import — so this is not reachable through any code path this feature adds.

**Hibernate note**: `ddl-auto=validate` checks tables, columns and column types, not indexes
(established in 021 research R4). The partial unique index therefore lives only in the migration.
The `external_id` *column* must still be mapped on the entity, which is what makes `validate`
confirm it exists.

---

## R5: Migration shape — the first one after the baseline

**Decision**: `V2__add_external_transaction_identity.sql` in
`Infrastructure/src/main/resources/db/migration/`, adding one nullable column to `bill` and one to
`income`, plus a partial unique index on each. No backfill.

This is the first real schema change on feature 021's Flyway baseline, and it is deliberately the
easy shape: additive, nullable, no data movement. `ALTER TABLE … ADD COLUMN` with no default is a
catalogue-only operation in modern PostgreSQL, so it does not rewrite an operator's table.

**Do not touch `V1`.** Flyway checksums applied migrations; editing the baseline after any instance
has run it turns every subsequent startup into a validation failure. This is stated in `V1`'s own
header and repeated here because "just add the column to the baseline" is the tempting wrong move.

**Two indexes, not one shared one.** `bill` and `income` are separate tables with separate indexes.
A given statement row becomes one or the other, never both, and direction is part of the derived
hash (R1), so an identity cannot legitimately appear in both.

---

## R6: Where parsing lives, and how a future Kafka consumer reuses this

**Decision**: two Domain services, not one.

- `ParseStatementService` — CSV text → structured rows. Pure computation.
- `IngestTransactionsService` — structured rows → identity derivation → persistence port →
  per-row result.

**Why split**: FR-016 requires the ingestion capability to be usable by a caller other than the
file-upload path without redesign. `BookingConsumer` — today a stub that logs its message and
returns — would receive already-structured bookings, not CSV text. If parsing were fused into
ingestion, that consumer would have to either fabricate CSV or force a redesign. Splitting costs
nothing now and is the difference between FR-016 being true and being aspirational.

**Why parsing stays in Domain rather than behind a port**: Principle VIII requires ports for *I/O*.
Turning a string into a list of rows is computation, not I/O — there is no external system to
mediate. A port here would exist for symmetry alone, which the constitution's "no speculative
generality" rule prohibits. It also keeps identity derivation and parsing under plain, fast JUnit
with no application context, which Principle VIII explicitly wants. If a future format needs
streaming or network access, *that* is when the port earns its place.

**CSV library**: `org.apache.commons:commons-csv`, added explicitly (Spring Boot does not manage
its version). Rejected hand-rolling it: quoted fields containing commas, escaped quotes, and
embedded newlines are exactly the cases a hand-rolled parser gets wrong, and the failure mode is a
silently mis-parsed financial record. Feature 017's frontend `parseCsvLine` is line-oriented and
cannot represent an embedded newline at all — one of several reasons it is being retired rather
than ported.

---

## R7: Preview and confirm without server-side session state

**Decision**: both endpoints take the **file**. Confirm additionally takes the row indices the
operator excluded. The server re-parses and re-derives identity on confirm; the client never
handles an identity value at all.

**Why not the obvious alternative** — preview returns rows with their identities, and confirm posts
those rows back: it puts identity values in the client's hands. FR-010 says exactly one place
decides identity, and a client that echoes an identity is a client that can echo a wrong one. The
worst case is not dramatic on a single-user instance, but the resulting row would carry an identity
that no re-parse of the statement would ever reproduce — permanently invisible to future
deduplication, which is the one thing this feature exists to guarantee.

**Why not a staging store keyed by a token**: it introduces expiry, cleanup, and a second source of
truth for what a statement contains. Re-uploading a file that is measured in kilobytes is cheaper
than any of that.

**Parsing is deterministic**, so re-parsing on confirm yields byte-identical identities. This is a
property the implementation must not break — notably, the occurrence index must be assigned over
the **whole file, before exclusions are applied** (R8).

**Preview is advisory; the constraint is authoritative.** The preview's "already recorded" marks
come from a read, which can be stale by the time the operator confirms. That is fine and expected:
nothing depends on the preview being right, because the write is what enforces the invariant.

---

## R8: Exclusions must not renumber

**Decision**: occurrence indices are assigned during parsing over every row in the file, before any
exclusion is applied.

If exclusion renumbered, this would happen: a file contains two identical coffees (`H:0`, `H:1`).
The operator excludes the first and confirms. If the survivor were renumbered to `H:0`, then
re-importing the same file later would find `H:0` present and `H:1` absent — offering the operator
the row they *kept* as new, and hiding the one they *rejected*. Exactly inverted.

With stable indices, the survivor is `H:1`, and a later re-import correctly offers `H:0` — the one
they excluded — as new. That is FR-014 satisfied precisely: *"a row excluded by the operator MUST be
offered again as new on a later import of the same statement."*

---

## R9: What happens to feature 017

**Decision**: `parseImportFile`, `detectDuplicates` and `suggestCategory` are deleted from
`frontend/src/utils/transactionImport.ts`. `ImportTransactionsDialog` keeps its shape — file picker
→ review table → confirm — but every judgement in it now comes from the server.

Feature 017's own plan states it added *"no new backend endpoint, no new Domain service, no new
persisted data"*. Its `detectDuplicates` compares a candidate against `existingOnAccount` — the
transactions the browser happens to have loaded — on same calendar day, normalised description and
amount. That is a useful UI convenience and it is not idempotency: it cannot see transactions
outside the loaded window, cannot protect a second device, and has no effect on anything arriving
by any other path.

**Two implementations of "is this a duplicate" that can disagree is worse than either alone.** Only
the server's answer can be authoritative, so the client's is removed rather than kept as a
"fast path".

`suggestCategory` moves server-side unchanged in behaviour — the same reuse-a-previous-category
rule, so the operator loses nothing. Learning from corrections remains a separate feature.

---

## R10: Risks to check early rather than late

Recorded because each has cost a rework cycle in an earlier feature of this project:

1. **Multipart through the OpenAPI generator.** Every existing endpoint in this project is
   JSON-in/JSON-out. `multipart/form-data` with the delegate pattern is untried here, and the
   generated signature (`MultipartFile` vs `Resource` vs a byte array) is not obvious. **Generate
   the sources and look at the delegate interface before writing any controller** — the same
   discipline that caught the `JsonNullable` wrapping in features 018 and 019.
2. **`ON CONFLICT … WHERE` conflict-target syntax.** Inferring a *partial* index requires repeating
   the index predicate in the `ON CONFLICT` clause. Omitting it produces
   *"there is no unique or exclusion constraint matching the ON CONFLICT specification"* at
   runtime, not at build time.
3. **Batch `RETURNING` through `NamedParameterJdbcTemplate`.** `batchUpdate` discards returned rows.
   A multi-row `VALUES` list in a single statement is needed to get `RETURNING` back.
4. **The concurrency test must actually be concurrent.** Two sequential calls will pass whether or
   not the constraint works, which would make SC-004 falsely green.

**Verification reality**: this sandbox has no Docker daemon, so nothing touching PostgreSQL —
the migration, the constraint, the concurrency test — can be run locally. Domain-level identity
derivation and CSV parsing are plain JUnit and run here. Everything else is CI-verified, exactly as
in feature 021.
