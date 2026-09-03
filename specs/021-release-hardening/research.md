# Phase 0 Research: Release Hardening

**Feature**: 021-release-hardening | **Date**: 2026-09-03

All decisions below were reached against the actual repository state, not from general practice.
Where a decision could not be verified in this environment, that is said plainly rather than
implied away.

---

## R1: Flyway over Liquibase

**Decision**: Flyway, with plain-SQL versioned migrations under `db/migration/`.

**Rationale**: The constitution permits either. The thing this feature protects is *someone
else's irreplaceable financial history*, so the reviewable unit matters: a plain `.sql` file is
literally the statements that will run against that data. Liquibase's changelog abstraction adds
a translation step between what is reviewed and what executes, which buys database portability —
a non-goal here, since PostgreSQL is the only supported engine (spec Assumptions).

**Alternatives considered**:

- *Liquibase*: rejected — its advantage is engine portability, which this project explicitly does
  not want, and its changesets are harder to eyeball for "will this drop a column".
- *Hand-rolled migration runner*: rejected outright. Applying schema steps exactly once, under a
  lock, with a recorded history is precisely the wheel Flyway already is.

**Consequence for dependencies**: Flyway 10 (Spring Boot 3.4.0 manages **10.20.1**) split database
support out of the core artifact. `org.flywaydb:flyway-core` **alone fails at startup** against
PostgreSQL with *"No database found to handle jdbc:postgresql://…"*. Both
`flyway-core` **and** `flyway-database-postgresql` are required. This is the single most likely
way to get a red first build on this feature.

---

## R2: Producing a baseline that matches an already-running instance

This was the acknowledged risk when the spec was written: the baseline must reproduce *exactly*
what `ddl-auto=update` has already created in an operator's live database, and there is no live
PostgreSQL in this development sandbox (no Docker daemon).

**Decision**: Generate the baseline DDL **offline from the JPA mappings** using Hibernate's own
schema-generation, then treat the CI integration suite as the verifier.

**This was validated, not assumed.** A throwaway probe was run in this repository and produced
the complete DDL for all nine entities with no database present. The probe has since been
deleted; the recipe it proved is recorded here and in `quickstart.md` so the baseline can be
regenerated and future migrations can be derived the same way.

The recipe (Hibernate 6.6.2, as managed by Spring Boot 3.4.0):

| Setting | Value | Why |
|---|---|---|
| `hibernate.dialect` | `org.hibernate.dialect.PostgreSQLDialect` | Match production; the app pins this explicitly today. |
| `hibernate.boot.allow_jdbc_metadata_access` | `false` | This is what makes it work with **no database at all**. |
| `hibernate.physical_naming_strategy` | `org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy` | Spring Boot 3.x's default. Getting this wrong yields `tokenVersion` instead of `token_version`. |
| `hibernate.implicit_naming_strategy` | `org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy` | Spring Boot 3.x's default. |
| `jakarta.persistence.schema-generation.scripts.action` | `create` | Script output, no execution. |
| `jakarta.persistence.schema-generation.scripts.create-target` | *(output path)* | |
| `hibernate.hbm2ddl.delimiter` | `;` | Statement terminator Flyway needs. |

Entry point: `SchemaManagementToolCoordinator.process(metadata, registry, settings,
DelayedDropRegistryNotAvailableImpl.INSTANCE)` with a `MetadataSources` listing the nine entity
classes. **Note for whoever reruns this**: `org.hibernate.tool.hbm2ddl.SchemaExport` — the class
every tutorial reaches for — **does not exist in Hibernate 6.6**. The coordinator above is the
supported replacement.

**Generated schema** (validated output, 10 tables):

| Table | Notes |
|---|---|
| `account` | `balance numeric(38,2)`, `name` unique |
| `account_currency` | `@ElementCollection` side table; the **only** real foreign key in the schema (`account_id → account`) |
| `admin_account` | from feature 020; `token_version integer not null` |
| `allocation_transfer` | `amount numeric(38,2)` |
| `bill` | `reversal boolean default false not null`, `necessity_tag_updated_at` |
| `budget` | composite `unique (category_id, year, month)` |
| `category` | `name` unique |
| `income` | 15 columns, `amount numeric(38,2) not null` |
| `recurring_series` | |
| `savings_goal` | `target_amount numeric(38,2)` |

