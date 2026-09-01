# Research: Device Sync via File Export

## R1: How does an import insert a row that preserves another device's id?

**Decision**: Merge logic sets the DTO's `id` field from the incoming snapshot before calling the *same* persistence-port method already used for normal writes (e.g. `AddBillPersistencePort.addBill`, `SetBudgetPersistencePort.upsert`), rather than building parallel "insert-with-given-id" adapters.

**Rationale**: Every entity's Spring Data JPA repository already goes through `save()` on a MapStruct-mapped entity. Spring Data JPA's default `save()` behavior for a `@GeneratedValue` entity is: id `null` → `persist()` (server generates a fresh id); id non-null → `merge()` (Hibernate looks the id up, and if no row exists with it, inserts a new row using that exact id). `SetBudgetPostgresAdapter.upsert` already relies on exactly this mechanism today for its update path. Reusing it for "insert preserving a foreign id" needs no new adapter code, only that the merge service populates `id` before calling in.

**Verification needed during implementation**: This is standard, well-documented Hibernate behavior, but this repo has no integration-test run available in this sandbox (no Docker) to confirm it end-to-end against real Postgres. Flag as the first thing to verify once Docker is available — if it doesn't hold for any entity, the fallback is a native `INSERT ... ON CONFLICT DO NOTHING`-style query method on that one repository, not a redesign.

**Alternatives considered**: A dedicated `entityManager.persist()` call bypassing `@GeneratedValue` entirely (rejected — needs per-entity native queries or generator overrides, more invasive than reusing `save()`); a UUIDv7/ULID switch for sortable ids (rejected — solves a different problem, not needed here).

## R2: Matching entities across devices — id alone is not enough

**Decision**: Each entity type merges using its id when present, but falls back to matching by its existing **database unique constraint** (its natural key) when two devices independently created "the same" thing with different ids:

