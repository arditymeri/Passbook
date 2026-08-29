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

The project is pre-1.0 and there are no released versions yet. Only the current `main` branch
receives fixes. Once tagged releases exist, this section will say which of them are supported.

## Known and Accepted Limitations

The following are **documented design decisions or tracked gaps, not vulnerabilities**. Reports
about them are not needed — they are already in the
[README](README.md#before-you-self-host) and the
[constitution](.specify/memory/constitution.md).

- **No authentication or authorisation.** The API is unauthenticated by design: every instance is
  single-tenant, intended for one person or household, and network placement is the access
  control. **Do not expose an instance to the internet.**
- **No transport encryption.** The app assumes it runs on localhost or inside a trusted network.
  TLS is the operator's responsibility, via a reverse proxy.
- **Default credentials in `docker-compose.yaml`.** The Postgres password and pgAdmin login are
  local development defaults, published deliberately so the stack starts with one command. Any
  deployment beyond a local machine must override them.
- **`spring.jpa.hibernate.ddl-auto=update`.** Schema is inferred at startup rather than migrated.
  This is a known release blocker and is unsafe for data you care about.
- **Dependency advisories with no practical exploit path** in this codebase. A version bump is
  welcome as a normal pull request rather than a security report.

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