Two properties of this schema make baselining unusually safe here:

1. **Every money column is `numeric(38,2)`** — no floating point anywhere, so Principle IV is
   carried into the baseline verbatim rather than needing to be re-established.
2. **Cross-entity references are plain `varchar(255)` columns, not foreign keys.** `bill.account_id`,
   `income.category_id`, `savings_goal.account_id` and friends are `String` fields on the entities.
   That is not ideal schema design, but it means the baseline has almost no constraint graph to
   reproduce and no ordering hazards.

**Alternatives considered**:

- *`pg_dump --schema-only` from a live development database*: the obvious approach, and impossible
  here. It is also weaker than it looks: it captures whatever one particular developer's database
  drifted into over 20 features of `ddl-auto=update`, not what the current mappings say. The
  offline generation is derived from the mappings, which is what `ddl-auto=validate` will check
  against afterwards.
- *Hand-writing the DDL from reading the entities*: rejected. Nine entities, ~80 columns, and a
  single wrong type or a missed `not null` is a failed startup on someone else's instance.

**How this gets verified despite no local database**: the `integration-tests` module boots a real
PostgreSQL TestContainer per context and runs ~109 tests, and it runs in CI. Today those tests
get their schema from `ddl-auto=update`. After this feature they get it from the Flyway baseline,
under `ddl-auto=validate`. If the baseline is wrong in any column name, type, or nullability that
Hibernate checks, **every one of those tests fails at context startup**. The verification is real;
it just happens in CI rather than on this machine. This is stated as a known limitation rather
than papered over.

---

## R3: Adopting an existing database without a destructive rebuild

**Decision**: `spring.flyway.baseline-on-migrate=true` with `spring.flyway.baseline-version=1`,
and the baseline itself as `V1__baseline_schema.sql`.

**How the two required behaviours both fall out of one setting** (FR-003 and FR-004):

- *Existing instance* — the schema is non-empty and has no `flyway_schema_history` table. Flyway
  stamps a baseline row at version 1 and applies only migrations **above** it. `V1` is therefore
  recorded as applied but **never executed**. Nothing is dropped, recreated, or emptied.
- *Brand-new empty database* — `baseline-on-migrate` does not engage (there is nothing to adopt),
  so Flyway runs `V1` normally and builds the schema from scratch.

Both paths end at the same schema, which is exactly FR-004.

**Alternatives considered**:

- *`flyway.baselineVersion=0` with `V1` always running*: would attempt to `CREATE TABLE` over an
  existing operator's tables. Rejected.
- *A conditional `CREATE TABLE IF NOT EXISTS` baseline*: would "work" on both paths but silently
  accepts a partially-matching schema, which is the inference behaviour this feature exists to
  remove. Rejected.

---

## R4: Failing loudly on drift

**Decision**: `spring.jpa.hibernate.ddl-auto=validate`, plus Flyway's default
`validate-on-migrate=true` stated explicitly in configuration so the behaviour is visible rather
than inherited.

Two independent checks result, covering FR-005 and US3 scenario 3:

- **Flyway** refuses to start when the recorded history does not match the migrations on the
  classpath — including the "backup restored from a newer version into older code" case, where
  the history contains a version the running code has never heard of.
- **Hibernate `validate`** refuses to start when a table or column the mappings expect is missing
  or has the wrong type, and — critically — **makes no attempt to alter anything**.

**Known limits of `validate`, recorded honestly**: Hibernate's validation checks tables, columns
and column types. It does **not** check indexes, unique constraints, foreign keys, defaults, or
constraint names. So an operator whose database carries a stale *extra* column, or differently
named constraints, will still start. FR-005 and SC-004 are satisfied for the mismatches that can
actually corrupt reads and writes; claiming full structural equivalence would be false.

A useful consequence: because constraint names are not validated, the auto-generated names in the
baseline (`FK1tgr7gw8nsslr01c5sn10cl16`, unnamed inline `unique`) do not need to match what an
operator's database happens to have. They are kept verbatim from the generated script anyway,
since for a fresh database Hibernate emits the identical statement.

---

## R5: Atomicity and concurrent startup

Both edge cases from the spec are answered by PostgreSQL and Flyway rather than by anything this
feature builds — worth recording so nobody re-solves them:

