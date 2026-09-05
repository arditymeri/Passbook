# Deploying Passbook

A deployment you can reach over the internet, for testing. Three containers, one exposed port,
and no credentials in the repository.

This is **not** a production guide. It gets you a working instance on a URL; it does not give you
monitoring, log retention, an automated backup schedule, or a second copy of anything.

---

## What this deploys

| Container | What it is |
|---|---|
| `postgres` | The database. Internal network only — no published port. |
| `backend` | The Spring Boot app on `:8080`. Internal network only. |
| `web` | Caddy: serves the built SPA and reverse-proxies `/api` to the backend. **The only exposed port.** |

Defined in [`docker-compose.deploy.yaml`](../docker-compose.deploy.yaml), separate from
`docker-compose.yaml`, which is the development rig and stays as it is.

### Why one origin matters

The SPA calls relative paths (`/api/v1/...`). Serving it from the same origin as the API means no
CORS configuration, no API base URL injected at build time, and no code change to deploy. Split
them across two hostnames and all three of those problems arrive together.

### What was left out, and why

- **Kafka, Control Center, Kafdrop.** Nothing produces to `booking.topic` — the consumer is a
  stub. Control Center alone wants about a gigabyte. Dropping all three is what makes this fit on
  a free tier. The listener is switched off in the backend so it does not spend the life of the
  container dialling a broker that is not there.
- **pgAdmin.** A development convenience. Reaching the database from the internet is not something
  a deployment should offer.
- **Published ports for anything but the web server.** The development compose file publishes
  5432, 5050, 9000 and 9021 to the host. That is fine on a laptop and is *your database on the
  internet* the moment the host has a public IP.

---

## GitHub Codespaces

The shortest path: no new account, no card, no DNS. The repository carries a
[`.devcontainer`](../.devcontainer) so the Codespace has Docker and generates its own secrets.

1. On GitHub: **Code → Codespaces → Create codespace on main.**
2. Wait for it to finish building. `scripts/generate-env.sh` runs automatically and writes a
   `.env` with freshly generated `POSTGRES_PASSWORD` and `JWT_SECRET`. It never overwrites a
   complete one.
3. In the Codespace terminal:

   ```bash
   docker compose -f docker-compose.deploy.yaml up -d --build
   ```

   The first build takes several minutes: Maven downloads the dependency tree and the frontend
   installs its packages. Later builds reuse the cached layers.

4. Watch it come up. The backend runs Flyway migrations and seeds demo data on first boot, so
   allow it a minute:

   ```bash
   docker compose -f docker-compose.deploy.yaml ps      # backend should reach "healthy"
   docker compose -f docker-compose.deploy.yaml logs -f backend
   ```

5. Open the **Ports** panel. Port 8080 is forwarded and starts **Private**, which is what you
   want for the next step: only you can reach it. Click the globe to open it.

6. **Complete first-run setup now, while the port is still private.** The first screen asks you to
   create the instance's admin account.

7. **Only then**, if you want anyone else to reach it: right-click the port →
   **Port Visibility → Public**.

   Steps 6 and 7 are in this order for a reason, and reversing them is not a small mistake.
   `/auth/setup` is open until it succeeds once — it has to be, since there is no account yet to
   authorise it. On a public URL that means **whoever loads the page first becomes the owner of the
   instance**, and of every transaction you later put into it. Publishing the URL and then going to
   make coffee is enough. Setting up first costs nothing and closes the window completely.

To stop paying for idle time, stop the Codespace when you are done; the data volume survives until
the Codespace is deleted.

### Costs

Personal GitHub accounts include a monthly allowance of Codespaces core-hours and storage. The
devcontainer asks for the smallest (2-core) machine on purpose, because a bigger one burns that
allowance faster for no benefit here. Allowances change — check your account's current limits
rather than trusting this paragraph.

---

## Anywhere else with Docker

The same two files work unchanged on any host with Docker — a VPS, a Raspberry Pi, an Oracle Cloud
Always Free instance:

