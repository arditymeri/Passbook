# Phase 1 Quickstart: Release Hardening

**Feature**: 021-release-hardening | **Date**: 2026-09-03

Validation scenarios for this feature. Each maps to a user story and its acceptance scenarios.
Because this feature's subject is *operating* an instance, most scenarios are operator procedures
rather than code paths — several of them become the literal content of `README.md`,
`docs/UPGRADING.md`, and `docs/BACKUP.md`.

> **Environment note, stated up front**: the development sandbox this feature was planned in has
> **no Docker daemon**, so scenarios 1, 2, 4 and 5 below cannot be executed locally. They are
> executed in CI, where the `integration-tests` module boots a real PostgreSQL container. Where a
> scenario is CI-verified rather than locally verified, it says so. Nothing here is claimed as
> "tested" on the basis of having been written down.

---

## Scenario 1 — Upgrade an existing instance (US1, FR-003)

**Verifies**: acceptance scenario 1 — existing rows survive, nothing is dropped or recreated.

**Setup**: an instance running the *pre-migration* version, whose schema was built by
`ddl-auto=update`, containing real data.

```bash
# 1. Back up first. This is step one of the documented upgrade, not a suggestion.
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > passbook-preupgrade.dump

# 2. Record what you have, so "nothing was lost" is checkable rather than hopeful.
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT 'account' t, count(*) FROM account
   UNION ALL SELECT 'bill', count(*) FROM bill
   UNION ALL SELECT 'income', count(*) FROM income
   UNION ALL SELECT 'category', count(*) FROM category
   UNION ALL SELECT 'budget', count(*) FROM budget
   UNION ALL SELECT 'recurring_series', count(*) FROM recurring_series
   UNION ALL SELECT 'savings_goal', count(*) FROM savings_goal;"

# 3. Stop, take the new version, set the secrets it now requires, start.
docker compose down
git pull && cp .env.example .env && "$EDITOR" .env
docker compose up --build
```

**Expected**:

- The app starts.
- The counts from step 2 are unchanged.
- `SELECT version, type FROM flyway_schema_history;` shows exactly one row: version `1`, type
  **`BASELINE`** — proving `V1` was adopted, not executed (data-model §1).

**Status**: not locally executable (no Docker). The equivalent guarantee is CI-verified: with
`ddl-auto=validate`, the ~109 integration tests fail at context startup if the baseline disagrees
with the mappings in any way Hibernate checks.

---

## Scenario 2 — A brand-new empty database (US1, FR-004)

**Verifies**: acceptance scenario 2 — a fresh install ends up indistinguishable from an upgraded one.

```bash
docker compose down -v          # -v discards the volume: genuinely empty
cp .env.example .env && "$EDITOR" .env
docker compose up --build
```

**Expected**:

- Startup logs show Flyway applying `V1__baseline_schema.sql`.
- `flyway_schema_history` has one row, version `1`, type **`SQL`** — the mirror image of scenario 1.
- The resulting schema is identical to the upgraded instance's.

**Status**: CI-verified. This is precisely the path every integration test context takes — a fresh
TestContainer database, migrated from empty by Flyway.

---

## Scenario 3 — Drift fails loudly (US1, FR-005, SC-004)

**Verifies**: acceptance scenario 3 — a mismatched database refuses to start and is not "fixed".

```bash
# Introduce a mismatch by hand against a scratch database.
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "ALTER TABLE bill DROP COLUMN necessity_tag;"
docker compose restart finance-app
```

**Expected**: startup fails with a Hibernate `SchemaManagementException` naming the missing column,
and `bill` is **not** altered back.

**Honest limit** (research R4): Hibernate `validate` checks tables, columns and types. It does
**not** check indexes, unique constraints, foreign keys, defaults, or constraint names. An extra
leftover column will *not* fail startup. FR-005 covers the mismatches that corrupt reads and
writes; it does not claim full structural equivalence.

**Status**: not locally executable.

---

## Scenario 4 — Backup and restore (US3, FR-011, FR-012, SC-003)

**Verifies**: acceptance scenarios 1 and 2 — a single artifact, and a restore that actually works.

```bash
# Backup — one artifact, compressed, custom format.
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" \
  > "passbook-$(date +%F).dump"

# Restore — into an empty database.
docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --clean --if-exists < "passbook-$(date +%F).dump"
```

**Expected**: the app starts against the restored database and shows exactly the data captured at
backup time.

**Status**: **CI-verified by an automated test**, which is how FR-012's "MUST have been executed
end-to-end, not merely described" is satisfied given no local Docker. The integration test seeds
data through the API, runs these commands inside the container via Testcontainers'
`execInContainer`, destroys the schema, restores, and asserts the data is back.

> **Design constraint on that test**: it must run *these* commands — the ones in the documentation.
> A test that dumps and restores by some other route verifies something no operator will ever do.

---

## Scenario 5 — A newer backup into older code (US3 scenario 3)

**Verifies**: the version mismatch surfaces as a refusal.

**Setup**: restore a dump taken from an instance one release newer, then start the older code.

