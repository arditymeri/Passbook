# Research: Single-User Authentication

## R1: Where authentication logic lands across the hexagonal boundary

**Decision**: Split by what the concern actually is, not by "auth is one blob":

- **Domain** (framework-free, Constitution Principle VIII): `AdminAccountDto` (username, password
  hash, a `tokenVersion` counter — see R2), `SetupAdminAccountService` (creates the one account,
  refuses a second), `AuthenticateService` (verifies username/password against the stored hash),
  `ValidateSessionService` (given a username + token-version pair extracted from a request, says
  whether that session is still current), `ChangePasswordService` (re-verifies the current
  password, then rotates the hash and bumps `tokenVersion`), `LogoutService` (bumps
  `tokenVersion`). All of this is pure "is this credential/session valid" business logic with no
  HTTP or JWT vocabulary in it — it takes and returns plain values (username, password, a
  version number), never a token.
- **Domain SPI (ports)**: `GetAdminAccountPersistencePort` / `SaveAdminAccountPersistencePort`
  (the single row), and a new `PasswordHasher` port (`hash(raw): String`,
  `matches(raw, hash): boolean`) — mirrors how every other cross-cutting capability in this
  codebase (persistence, matching) is a Domain-defined port implemented outside Domain, per
  Principle VIII's "all I/O MUST be mediated through port interfaces."
- **Infrastructure**: `AdminAccountEntity` + JPA repository + `AdminAccountPostgresAdapter`
  implementing the two persistence ports (same shape as every other `*PostgresAdapter` in this
  codebase); a `BCryptPasswordHasher` implementing `PasswordHasher` via Spring Security's
  `BCryptPasswordEncoder`.
- **Application**: everything that is actually HTTP/JWT-shaped — the OpenAPI contracts and
  generated delegates for `/auth/*`, the controller implementing them, a `JwtTokenService` that
  encodes/decodes the JWT itself (username + `tokenVersion` claims, signature, expiry) using a JWT
  library, a `SecurityFilterChain` bean, and a `JwtAuthenticationFilter` that reads the
  `Authorization` header, decodes the JWT via `JwtTokenService`, and calls Domain's
  `ValidateSessionService` before letting the request through.

**Rationale**: Domain already never depends on JPA/Kafka/HTTP (Principle VIII); a JWT is squarely
an HTTP wire-format concern the same way an OpenAPI-generated request DTO is, and belongs where
those already live (Application), not in Domain. What Domain legitimately owns is the *business*
question "is this username/password/version combination currently valid" — the same shape of
question every other Domain service in this codebase answers about bills, budgets, or recurring
series, just for one new entity.

**Alternatives considered**: Putting JWT encode/decode in Infrastructure (rejected — Infrastructure
today is exclusively persistence adapters; JWT is a request/response concern, not storage);
building the whole feature as one undifferentiated `AuthService` (rejected — every other feature in
this codebase already follows one-Domain-service-per-verb, e.g. `AddBillService`/`CorrectBillService`/
`RemoveBillService` rather than one `BillService`, and auth has no reason to be the exception).

## R2: Making logout and password-change actually invalidate a stateless JWT

**Decision**: Store one integer `tokenVersion` on the admin account row. Every issued JWT carries
the account's `tokenVersion` at issue time as a claim. `ValidateSessionService` compares the JWT's
claim against the row's *current* `tokenVersion` — a mismatch is rejected even though the token's
own signature and expiry are still technically valid. `LogoutService` and `ChangePasswordService`
both increment `tokenVersion`.

**Rationale**: A pure stateless JWT (validated only by signature + expiry) cannot be revoked before
it naturally expires — but FR-008 (logout) and User Story 4 (password change) both require the old
credential to stop working immediately, not just eventually. A full token blocklist is the general
solution to this, but this app has exactly one account, so a single version counter is the entire
blocklist a one-account system needs: bumping it invalidates every previously issued token in one
write, with no per-token bookkeeping and no separate storage.

