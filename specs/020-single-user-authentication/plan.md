# Implementation Plan: Single-User Authentication

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-single-user-authentication/spec.md`

## Summary

Protect this self-hosted instance with a single shared admin credential (username + password, set
up once on first run). Login is verified server-side and returns a JWT bearer token the frontend
attaches to every subsequent request; every existing REST endpoint (including feature 019's sync
export/import) starts requiring a valid, current token, with only `/auth/status`, `/auth/setup`,
`/auth/login`, and Swagger UI staying public. Real logout/password-change invalidation on an
otherwise-stateless JWT is achieved with one `tokenVersion` counter per account (research.md R2) —
no token blocklist needed at single-account scale. The frontend gains a first-run setup screen, a
login screen, and centralized 401-handling that returns to login instead of showing broken API
errors anywhere in the dashboard.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.4.0), TypeScript / React 18 (frontend, Vite)

**Primary Dependencies**: `spring-boot-starter-security` (new), a JWT library — `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` (new, pinned version TBD at implementation time to the latest 0.12.x), Spring Security's `BCryptPasswordEncoder` (from the new starter, no extra dependency). No new frontend dependency — `fetch` + `localStorage` are already used throughout `frontend/src`.

**Storage**: PostgreSQL via Spring Data JPA (existing) — one new table, `admin_account`, holding at most one row.

**Testing**: JUnit 5 + Mockito (Domain, existing convention); no frontend test runner exists anywhere in this repo (confirmed again for this feature — pre-existing gap, not introduced or worsened).

**Target Platform**: Linux server (Docker), same as every existing feature.

**Project Type**: Web application (existing Maven multi-module hexagonal backend + separate Vite/React frontend).

**Performance Goals**: Not a throughput-sensitive feature — login/setup/change-password are low-frequency, single-account operations. No new goal beyond "does not measurably slow down every other request" (JWT signature verification is sub-millisecond).

**Constraints**: Must not depend on any external identity provider or outbound network call (self-hosted, offline-capable per the constitution's Vision). Must not introduce `userId`/row-level tenancy anywhere (constitution v2.1.0's narrowed Deliberately-Out-of-Scope boundary — a single shared credential gate only).

**Scale/Scope**: Exactly one admin account, ever, per instance.

## Constitution Check

*GATE: Must pass before Phase 0 research is complete. Re-checked after Phase 1 design.*

- **I. Transaction Immutability** — N/A. This feature touches no `Bill`/`Income` row.
- **II. Ingestion Is Idempotent** — N/A. No new ingestion path.
- **III. Balance Derivation** — N/A. No balance-bearing entity involved.
- **IV. Currency Precision** — N/A. No monetary field in this feature's new entity.
- **V. Audit Trail & Observability** — Applies at the margin: login success/failure and
  password-change are state-changing security events. Structured logging of these (no plaintext
  password ever logged; failed-login logs record only that an attempt failed, not which part was
  wrong, matching FR-012) is in scope for the implementation, not a violation to justify.
- **VI. Test-First Development** — Applies in full: `SetupAdminAccountService`,
  `AuthenticateService`, `ValidateSessionService`, `ChangePasswordService`, `LogoutService` are all
  Domain business-rule logic and MUST have unit tests written alongside them, same as every prior
  feature's Domain services.
- **VII. API Contract Stability** — Applies in full: `/auth/*` is defined in OpenAPI YAML
  (`contracts/auth-api.yaml` / `auth-model.yaml`, Phase 1 output below) before implementation, per
  this codebase's established delegate-pattern codegen convention.
- **VIII. Hexagonal Architecture Compliance** — Applies in full, and is the principle this plan's
  research (research.md R1) spends the most effort satisfying: Domain holds only the
  framework-free "is this credential/session valid" business logic; JWT encoding/decoding and the
  Spring Security filter chain — both HTTP/wire-format concerns — live in Application, matching
  where REST controllers already live; persistence goes through a Domain-defined port implemented
  in Infrastructure, exactly like every other entity in this codebase.
- **Deliberately Out of Scope (constitution v2.1.0)** — This feature is the single shared
  instance-level credential gate that boundary was just narrowed to explicitly permit; it
  introduces no `userId`, no row-level tenancy, no per-account data isolation, no signup flow.
- **Self-Hosting Obligations** — This feature directly satisfies the newly-added "an
  instance-level authentication gate MUST be enabled" bullet. The JWT signing secret follows "no
  credentials in version control, all secrets via environment variables" (research.md R4).

**Result**: PASS. No violations requiring justification in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/020-single-user-authentication/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/            # Phase 1 output
│   ├── auth-api.yaml
│   └── auth-model.yaml
└── tasks.md              # Phase 2 output (/speckit-tasks — not created by this command)
```

### Source Code (repository root)

Existing hexagonal Maven multi-module backend + Vite/React frontend (see `CLAUDE.md`). This
feature's new files follow the exact same per-layer conventions every prior feature already
established:

```text
Application/src/main/resources/swagger/auth/
├── auth-api-controller.yaml       # GET /auth/status, POST /auth/setup, /login, /logout, /change-password
└── auth-model.yaml                # authStatus, setupRequest, loginRequest, changePasswordRequest, session

Application/src/main/java/at/ymeri/my/finance/
├── controller/auth/
│   └── AuthController.java        # implements the generated AuthApi delegate
├── application/mapper/
│   └── AuthMapper.java             # Domain DTOs <-> generated API models
└── security/                       # new package — the HTTP/JWT-specific half of R1
    ├── JwtTokenService.java        # encode/decode, username + tokenVersion claims, expiry
    ├── JwtAuthenticationFilter.java # reads Authorization header, calls ValidateSessionService
    └── SecurityConfig.java         # SecurityFilterChain — public paths vs. authenticated

Domain/src/main/java/at/ymeri/my/finance/domain/
├── data/auth/
│   └── AdminAccountDto.java
├── api/
│   ├── SetupAdminAccountService.java
│   ├── AuthenticateService.java
│   ├── ValidateSessionService.java
│   ├── ChangePasswordService.java
│   └── LogoutService.java
├── service/auth/
│   └── *Impl.java                  # one per interface above
└── spi/auth/
    ├── GetAdminAccountPersistencePort.java
    ├── SaveAdminAccountPersistencePort.java
    └── PasswordHasher.java         # port; BCrypt implementation lives in Infrastructure

Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/
├── entity/AdminAccountEntity.java
├── repository/AdminAccountRepository.java
├── adapter/postgres/auth/AdminAccountPostgresAdapter.java   # implements both persistence ports
└── security/BCryptPasswordHasher.java                        # implements PasswordHasher

frontend/src/
├── types/index.ts          # AuthStatus, Session types
├── api/client.ts            # fetchAuthStatus, setupAdminAccount, login, logout, changePassword; centralized Authorization header + 401 handling
├── auth/
│   ├── authToken.ts         # localStorage get/set/clear + a tiny "session died" event target
│   └── AuthGate.tsx         # the setup/login/authenticated top-level switch, mirrors App.tsx's existing `view` pattern
└── components/
    ├── SetupPage.tsx
    ├── LoginPage.tsx
    └── ChangePasswordDialog.tsx
```

**Structure Decision**: No new module. Every new backend file lands in the existing
Application/Domain/Infrastructure split exactly where every prior feature's equivalent concern
already lives, with one new addition: an Application-level `security/` package, since a JWT
filter and Spring Security config are HTTP-boundary concerns co-located with the REST controllers
they protect, not persistence adapters and not framework-free Domain logic (research.md R1).
Frontend gains one new `auth/` directory for the small amount of cross-cutting session state this
feature needs, alongside the existing flat `components/`.

## Complexity Tracking

*No Constitution Check violations — this section intentionally left empty.*