**Expected**: Flyway refuses to start — the restored `flyway_schema_history` contains an applied
migration the running code cannot resolve. Older code never writes against a newer schema.

**Status**: not locally executable; the mechanism is Flyway's default `validate-on-migrate`, which
this feature sets explicitly so the behaviour is visible in configuration rather than inherited.

---

## Scenario 6 — No credentials in the repository (US2, FR-007, SC-005)

**Verifies**: acceptance scenario 1 — no usable credential in any tracked file.

```bash
# Every password in the shipped config surface must be an environment placeholder, never a
# literal. Empty output is the pass condition. The old value is deliberately NOT written out
# here: a verification command must not itself put a credential into a tracked file.
git grep -nIE '(password|PASSWORD)[[:space:]]*[:=][[:space:]]*[^$?[:space:]]' \
  -- '*.properties' '*.yaml' '*.yml' 'Dockerfile' ':!integration-tests/**' ':!specs/**'

# integration-tests/ is excluded knowingly: its values are throwaway Testcontainer fixtures on a
# random port, not credentials to anything reachable. They are also no longer the same string as
# the historically published password.

# What an operator actually does:
cp .env.example .env
"$EDITOR" .env                  # set POSTGRES_PASSWORD, JWT_SECRET
docker compose up --build       # still one command
```

**Expected**: the grep finds nothing; `.env` is gitignored (already covered by the existing
`### Secrets & local environment ###` block); the stack starts.

**Locally executed — result**: the check passes. The historically published password appears in no
tracked file, including this feature's own planning documents and `.env.example`: a document that
tells you to rotate a credential should not restate it. The startup half needs Docker.

---

## Scenario 7 — Missing secrets fail fast (US2, FR-009)

**Verifies**: acceptance scenario 3 — startup names what is missing rather than falling back.

```bash
unset POSTGRES_PASSWORD; rm -f .env
docker compose up            # Compose itself refuses: ${POSTGRES_PASSWORD:?...}

./mvnw -pl Launcher spring-boot:run   # Spring refuses, naming POSTGRES_PASSWORD
```

**Expected**: two distinct, actionable failures, and **no built-in default anywhere**. With
`JWT_SECRET` unset, `JwtTokenService` throws an `IllegalStateException` naming the variable —
replacing today's random-key fallback, which silently logs every session out on restart.

**Locally executed — result**: verified by running it. With neither secret set, startup fails with
*"Passbook cannot start: required secret(s) not configured — JWT_SECRET (...), POSTGRES_PASSWORD
(...)"* naming both; with only `JWT_SECRET` set it names `POSTGRES_PASSWORD` alone.

**What running it revealed**: a `${VAR}` placeholder with no default is *not* sufficient on its
own. `@Value` throws on an unresolvable placeholder, but the `@ConfigurationProperties` binder
that binds `spring.datasource.*` silently leaves it as literal text — so a missing
`POSTGRES_PASSWORD` originally reached a TCP connection attempt instead of an actionable error.
`RequiredSecretsEnvironmentPostProcessor` closes that, running before any bean so the failure is
the first thing the operator sees.

---

## Scenario 8 — Know your version (US4, FR-013, SC-006)

**Verifies**: acceptance scenarios 1 and 2 — version readable from the running instance, and what
changed is findable.

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/system/version
# {"version":"0.1.0","buildTime":"2026-09-03T10:15:30Z"}
```

Or simply: read the footer in the web UI, which renders the same value.

Then open `CHANGELOG.md` for what changed, and `docs/UPGRADING.md` for how to move between
versions — where step one is taking a backup, and no step is a hand-written database command
(FR-015).

**Expected**: the POM version, the newest `CHANGELOG.md` heading and this response are the same
string (data-model §2).

---

## Developer scenario — regenerating DDL for a future migration

Not an operator procedure, but the recipe that produced `V1__baseline_schema.sql` and the one a
contributor needs when they add or change an entity. Recorded because it is non-obvious:
`org.hibernate.tool.hbm2ddl.SchemaExport` **does not exist in Hibernate 6.6**.

Write a temporary JUnit test in `Infrastructure/src/test/java/`:

```java
Map<String, Object> settings = new HashMap<>();
settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");   // no database needed
settings.put("hibernate.physical_naming_strategy",
        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
settings.put("hibernate.implicit_naming_strategy",
        "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
settings.put("jakarta.persistence.schema-generation.scripts.create-target", "/tmp/schema.sql");
settings.put("hibernate.hbm2ddl.delimiter", ";");

StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
settings.forEach(builder::applySetting);
StandardServiceRegistry registry = builder.build();

Metadata metadata = new MetadataSources(registry)
        .addAnnotatedClass(AccountEntity.class)  // …all entities…
        .buildMetadata();

SchemaManagementToolCoordinator.process(
        metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);
```

Run it, take the DDL, **diff it against the existing migrations**, and hand-write only the delta
as the next `V<n>__*.sql`. Then delete the temporary test — it is a tool, not a test. Never edit an
already-applied migration: Flyway checksums them (data-model §1).
