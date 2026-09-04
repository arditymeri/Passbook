# Upgrading Passbook

Read [CHANGELOG.md](../CHANGELOG.md) for the version you are moving to first — it says whether
that release needs anything from you beyond these steps.

No step below asks you to run a database command by hand. Schema changes are applied
automatically on startup, in order, exactly once.

---

## 1. Back up. Always, first, no exceptions.

```bash
docker compose exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > "passbook-$(date +%F).dump"
```

Full details, including how to restore, are in [docs/BACKUP.md](BACKUP.md). If you skip this step
and something goes wrong, there is nothing anyone can do for you — this instance is the only copy
of your financial history.

## 2. Stop the instance

```bash
docker compose down
```

Upgrades are stop-then-start. Passbook is a single instance against a single database; there is
no rolling or zero-downtime upgrade, and you do not need one.

## 3. Get the new version

```bash
git pull
```

## 4. Check the changelog for required actions

Open [CHANGELOG.md](../CHANGELOG.md) and read every entry between your version and the new one —
not only the newest. If any of them has an **Operator action required** section, do it now.

Don't know what version you are on? Start the old instance and read the footer in the web UI, or:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/system/version
```

## 5. Start

```bash
docker compose up --build
```

That is the upgrade. Schema changes are applied during startup.

## 6. Confirm

Log in and check that data you remember is there. Then look at the schema history:

```bash
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Every row should show `success = t`. On an instance that existed before migrations were
introduced, the first row is `type = BASELINE` — that is your existing database being adopted,
and it is what you want to see. On an instance created fresh, the same row is `type = SQL`.

---

## Upgrading across several versions at once

Fine. Skipping intermediate releases is supported: every schema change between where you are and
where you are going is applied, in order, exactly once. Still read the changelog entry for each
of the versions you skipped — an **Operator action required** section in one of them applies to
you even though you never ran that version.

---

## When it goes wrong

**The app refuses to start, naming a missing secret.**
Expected, and the correct behaviour. Copy `.env.example` to `.env` and fill it in. There is no
built-in default on purpose: a shipped fallback would mean every install shares a credential.

**The app refuses to start with a schema validation error.**
Your database has something the running code does not expect. This is the app protecting you, not
malfunctioning — it will not alter your schema to make itself run. Check that you are on the
version you think you are, restore your backup if you need to get back to a known state, and open
an issue with the message: a validation failure on a stock upgrade is a bug in Passbook.

**A migration failed partway.**
PostgreSQL applies each migration in a transaction, so it rolled back and your database is in its
previous consistent state. The app will not serve requests against a half-changed database. Fix
the cause and start again; you do not need to repair anything by hand.

**Two instances started at once.**
Only one applies schema changes; the other waits for the lock and then finds the work already
done. Nothing is applied twice.

**You restored a backup from a newer version into older code.**
Startup refuses, because the restored schema history contains a migration this code has never
heard of. Run the version the backup came from, or newer. Do not delete rows from
`flyway_schema_history` to get past it — that would let old code write against a newer schema,
which is the failure the refusal exists to prevent.

---

## Downgrading

Not supported. There are no down-migrations. To go back to an earlier version, restore a backup
taken while you were on it — which is the other reason step 1 is step 1.
