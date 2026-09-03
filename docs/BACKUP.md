# Backing Up and Restoring Passbook

Your financial history is irreplaceable and this instance is the only copy. Nothing else in this
project will save you if the database is lost — take backups.

Every command below assumes the Docker Compose setup from the README and that your `.env` is
filled in. Replace `$POSTGRES_USER` and `$POSTGRES_DB` with your values if you changed them from
the defaults (`diti` and `myfinance`).

---

## Back up

```bash
docker compose exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > "passbook-$(date +%F).dump"
```

That is the whole procedure. It produces **one file** containing your complete financial history:
every account, category, budget, bill, income, recurring series and savings goal, plus your admin
account and the schema-version history.

`-Fc` is PostgreSQL's custom format — compressed, restorable selectively, and the format
`pg_restore` expects. Do not substitute a plain-SQL dump unless you also change the restore
command to match.

**Where to put it.** Somewhere that is not the machine running Passbook. A dump sitting on the
same disk as the database it came from protects you against exactly one failure mode (your own
mistake) and none of the others.

**When.** Before every upgrade, without exception — this is step one of
[docs/UPGRADING.md](UPGRADING.md) — and on whatever schedule matches how much data you are
willing to re-enter by hand.

---

## Restore

```bash
docker compose exec -T postgres \
  pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists < passbook-2026-09-03.dump
```

`--clean --if-exists` drops the existing objects before recreating them, so this replaces the
current contents of the database rather than merging into them. That is what you want for a
restore and emphatically not what you want by accident — **the data currently in that database is
gone afterwards.**

Then start the app. It will come up against the restored database.

### Verify the restore, don't assume it

Log in and check that a few things you remember are actually there. A backup you have never
restored is a hypothesis, not a backup.

---

## What travels inside the dump, and why it matters

The dump includes `flyway_schema_history` — the record of which schema migrations have been
applied to that particular database. This is deliberate and load-bearing:

- Restoring a backup taken from a **newer** version of Passbook into **older** code leaves an
  applied migration the older code has never heard of. Flyway refuses to start. Old code never
  silently operates on a newer schema.
- Restoring into the same or a newer version works normally, and any migrations you are missing
  are applied on the next startup.

If you hit that refusal, the fix is to run the version the backup came from (or newer), not to
delete rows from `flyway_schema_history`.

---

## The Sync export is not a backup

Passbook has an export on the Sync page (feature 019). It is **data portability between your own
devices, not a backup**, and using it as one will disappoint you:

| | Sync export | `pg_dump` backup |
|---|---|---|
| Financial records | yes | yes |
| Admin account / instance config | **no** | yes |
| Schema version history | **no** | yes |
| On import | **merges** into existing data | replaces |

Merging is the right behaviour for moving between your phone and your laptop. It is the wrong
behaviour for "put it back exactly as it was", which is what a restore has to mean.

---

## Automating it

There is deliberately no built-in scheduler. A cron entry is enough and keeps the retention
policy yours:

```cron
0 3 * * * cd /path/to/passbook && docker compose exec -T postgres \
  pg_dump -U diti -Fc myfinance > /backups/passbook-$(date +\%F).dump
```

Note the escaped `\%` — cron treats a bare `%` as a newline.

---

## Is this tested?

Yes, and not only on paper. `BackupRestoreIntegrationTest` in the `integration-tests` module runs
these `pg_dump` and `pg_restore` invocations against a real PostgreSQL container on every CI
build: it writes a record through the API, dumps, restores into an empty database, and asserts
that the record, the full set of tables, and `flyway_schema_history` all came back. If the
commands on this page stop working, that test fails.

Three honest notes about what it covers:

- It restores into an **empty** database rather than over a populated one, because that is the
  case the acceptance criteria describe and because destroying the shared test database would
  take the rest of the suite with it. `--clean --if-exists` behaves the same either way — it is a
  no-op against an empty database and drops-then-recreates against a populated one.
- It writes the dump with `-f <path>` instead of a shell redirect (`>`). Same command, same
  output; the redirect just needs a shell.
- It asserts the restored data through `psql`. It does not additionally boot the application
  against the restored database, so "the app starts against it" remains something you should
  confirm yourself after a real restore.