```bash
git clone https://github.com/arditymeri/Passbook.git && cd Passbook
./scripts/generate-env.sh                 # writes .env with generated secrets
docker compose -f docker-compose.deploy.yaml up -d --build
```

That serves plain HTTP on port 8080. On a public host, put something in front that terminates
TLS — Caddy or nginx with a real hostname, or a Cloudflare Tunnel, which also spares you opening
a port at all. Set `WEB_PORT` in `.env` to bind somewhere other than 8080.

**Same ordering applies here.** Reach the instance privately first — over an SSH tunnel, or with
the port bound to `127.0.0.1` — complete first-run setup, and only then expose it. `/auth/setup` is
open until it succeeds once, so on a reachable URL the first person to load it owns the instance
and everything later put into it.

---

## Before you put it on the internet

An instance reachable from the internet is reachable by people who are not you.

- **Set up before you publish.** Covered as an ordered step above rather than repeated here,
  because the mitigation only works if it is done in the right order.
- **Failed logins are throttled.** After five consecutive failures from one caller the instance
  stops answering that caller for fifteen minutes, and twenty failures across all callers stops it
  answering anyone. Both expire on their own — there is no endpoint to lift a refusal and nothing
  to edit in the database, deliberately, because a lockout only a developer could clear is one you
  could not. If you lock yourself out, wait. Move the thresholds with
  `app.security.login-throttle.per-caller-threshold`, `.instance-threshold` and `.window-minutes`.
- **Your password needs at least 12 characters.** Enforced when you set it, never when you use it,
  so an instance created before this rule keeps working with whatever it has.
- **`JWT_SECRET` must be real randomness.** It signs sessions. `openssl rand -base64 32`.
  `scripts/generate-env.sh` does this for you; a hand-written `.env` is where a placeholder creeps
  in.
- **A deployed instance serves no API browser.** `docker-compose.deploy.yaml` turns springdoc off,
  so `/swagger-ui` and `/v3/api-docs` return nothing. Development keeps them.
- **TLS is not the app's job.** Codespaces port forwarding and Cloudflare Tunnel both terminate
  HTTPS for you. A bare VPS on port 8080 does not — that is plain HTTP, session token included.
- **Do not put real financial history on a test instance.** There is no backup schedule here, and
  the app is the only copy of what it holds. If you want one anyway, follow
  [BACKUP.md](BACKUP.md) and actually run a restore.
- **Demo data is on by default**, which is what makes a test instance worth looking at. Put
  `DEMO_DATA=false` in `.env` for an empty one. It only seeds against an empty database, so it
  cannot duplicate anything later.

---

## Operating it

```bash
# Update to the latest code
git pull && docker compose -f docker-compose.deploy.yaml up -d --build

# Logs
docker compose -f docker-compose.deploy.yaml logs -f backend

# Stop, keeping the data
docker compose -f docker-compose.deploy.yaml down

# Stop and DISCARD the database
docker compose -f docker-compose.deploy.yaml down -v
```

Upgrades apply Flyway migrations at startup, and Hibernate runs with `ddl-auto=validate`, so a
database that disagrees with the code stops startup instead of being silently reshaped. Read
[UPGRADING.md](UPGRADING.md) before upgrading anything whose data you care about — step one is a
backup.

---

## When it does not come up

| What you see | Usually |
|---|---|
| Compose refuses to start, naming a variable | No `.env`, or an empty value in it. That refusal is deliberate — the app ships no credential to fall back on. |
| `web` answers, `/api` returns 502 | The backend is still starting. A first boot runs migrations and seeds demo data; give it a minute and watch `logs -f backend`. |
| Backend exits during startup, mentioning Flyway | The database disagrees with the code. On a fresh volume this should not happen; if you restored a backup from a newer version, see UPGRADING.md. |
| Backend never becomes `healthy` | Check `logs backend`. A container killed without a message is usually memory — this needs roughly 1 GB, comfortable in 2 GB. |
| The build runs out of disk | `docker system prune -a` between attempts. The build stage carries a JDK and Maven's whole repository; only the JRE and one jar reach the final image. |