| Entity | Natural key (existing unique constraint) |
|---|---|
| Category | `name` |
| Account | `name` |
| Budget | `(category_id, year, month)` |
| Recurring series | none in the DB today — falls back to `(transactionType, groupKey, normalizedDescription)`, the same grouping `DetectRecurringSeriesServiceImpl` already uses |
| Savings goal | none — id only |
| Bill / Income | none — id only (this is the whole point of Principle II: a transaction's identity *is* its id, nothing about its content is guaranteed unique) |

**Rationale**: "Common lineage" (spec Assumptions) guarantees ids match for anything that existed *before* the two devices last synced. It does not stop a user from independently creating, say, a "Groceries" budget for July on both devices before ever syncing. A naive id-only merge would then try to insert a second row with the same `(category_id, year, month)` and hit the existing unique constraint — a hard failure, not a graceful merge. Falling back to the natural key resolves it the same way a single device already would (there can only be one), then applies FR-005's last-modified-wins between the two candidates. This isn't new risk-taking: it only extends a uniqueness rule the schema already enforces on one device out across two.

**Alternatives considered**: Id-only matching, treating any natural-key collision as a hard import error requiring manual resolution (rejected — defeats the purpose of an unattended full-state merge for an entirely foreseeable scenario, not an edge case); fuzzy/heuristic matching by similarity (rejected — unbounded complexity, not needed when a real unique constraint already exists).

## R3: The correction tie-breaker needs a timestamp that doesn't exist yet

**Decision**: Add a `recordedAt` timestamp to `BillEntity`/`IncomeEntity` — set once, at the moment a row (original, correction-replacement, or reversal) is first written, and never changed afterward. When merge finds two sibling rows (same non-null `correctsTransactionId`, both non-reversal) that only sync ever produces, the one with the later `recordedAt` becomes the bill's current value; the other is retained (Principle I — nothing is ever deleted) but excluded from `GetBillService.getAll()`'s visible/current set, the same way an already-superseded row is today.

**Rationale**: The existing `time` field on `BillDto`/`IncomeDto` is the transaction's *occurrence* time (when the purchase happened) — user-editable via correction, and two independent corrections could easily carry similar or even identical occurrence times. It cannot serve as a "which correction happened more recently" signal. No field recording *when a row was actually written* exists today (`CorrectBillServiceImpl`/`BillCorrections` never persisted one because a single device never needed it — `assertNotSuperseded` already prevented more than one child per original from ever existing). Sync is what first makes two siblings possible, so this is the first feature that needs the field.

**Alternatives considered**: Reusing `time` as the tie-breaker (rejected — wrong semantics, see above); requiring the user to manually resolve every correction conflict (rejected — the spec's own FR-006 already calls for a deterministic, automatic resolution); a vector clock or per-device sequence number (rejected — solves a more general problem than one field needs to; a single "when was this written" timestamp is sufficient because the rule is simply "most recent wins").

## R4: Mutable-entity "last-modified" needs a genuinely new column, set on every mutation

**Decision**: Add `updatedAt` to `CategoryEntity`, `BudgetEntity`, `AccountEntity`, `RecurringSeriesEntity`, and `SavingsGoalEntity` — set on creation and bumped by every existing update path (`UpdateCategoryPostgresAdapter`, `SetBudgetPostgresAdapter`, `UpdateAccountPostgresAdapter` (or equivalent), `UpdateRecurringSeriesStatusPersistencePort`'s adapter, `UpdateSavingsGoalPostgresAdapter`). `Bill` gets its own narrower `necessityTagUpdatedAt`, bumped only by `UpdateBillNecessityTagPostgresAdapter` (feature 018) — a bill has exactly one thing sync ever needs to arbitrate about it, and conflating it with a general "bill updatedAt" would incorrectly suggest the bill's financial facts are versioned the same way, when they are not (those go through correction, not last-write-wins).

**Rationale**: Confirmed by direct inspection — none of these five entities carry any modification timestamp today (`RecurringSeriesEntity`/`SavingsGoalEntity` have `createdAt` only; `CategoryEntity`/`AccountEntity`/`BudgetEntity` have neither). FR-004 requires one. This is the feature's single largest, but entirely mechanical, piece of foundational work: five entities, five columns, and one line added to each entity's existing mutation adapter.

## R5: Export/import as dedicated endpoints, not client-orchestrated per-row calls (unlike feature 017)

**Decision**: Two new server-side capabilities — `GET /sync/export` (assembles the full snapshot in one response) and import as `POST /sync/import/preview` + `POST /sync/import/apply` (same request/response shape; preview computes and returns the merge plan's summary without writing anything, apply computes the same plan and persists it) — both backed by genuinely new Domain services, not the frontend fetching every entity type individually and replaying existing create endpoints the way `transactionImport.ts` (017) does.

**Rationale**: 017's client-side approach worked because its only decision was "does this row look like a duplicate of something already fetched" — one comparison, no cross-entity dependency, no natural-key fallback, no conflict tie-breaking, and every write went through the ordinary `createBill`/`createIncome` service (brand-new data, always a fresh server-generated id). This feature's merge has real cross-cutting logic — natural-key fallback (R2), the correction tie-breaker (R3), a fixed entity-processing order (accounts and categories before anything that references them), and identity-preserving writes that go through the persistence port directly rather than the validating `Add*Service`/`Update*Service` layer (re-running origin-device validation, like a duplicate-name check, against data that already passed it on the origin device risks rejecting a legitimate incoming entity). That's Domain business logic squarely inside Constitution Principle VI's "financial calculation / business-rule logic" — it belongs in one transactional, unit-tested Domain service, not spread across dozens of individual frontend-orchestrated HTTP calls with partial-failure risk.

**Two endpoints, one shared computation**: `computeMergePlan(SyncSnapshotDto)` is a pure, read-only function (loads current state, decides what's new/updated/conflicted, never writes) — `preview` calls it and returns the summary; `apply` calls it and then persists the plan. This keeps the "what would happen" and "make it happen" paths guaranteed consistent by construction, rather than two independently-maintained implementations that could drift.

**Alternatives considered**: A single endpoint with a `dryRun` flag, applying inside a transaction that's rolled back when `dryRun=true` (rejected — works, but makes the "preview" path do real write I/O and rely on transaction rollback for correctness, which is harder to unit-test than a pure planning function); client-orchestrated merge mirroring 017 (rejected per Rationale above).

## R6: Entity processing order

**Decision**: Merge applies entity types in dependency order: accounts → categories → budgets & recurring series (both depend only on accounts/categories) → bills & incomes (depend on accounts/categories, and bills carry the necessity tag) → savings goals (depend on accounts).

**Rationale**: A bill imported before its account or category exists locally would reference a dangling id. Since imports are all-or-nothing per the spec's Assumptions, processing in dependency order avoids ever needing a two-pass "insert then backfill references" approach.

## R7: Snapshot format and amount encoding

**Decision**: The export is JSON (not a database dump), with every monetary amount as a decimal **string** in the wire format (`"45.50"`, not a JSON number) — the exact convention `correctBillRequest` already established for the same Principle IV reason. The snapshot carries a `schemaVersion` field; import rejects (FR-010) a file whose `schemaVersion` it doesn't recognize, without attempting a partial import.

**Rationale**: JSON is human-inspectable (the user can open the file and see what it contains — no encryption per FR-012 means this is a real, intended property, not just an implementation shortcut) and needs no new dependency; the app already has a JSON (Jackson) stack throughout. `schemaVersion` is the cheapest possible forward-compatibility guard and satisfies FR-010's "incompatible version" rejection without needing real schema migration logic in v1 — an unrecognized version is a hard reject, not a best-effort partial read.

**Alternatives considered**: A raw SQL/database dump (rejected — ties the format to the current schema and Postgres specifically, not portable to a future mobile app's local store); a binary format (rejected — no compactness need at personal-finance scale, and loses the human-inspectable property that matters given FR-012's decision).
