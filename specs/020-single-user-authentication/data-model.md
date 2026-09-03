# Data Model: Single-User Authentication

## New persisted entity

### Admin Account (`AdminAccountEntity`)

The one and only credential set protecting this instance. Exactly zero or one row ever exists —
enforced in `SetupAdminAccountService` (checks none exists before creating one), not by a DB
constraint beyond the table's natural single-row usage.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key, same convention as every other entity in this codebase |
| `username` | String | Chosen at first-run setup; not editable in v1 (no requirement for it) |
| `passwordHash` | String | BCrypt hash — never the raw password, never reversible |
| `tokenVersion` | int | Starts at 0; incremented on logout and on password change (research.md R2) |
| `createdAt` | OffsetDateTime | Set once, at setup |
| `updatedAt` | OffsetDateTime | Bumped on password change |

**Explicitly not part of device-sync (feature 019)**: this is instance-level configuration, not
financial data — `SyncSnapshotDto` gains no new field for it, matching the constitution's framing
of authentication as protecting the instance rather than being data the instance manages.

## Derived (non-persisted) concepts

### Session

Not a stored entity — a session is entirely represented by a JWT the frontend holds, containing:

| Claim | Meaning |
|---|---|
| `sub` | The admin account's username |
| `tokenVersion` | The account's `tokenVersion` at the moment this token was issued |
| `iat` / `exp` | Standard issued-at / expiry claims — expiry is a fixed duration from issue (research.md: "on the order of a working day") |

A request's session is valid only if: the JWT's signature verifies, `exp` has not passed, **and**
its `tokenVersion` claim equals the admin account's *current* `tokenVersion` (research.md R2) — the
first two are checked by the JWT library itself; the third is `ValidateSessionService`, a Domain
business rule.

## Validation rules

- Username and password are both required (non-blank) at setup; no further format constraints in
  v1 (this is a single self-chosen credential, not a public registration form — see spec
  Assumptions).
- `POST /auth/setup` MUST reject (409) if an admin account already exists, unconditionally and
  permanently — this is the mechanism that keeps the system single-user (FR-002).
- `POST /auth/login` MUST return the same generic "incorrect credentials" error whether the
  username or the password was wrong (FR-012) — never reveal which.
- `POST /auth/change-password` MUST re-verify the caller's current password before accepting a new
  one, and MUST bump `tokenVersion` on success so every other outstanding session (if any) is
  invalidated too, not just the one that requested the change.
