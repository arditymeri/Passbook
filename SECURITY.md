# Security Policy

Passbook handles financial records. Reports about anything that could expose or corrupt them
are taken seriously, and this document tries to be honest about what the project does and does
not currently protect against.

## Reporting a Vulnerability

**Please do not open a public issue for a security problem.** A public report tells everyone
running an instance about the weakness before there is a fix available.

Use GitHub's private vulnerability reporting instead: go to the **Security** tab of this
repository and choose **Report a vulnerability**. That opens a private channel visible only to
the maintainer.

Useful reports include:

- what an attacker can achieve (read another instance's data, corrupt a ledger, execute code)
- the steps to reproduce it, ideally against a stock `docker-compose up` deployment
- the affected commit or version

**What to expect:** this is a solo-maintained project, not a funded product. There is no
guaranteed response time and no bug bounty. Reports are acknowledged and fixed on a best-effort
basis. If a fix will take a while, you will be told that rather than left in silence.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.0   | ✅ |
| earlier (unreleased snapshots) | ❌ — upgrade to 0.1.0 |

The project is pre-1.0. Only the latest release and the current `main` branch receive fixes;
there are no backports to older releases.

## Known and Accepted Limitations

The following are **documented design decisions or tracked gaps, not vulnerabilities**. Reports
about them are not needed — they are already in the
[README](README.md#before-you-self-host) and the
[constitution](.specify/memory/constitution.md).

- **Single shared credential, not multi-user.** One admin username and password protects the whole
  instance. There are no per-user accounts, no roles, and no row-level isolation — every instance
  is single-tenant, for one person or household, by design. "User B can see user A's data" is not
  a finding; there is only one user.
- **No transport encryption of its own.** The app speaks plain HTTP. TLS is the operator's
  responsibility, via a reverse proxy — [docs/DEPLOYING.md](docs/DEPLOYING.md) covers doing that.
  **Do not put the app itself on a public port with nothing in front of it.**
- **The session token is held in browser storage.** It is sent as a bearer token and kept in
  `localStorage`, so any script running on the page can read it. Moving to a server-set httpOnly
  cookie would mean taking on cross-site request forgery handling — a redesign of how sessions
  work rather than a hardening of what exists — and it has not been done. Reports of XSS itself
  are very much wanted; the storage choice is known.
- **A distributed attacker can lock the operator out.** Login throttling has an instance-wide tier,
  which is what stops guessing distributed across many addresses. Someone willing to keep it
  tripped can therefore keep the operator out for as long as they continue. That is a denial of
  service rather than a break-in, it clears on its own when they stop, the thresholds are
  configurable, and the alternative — no instance-wide tier — leaves distributed guessing
  unbounded against financial records.
- **Session tokens are not individually revocable.** Logging out or changing the password
  invalidates *all* existing sessions (via a token version counter) rather than one device's.
  At single-account scale this is the intended trade-off, not an oversight.
- **Dependency advisories with no practical exploit path** in this codebase. A version bump is
  welcome as a normal pull request rather than a security report.

The following **were** on this list and are no longer, as of the unreleased hardening work:
unlimited password guessing against `/auth/login` (attempts are now counted and refused, with the
refusal expiring unattended), a one-character password minimum (now twelve, enforced where a
password is set), and a deployed instance serving its own API browser and description to anonymous
visitors (now off in the deployment configuration).

## Rotate the historically published database password

Releases before 0.1.0 shipped a working PostgreSQL password in `docker-compose.yaml` and
`application.properties`. It is **still in this repository's published git history**, and removing
it from the working tree does not remove it from history.

If you have ever run an instance using that value, **rotate it** — set a new password in your
`.env` and change it on the database itself. Assuming that removal was sufficient is the mistake
this section exists to prevent. From 0.1.0 onward all secrets come from the operator's
environment, and the app refuses to start without them.

## In Scope

Reports that are genuinely useful:

- anything allowing data to leave an instance, or one instance's data to reach another
- injection through the REST API or the OpenAPI-generated layer
- flaws in correction and reversal handling that let a ledger be silently altered, contrary to
  Principle I of the constitution
- dependency vulnerabilities with a demonstrated path to exploitation here
- once bank synchronisation exists: anything touching connection credentials or the sync relay

## A Note for Self-Hosters

Because you run your own instance, its security is largely determined by how you deploy it —
where it listens, who can reach it, whether you changed the default credentials, and whether you
back it up. The **Before you self-host** section of the README lists what the project has not yet
solved for you. Read it before trusting the app with records you would be upset to lose.