- **Partial failure (FR-006)**: PostgreSQL has transactional DDL. Flyway wraps each migration in
  a transaction, so a failed migration rolls back to the prior consistent state. The failure
  propagates out of `FlywayMigrationInitializer`, the Spring context fails to start, and the app
  serves nothing.
- **Two instances starting at once**: Flyway takes a database-level lock for the duration of
  `migrate`. The second instance waits, then finds the migration already recorded. No double
  application.

Both are free, and both are stated in the operator documentation so an operator does not invent
a stop-the-world ritual that isn't needed.

---

## R6: Where the migrations live

**Decision**: `Infrastructure/src/main/resources/db/migration/`, with the Flyway dependencies
declared in `Infrastructure/pom.xml`.

**Rationale**: The schema is defined by the JPA entities, and those live in Infrastructure. Putting
the migrations anywhere else means a contributor can change an entity without the migration
sitting next to it in the same module. Flyway's default location is `classpath:db/migration`,
which is scanned across the whole assembled classpath, so Launcher picks them up with no extra
configuration. The PostgreSQL driver is already an Infrastructure dependency, so the database
concern is already located there.

**Alternative considered**: `Launcher/src/main/resources/`, alongside `application.properties`.
Rejected — Launcher is an assembly module; it holds *configuration*, and a migration is not
configuration, it is a statement about the entities.

---

## R7: Reporting the running version

**Decision**: A new `GET /system/version` endpoint, specified in OpenAPI first, returning the
Maven project version and build timestamp. The frontend renders it in the footer.

**Rationale**: Principle VII is unambiguous — public REST contracts MUST be defined in OpenAPI
YAML before implementation — and the frontend *will* depend on this response shape. That rules
out the otherwise-obvious answer.

**Alternatives considered**:

- *Spring Boot Actuator `/actuator/info` with `build-info`*: the conventional choice, and rejected
  deliberately. It would make the frontend depend on a framework-owned response shape that
  Principle VII does not cover, and it adds a whole endpoint infrastructure (and its security
  surface) to expose one string. If a later feature needs health checks and metrics, Actuator is
  the right answer *then*.
- *Baking the version into the frontend bundle at build time* (`__APP_VERSION__` from Vite):
  rejected because it reports the version of the *frontend build*, which can diverge from the
  backend an operator is actually running. FR-013 is about the running instance.

**Where the value comes from**: `spring-boot-starter-parent` configures Maven resource filtering
with `@…@` delimiters for `application*.properties`, so `app.version=@project.version@` in
`Launcher/src/main/resources/application.properties` resolves at build time with no new
dependency and no plugin configuration. The controller injects it with `@Value`.

**Hexagonal note**: this controller has no Domain service behind it. That is correct rather than a
shortcut — the build version is not business logic and involves no I/O against an external system,
so there is no port to mediate. Principle VIII's rule is about I/O crossing the Domain boundary.

---

## R8: Secrets from the environment

**Decision**: every secret becomes an unresolvable-by-default placeholder; non-secrets keep
sensible defaults.

| Property | Becomes | Default? |
|---|---|---|
| `spring.datasource.password` | `${POSTGRES_PASSWORD}` | **None** — startup fails naming `POSTGRES_PASSWORD` |
| `spring.datasource.username` | `${POSTGRES_USER:passbook}` | Yes — not a secret |
| `spring.datasource.url` | `${DATABASE_URL:…}` | Yes — not a secret |
| `app.security.jwt-secret` | `${JWT_SECRET:}` + an explicit guard in `JwtTokenService` | Empty placeholder, then a thrown `IllegalStateException` with an actionable message |

**Why the JWT secret is handled differently**: leaving it unresolvable would produce Spring's
generic placeholder error. Keeping the empty default and throwing from `JwtTokenService` produces
a message that says what the secret is *for* and how to set it. It also removes the current
random-key fallback, which silently logs every session out on restart — a built-in default in
exactly the sense FR-009 prohibits.

**Consequence for tests** (must not be discovered in CI): `SecurityConfigTest` currently
constructs `JwtTokenService` with no secret and relies on the random-key fallback. Once that
fallback throws, the slice test must supply a secret via `@WebMvcTest(properties = …)`. Likewise
the integration suite boots the full Launcher configuration, so `POSTGRES_PASSWORD` and
`JWT_SECRET` must resolve there — set as literal test values in
`integration-tests/src/test/resources/application.yaml`. Those are test fixtures, not credentials.