**Alternatives considered**: Short-lived JWTs with no invalidation at all, accepting that logout is
purely client-side and a stolen token stays valid until natural expiry (rejected — contradicts
FR-008's explicit requirement, and the fix is nearly free at this scale); a server-side session
store instead of JWT entirely (rejected — the user already chose bearer-token JWT specifically so a
future local-data mobile app can authenticate the same way; a session store reintroduces the
server-side statefulness that decision was meant to avoid).

## R3: First-run setup vs. ongoing login

**Decision**: `GET /auth/status` (public, no data beyond a boolean) reports whether an admin
account exists yet. `POST /auth/setup` (public) creates the one account and returns a JWT
immediately, so completing setup *is* being logged in — no separate login step right after. Once
an account exists, `POST /auth/setup` unconditionally rejects with 409, permanently.

**Rationale**: Directly satisfies FR-001/FR-002 and SC-002's "under one minute" — a returning-user
login step immediately after first-run setup would be pure friction with no security benefit (the
operator just proved they control the credential by choosing it).

**Alternatives considered**: Bootstrapping the admin account from an environment variable at
container start instead of an in-app setup screen (rejected — more ops burden for a self-hosted
single operator, and harder to change later without redeploying; an in-app flow is also the only
one that works identically whether the instance ships as a Docker container, a future desktop
one-click installer, or run straight from the jar).

## R4: JWT signing secret

**Decision**: Read the signing secret from an environment variable (`APP_SECURITY_JWT_SECRET`). If
unset, generate a cryptographically random secret once per process start and log a clear warning
that it was auto-generated and that every existing session will need to log in again after any
restart. Never hardcode a secret in a committed properties file.

**Rationale**: Matches the Self-Hosting Obligations principle ("No credentials in version control.
All secrets via environment variables") for anything meant to be distributed — while still letting
a developer run the app locally with zero setup, at the acceptable cost of losing sessions across
restarts in that unconfigured case. This intentionally does not fix the *pre-existing*, unrelated
issue of the Postgres password being hardcoded in `application.properties` — that predates this
feature and is a separate cleanup.

**Alternatives considered**: Requiring the secret to always be explicitly configured, failing
startup otherwise (rejected — makes the local dev loop worse for no security gain, since a
self-hosted operator running without setting it is only exposed to "sessions reset on restart," not
to a weak or guessable secret).

## R5: What stays public vs. what requires a valid session

**Decision**: Public (no credential required): `GET /auth/status`, `POST /auth/setup`,
`POST /auth/login`, and the existing Swagger UI / OpenAPI docs endpoints (dev tooling, exposes only
the API shape, no financial data). Everything else — every existing controller in every existing
feature, including the device-sync export/import endpoints from feature 019 — requires a valid,
current JWT.

**Rationale**: Directly implements FR-003 ("no unauthenticated read or write path" for financial
data) while keeping the three endpoints that must be reachable *before* a credential exists (or
before login itself succeeds) unauthenticated by necessity. Leaving Swagger UI open matches
existing project convention (already documented as available in CLAUDE.md) and doesn't leak any
operator data — only the API's shape, which is already public in this app's own OpenAPI YAML files
checked into source control.

## R6: Frontend session handling

**Decision**: Store the JWT in `localStorage`. Add one small wrapper the existing `api/client.ts`
request helpers route through that (a) attaches `Authorization: Bearer <token>` to every request,
and (b) on a `401` response, clears the stored token and notifies the rest of the app (a tiny
module-level event target, since there is no existing global state manager) so `App.tsx` can reset
to the login screen — mirroring FR-010's "never show a broken dashboard on an expired session."
`App.tsx` gains one more top-level gate — `setup` / `login` / authenticated — checked before its
existing `view` switch, following the same pattern already used for `categories`/`accounts`/
`budgeting`/`goals`/`sync`.

**Rationale**: `localStorage` (not a cookie) is the natural fit for a bearer token that a future
mobile app will also need to hold, matches the user's own stated reason for choosing JWT over
server-side sessions, and needs no new dependency. Centralizing the 401 handling in the request
helpers (rather than in every component that calls `fetch`) means FR-010 is enforced in one place
and can't be forgotten in a future feature's API call.

**Alternatives considered**: An `httpOnly` cookie instead of `localStorage` (rejected — cookies are
automatically sent same-origin only, which works today but works against the explicit "same
backend, future mobile app" goal that motivated choosing JWT in the first place, and reintroduces
CSRF considerations a bearer token in a header does not have); a full state-management library
(rejected — this app has no existing global state manager, and one small event target is enough
for a single boolean-ish "session died" signal).

## R7: Password hashing

**Decision**: BCrypt via Spring Security's `BCryptPasswordEncoder`, behind the Domain-defined
`PasswordHasher` port (R1).

**Rationale**: Industry-standard, adaptive-cost, salted hashing; already the de facto default for
Spring applications, and directly satisfies FR-005's "cannot be reversed to recover the original
password."
