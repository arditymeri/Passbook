# Changelog

All notable changes to Passbook are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[semantic versioning](https://semver.org/).

Versions before 0.1.0 were not released — the project ran as `0.0.1-SNAPSHOT` with no version an
operator could identify. The 0.1.0 entry therefore covers everything up to the first release that
is safe to hand to someone else, not just the last change.

## [Unreleased]

### Added

- **Server-side statement import with idempotent ingestion.** Upload a CSV bank statement, review
  what it will do, and confirm. Re-importing the same statement — or a later one that overlaps it —
  records nothing twice. Two genuinely identical rows on the same day are both kept: only a real
  repeat is a repeat.
- Transactions that arrive through import now carry a stable identity, which is what makes
  re-ingestion a no-op. Uniqueness is enforced by the database, so two imports running at once
  cannot both record the same transaction.
- **Confirmed recurring series now post their own transactions.** Once you confirm a series, the app
  records each occurrence as it comes due — daily, and on demand via `POST
  /recurring-series/post-due`. It catches up whatever was missed while the app was down, without
  posting anything twice: an occurrence already recorded is refused by the database rather than by a
  check that could be wrong.
- **An imported transaction supersedes the prediction it matches.** When a statement brings in the
  bank's own version of something the app predicted — same account, close enough in date and amount
  — the prediction is reversed by a compensating entry, so the charge lands in your balance once.
  Nothing is deleted or edited: the prediction, the reversal and the bank's row all stay visible.
- **Auto-posted transactions are marked as such** in the transaction list, so a row the app wrote is
  never mistaken for one you entered or your bank reported.
- **A series can be stopped** (`POST /recurring-series/{id}/stop`, or Stop in the recurring series
  dialog). Stopping ends the posting and leaves everything already posted in place — deliberately
  different from dismissing a proposal, which says the detection was wrong to begin with.

- **A deployable stack.** `docker-compose.deploy.yaml` runs Passbook as three containers —
  Postgres, the backend, and Caddy serving the built frontend and proxying `/api` so both answer
  on a single origin. Kafka, Control Center, Kafdrop and pgAdmin are all absent, and nothing but
  the web server binds a port; the development `docker-compose.yaml` is unchanged. The backend
  image is now multi-stage, so what ships is a JRE and one jar rather than a JDK and Maven's
  whole repository.
- **A devcontainer**, so the project runs on GitHub Codespaces with Docker available and a `.env`
  of generated secrets written on first create. [docs/DEPLOYING.md](docs/DEPLOYING.md) walks
  through reaching an instance over the internet, and is honest about what a test instance is
  not: no backup schedule, and the app is still the only copy of what it holds.

- **Failed logins are now refused after repeated failures.** Five consecutive failures from one
  caller stops that caller for fifteen minutes; twenty across all callers stops everyone. Both
  expire on their own — there is no endpoint to lift a refusal and nothing to edit in the database,
  deliberately, because this app has one account and no password-reset email, and a lockout only a
  developer could clear is one you could not. Thresholds are configurable.
- **A new password must be at least 12 characters**, enforced when it is set — at first-run setup
  and when changing it — and never when it is used.
- **A deployed instance no longer serves its API browser or machine-readable description.**
  Development still does.

### Fixed

- **Importing a statement containing income could break the app afterwards.** The ingest path wrote
  income rows without a value for one column that cannot be null, so every later read of the income
  table failed — account balances, budget status, savings goals and the next import all stopped
  working, days after the import that caused it and with nothing to connect them to it. The insert
  is fixed, the column now refuses null at the database, and rows already written are repaired on
  upgrade. Introduced with server-side import in this same unreleased cycle, so no released version
  is affected.

### Changed

- **The import dialog now asks the server, not the browser.** Feature 017's client-side CSV parsing
  and duplicate detection are gone. That detection could only compare against transactions the
  browser happened to have loaded; it could not protect a second device, or anything arriving by any
  other route. Quoted fields containing commas and newlines now parse correctly too, which the
  line-oriented browser parser could not represent at all.

### ⚠ Operator note

**Your existing password still works, but no longer meets the rule.** The 12-character minimum
applies when a password is set, never when one is used, so nothing about your instance stops
working on upgrade. The next time you change it, the new one will have to meet the minimum. If your
current password is short, that is worth doing now rather than later — particularly if the instance
is reachable from the internet.

**If you lock yourself out, wait.** There is no unlock endpoint and nothing to reset in the
database — by design. A refusal ends fifteen minutes after it started, whether or not anyone keeps
trying in the meantime.

**Confirming a recurring series now has a consequence it did not have before.** Under earlier
versions, confirming a series only improved the dashboard's predictions; nothing was ever written.
From this version a confirmed series writes transactions. If you have series confirmed under an
older version, posting will begin for them on the first run after upgrading. Nothing dated before
the day you confirmed a series is ever posted, so no history is fabricated — but a series confirmed
long ago whose occurrences were never recorded will catch up every period since, in one run. Review
your confirmed series **before** upgrading and stop any that have ended.

Note also what auto-posting cannot know: **a series posting to an account whose statements you never
import will keep posting until you stop it.** Nothing arrives to confirm or supersede those
transactions, so that account's balance drifts toward what the app expects rather than what your
bank did.

Transactions you imported with the **old** client-side dialog carry no identity, because there was
nowhere to store one. Re-importing a statement covering that period will therefore offer those rows
as new rather than recognising them. This is deliberate — the app will not claim to recognise
history it never ingested — but it means the first import after upgrading may need rows unticked.
Hand-entered transactions are unaffected and behave exactly as before.

## [0.1.0] — 2026-09-03

The first release intended for someone other than its author to run. Earlier states of this
repository were development snapshots: the schema was inferred at every startup, the database
password shipped in the source, there was no backup procedure, and no way to tell versions apart.
All four are closed here.

### ⚠ Operator action required

**If you are already running an instance, read this before upgrading.**

1. **Create a `.env` file.** The app no longer ships any credential and will refuse to start
   without `POSTGRES_PASSWORD` and `JWT_SECRET`. `cp .env.example .env` and fill it in.
2. **Rotate your database password.** The password this project used to ship is in its published
   git history. Removing it from the source does not remove it from history, and rewriting the
   history of a public repository is not something you can rely on. Set a new password — in
   `.env` *and* on the database itself — rather than assuming removal was enough.
3. **Take a backup first.** See [docs/UPGRADING.md](docs/UPGRADING.md), where this is step one,
   and [docs/BACKUP.md](docs/BACKUP.md) for how.
4. **Setting `JWT_SECRET` for the first time logs out existing sessions.** Expected: previously
   the signing key was regenerated on every restart, so sessions did not survive restarts anyway.

Your data is preserved. The first startup on this version adopts your existing database as the
migration baseline without dropping, recreating, or emptying any table.

### Added

- **Explicit schema migrations (Flyway).** Every schema change is now a versioned, ordered step
  recorded once applied. An already-running instance adopts the current schema as its baseline;
  a new empty database has it created from the same file.
- **Documented and tested backup/restore.** [docs/BACKUP.md](docs/BACKUP.md). The procedure runs
  against a real PostgreSQL on every CI build, so it is verified rather than merely written down.
- **Documented upgrade path.** [docs/UPGRADING.md](docs/UPGRADING.md).
- **`GET /system/version`.** Reports the running version, also shown in the app footer, so you
  can tell what an instance is without reading source or build files.
- **`.env.example`.** Documents every secret the app needs; contains no real values.
- **Single-user authentication** (0.1.0 also carries this, from before the release existed): one
  admin username and password protecting the whole instance, with logout and password change both
  invalidating existing sessions.
- **Device sync export/import**, spending trends, cash-flow forecast, recurring-cost detection
  and necessity tagging, envelope budgeting with allocation transfers, savings goals, and
  client-side transaction import — all built before this release existed.

### Changed

- **`spring.jpa.hibernate.ddl-auto` is now `validate`, not `update`.** The app refuses to start
  when the database disagrees with what the code expects, instead of silently reshaping it.
  Hibernate's validation checks tables, columns and column types; it does not check indexes,
  unique constraints, foreign keys or defaults, so a stale *extra* column will not stop startup.
- **All secrets come from the environment.** `spring.datasource.password` and the JWT signing key
  have no built-in default, and startup fails naming what is missing.
- **The JWT signing key is required.** It previously fell back to a random per-process key, which
  logged every session out on each restart.
- **Docker Compose reads credentials from `.env`** and refuses to start if they are absent.

### Fixed

- `.env` is excluded from the Docker build context. Without this, an operator's local `.env`
  would have been copied into the image by the `COPY . .` in the Dockerfile.

### Known limitations

Carried forward, and honest about them:

- **No transport encryption.** TLS is yours to terminate, via a reverse proxy.
- **Transactions carry no currency field.** Every amount is implicitly in its account's default
  currency; cross-currency transactions cannot be represented.
- **One integration test class remains disabled** (`BillGetControllerIntegrationTest`).
- **Kafka is present but unused** for transaction ingestion; the pipeline that fills itself is
  still the roadmap, not the reality.

[Unreleased]: https://github.com/arditymeri/Passbook/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/arditymeri/Passbook/releases/tag/v0.1.0