**Docker Compose**: `${POSTGRES_PASSWORD:?…}` substitution, which makes Compose itself refuse to
start with a named error, plus `env_file: .env`. Compose reads `.env` from the project directory
automatically, so `docker compose up` stays a single command once `.env` exists.

**Rotation (FR-010)**: the password this project used to ship is in this repository's published git history. Removing
it from the working tree does not remove it from history, and history rewriting on a public
repository is not a remedy an operator can rely on. The documentation must therefore say *rotate*,
not *removed in this version* — this is a documentation obligation, not a code change.

---

## R9: Backup and restore, and how FR-012 is actually satisfied

**Decision**: PostgreSQL's native `pg_dump -Fc` / `pg_restore`, and **an automated integration
test that executes the exact documented commands**.

FR-012 requires the restore procedure to have *been executed end-to-end successfully, not merely
described*. That cannot happen on this machine (no Docker). Rather than downgrade the requirement,
the verification moves into the place that already runs a real PostgreSQL: the
`integration-tests` module.

The test seeds data through the API, runs the documented `pg_dump` **inside the container** via
Testcontainers' `execInContainer`, destroys the schema, runs the documented `pg_restore`, and
asserts the data is back. The commands in the test and the commands in the documentation must be
the same commands — otherwise the test verifies something the operator will never run. This is
the design constraint on that task.

**Format**: custom format (`-Fc`) rather than plain SQL — a single compressed artifact (FR-011's
"single artifact"), restorable selectively, and the format `pg_restore --clean --if-exists`
expects.

**Explicitly not a backup**: feature 019's sync export. It omits instance-level configuration such
as the admin account, and it merges rather than replaces. The documentation must say so, because
an operator who has seen the Sync page will reasonably assume otherwise.

---

## R10: Version number and release documentation

**Decision**: `0.0.1-SNAPSHOT` → **`0.1.0`** across all six POMs and the Dockerfile's jar path;
`CHANGELOG.md` in Keep-a-Changelog form; upgrade instructions in `docs/UPGRADING.md`.

**Rationale for `0.1.0` rather than `1.0.0`**: the README and SECURITY.md both describe the project
as pre-1.0 and are right to. `0.1.0` is the first version that is *safe to hand to someone else*,
which is a smaller claim than "stable API". Dropping `-SNAPSHOT` matters: a version an operator
reads off a running instance has to identify a fixed thing.

**Rationale for a tracked `CHANGELOG.md`** rather than only GitHub release notes: an operator who
cloned the repository has it locally, and it is reviewable in the same pull request as the change
it describes.

**Stale documentation this feature must fix** — both files currently tell an operator things that
stopped being true:

- `README.md` "Before you self-host" still lists *"No authentication"* (closed by feature 020),
  *"No schema migrations"*, *"No secrets management"*, *"No backup/restore tooling or versioned
  releases"* (all closed here), and *"Integration tests are currently disabled (WIP)"* — which is
  simply wrong: nine of ten integration test classes are active and green in CI.
- `SECURITY.md` "Known and Accepted Limitations" still says *"No authentication or authorisation"*
  and *"Default credentials in `docker-compose.yaml`"*, and "Supported Versions" says there are no
  released versions.

Leaving these would mean the release ships with documentation that understates its own safety in
one place and overstates it in another. Both are in scope: FR-010 and FR-014 are documentation
requirements, and these are the documents they land in.

---

## R11: One constitutional loose end this feature surfaces

The Self-Hosting Obligations list five items. This feature closes three (migrations, secrets,
versioned releases + backup/restore); feature 020 closed the authentication gate. The fifth —
*"Integration tests MUST be enabled and green"* — is **all but met**: nine of ten integration test
classes run in CI, and `BillGetControllerIntegrationTest` is `@Disabled`.

This is not in the spec's four user stories, and it is not being quietly folded into them. It is
raised in the Constitution Check as a gate observation with a small Polish task: look at that one
class and either make it pass or delete it, because an indefinitely disabled test is a claim of
coverage that does not exist. If the user would rather keep this feature to its stated scope, that
task is the one to drop — nothing else depends on it.
