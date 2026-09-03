---

description: "Task list for the single-user authentication feature"
---

# Tasks: Single-User Authentication

**Input**: Design documents from `/specs/020-single-user-authentication/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included and REQUIRED for the new Domain logic — Constitution Principle VI (Test-First,
NON-NEGOTIABLE) mandates unit-test coverage for all new business-rule logic in the Domain module.
A new `@WebMvcTest`-slice test also covers the Application-layer security wiring itself (that an
unauthenticated request to an existing endpoint is now rejected) — this needs no database or
Docker, so it is not subject to this repo's usual "no Docker in this sandbox" limitation. No
frontend test tasks are included: this repo has no frontend test runner anywhere (a confirmed
pre-existing gap across every prior feature), and this feature does not introduce one.

**Organization**: Like feature 019, this does not decompose into cleanly independent story-sized
slices: an unprotected endpoint anywhere is a hole in the whole feature's promise, so the entire
backend — Domain services, persistence, JWT issuance/validation, and the Spring Security filter
chain protecting every existing endpoint — is Foundational (blocking, shared), fully correct and
fully tested before any user story begins. The four user-story phases below are the frontend
screens and wiring layered on top of an already-correct, already-enforced backend.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths are included in every task

## Path Conventions

Existing hexagonal Maven multi-module backend + Vite/React frontend (see plan.md Project
Structure). Domain API interfaces flat under `domain/api/`; SPI ports under `domain/spi/auth/`;
DTOs under `domain/data/auth/`; Infrastructure adapters under
`infrastructure/adapter/postgres/auth/`; Application controllers at
`at.ymeri.my.finance.controller.auth.AuthController` (implementing a generated delegate at
`at.ymeri.my.finance.application.controller.auth.AuthApi` — different package, matching every
other feature's controller/delegate split); a new `at.ymeri.my.finance.security` package for the
HTTP/JWT-specific pieces (research.md R1). `Application/target/generated-sources/` accumulates
stale `skipOverwrite=true`-protected files across builds — T005 must `clean` first (the same
gotcha every prior feature with new generated models has hit).

---

## Phase 1: Setup (OpenAPI contracts, spec-first per Constitution Principle VII)

- [X] T001 [P] Create `Application/src/main/resources/swagger/auth/auth-api-controller.yaml`
  (`GET /auth/status`, `POST /auth/setup`, `POST /auth/login`, `POST /auth/logout`,
  `POST /auth/change-password`), adapting `specs/020-single-user-authentication/contracts/auth-api.yaml`
  to this repo's real swagger conventions (compare `sync-export-controller.yaml`/
  `sync-import-controller.yaml` for style — one file, one generated delegate with all five
  methods, matching how `sync-import-controller.yaml` already puts two related operations in one
  file).
- [X] T002 [P] Create `Application/src/main/resources/swagger/auth/auth-model.yaml`
  (`authStatus`, `setupRequest`, `loginRequest`, `changePasswordRequest`, `session`), adapting
  `specs/020-single-user-authentication/contracts/auth-model.yaml`.
- [X] T003 [P] Add `spring-boot-starter-security` to the root `pom.xml`'s `<dependencies>` (same
  place `spring-boot-starter-web`/`spring-boot-starter-hateoas` already live, inherited by every
  module — Domain will have it on the classpath but, per research.md R1, will never import it,
  exactly like it already never imports `spring-boot-starter-web` today despite it being
  available). Add `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, and `jjwt-jackson` (latest 0.12.x,
  pinned exact version) to `Application/pom.xml`, where `JwtTokenService` (T018) will use them.
- [X] T004 [P] Register a new `auth` codegen execution in `Application/pom.xml`, modeled on the
  existing `sync-export`/`sync-import` executions (same `configOptions` as every other execution
  in that file): `<id>auth</id>` (`apiPackage` `${api-package}.auth`, input
  `swagger/auth/auth-api-controller.yaml`).
- [X] T005 Run `./mvnw -pl Application clean generate-sources` (depends on T001-T004) — **must**
  use `clean` per the stale-generated-file gotcha. Confirm `AuthApi`, `AuthStatus`,
  `SetupRequest`, `LoginRequest`, `ChangePasswordRequest`, and `Session` all appear under
  `Application/target/generated-sources/openapi/src/main/java/at/ymeri/my/finance/application/`.

---

## Phase 2: Foundational (blocking prerequisites — the entire auth engine)

**⚠️ CRITICAL**: Every user story below is a frontend screen or wiring change on top of the
backend built here — none of them can be implemented, let alone correctly, until this phase is
complete, every existing endpoint is actually enforcing authentication, and its own tests pass.

### Domain (research.md R1 — framework-free credential/session business logic)

- [X] T006 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/auth/AdminAccountDto.java`
  (`id`, `username`, `passwordHash`, `tokenVersion` (int), `createdAt`, `updatedAt` —
  data-model.md).
- [X] T007 [P] Create
  `Domain/src/main/java/at/ymeri/my/finance/domain/spi/auth/GetAdminAccountPersistencePort.java`
  (`Optional<AdminAccountDto> get()` — at most one row ever exists) and
  `Domain/src/main/java/at/ymeri/my/finance/domain/spi/auth/SaveAdminAccountPersistencePort.java`
  (`AdminAccountDto save(AdminAccountDto)` — used for both the initial create and every
  subsequent update, matching the `save()`-as-upsert convention already established in this
  codebase, e.g. `SetBudgetPostgresAdapter`).
- [X] T008 [P] Create `Domain/src/main/java/at/ymeri/my/finance/domain/spi/auth/PasswordHasher.java`
  (`String hash(String rawPassword)`, `boolean matches(String rawPassword, String hash)`).
- [X] T009 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/SetupAdminAccountService.java` and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/SetupAdminAccountServiceImpl.java`
  — `setup(username, password)`: throws `IllegalStateException` if
  `GetAdminAccountPersistencePort.get()` already returns a value (FR-002); otherwise hashes the
  password via `PasswordHasher` and saves a new `AdminAccountDto` with `tokenVersion=0` (depends
  on T006-T008).
- [X] T010 Implement `Domain/src/main/java/at/ymeri/my/finance/domain/api/AuthenticateService.java`
  and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/AuthenticateServiceImpl.java` —
  `authenticate(username, password): Optional<AdminAccountDto>`: empty if no account exists, empty
  if the username doesn't match, empty if `PasswordHasher.matches` fails — the same generic
  "no match" result whichever it was (FR-012 is enforced by the caller never being able to tell
  these apart from this return type alone) (depends on T006-T008).
- [X] T011 Implement
  `Domain/src/main/java/at/ymeri/my/finance/domain/api/ValidateSessionService.java` and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/ValidateSessionServiceImpl.java`
  — `isValid(username, tokenVersion): boolean`: true only if an account exists, the username
  matches, and `tokenVersion` equals the account's current `tokenVersion` (research.md R2)
  (depends on T006-T008).
- [X] T012 Implement `Domain/src/main/java/at/ymeri/my/finance/domain/api/ChangePasswordService.java`
  and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/ChangePasswordServiceImpl.java` —
  `changePassword(currentPassword, newPassword)`: loads the one account, verifies
  `currentPassword` via `PasswordHasher`, throws on mismatch (leaving the stored hash and
  `tokenVersion` untouched), otherwise hashes `newPassword`, increments `tokenVersion`, updates
  `updatedAt`, and saves (depends on T006-T008).
- [X] T013 Implement `Domain/src/main/java/at/ymeri/my/finance/domain/api/LogoutService.java` and
  `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/LogoutServiceImpl.java` —
  `logout()`: increments the one account's `tokenVersion` and saves (depends on T006-T008).
- [X] T014 [P] Create
  `Domain/src/test/java/at/ymeri/my/finance/domain/service/auth/SetupAdminAccountServiceImplTest.java`,
  `AuthenticateServiceImplTest.java`, `ValidateSessionServiceImplTest.java`,
  `ChangePasswordServiceImplTest.java`, `LogoutServiceImplTest.java` — covering: setup rejects a
  second account; authenticate rejects a wrong username and a wrong password identically (both
  return empty, never a different exception/message per case); validate accepts a matching
  version and rejects a stale one; change-password rejects a wrong current password without
  mutating state, and on success bumps `tokenVersion`; logout bumps `tokenVersion` (depends on
  T009-T013).

### Infrastructure (persistence + hashing)

- [X] T015 [P] Create
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/entity/AdminAccountEntity.java`
  (`id` UUID, `username`, `password_hash`, `token_version` int, `created_at`, `updated_at`) and
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/repository/AdminAccountRepository.java`
  (Spring Data JPA, `findFirstByOrderByCreatedAtAsc()` or equivalent single-row lookup).
- [X] T016 Implement
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/adapter/postgres/auth/AdminAccountPostgresAdapter.java`
  implementing both `GetAdminAccountPersistencePort` and `SaveAdminAccountPersistencePort` (depends
  on T007, T015).
- [X] T017 [P] Implement
  `Infrastructure/src/main/java/at/ymeri/my/finance/infrastructure/security/BCryptPasswordHasher.java`
  implementing `PasswordHasher` via Spring Security's `BCryptPasswordEncoder` (depends on T008,
  T003).

### Application (JWT + Spring Security wiring + REST layer)

- [X] T018 [P] Implement
  `Application/src/main/java/at/ymeri/my/finance/security/JwtTokenService.java` —
  `issue(username, tokenVersion): (token, expiresAt)` and
  `parse(token): Optional<{username, tokenVersion}>` (empty on bad signature, malformed token, or
  expiry). Signing key: reads `APP_SECURITY_JWT_SECRET` from the environment; if unset, generates
  a cryptographically random key once at startup and logs a clear warning that it changes every
  restart (research.md R4 — never a hardcoded secret). Token expiry: a fixed duration read from
  `app.security.jwt-expiry` (default 24h) (depends on T003).
- [X] T019 [P] Implement `Application/src/main/java/at/ymeri/my/finance/application/mapper/AuthMapper.java`
  (MapStruct — `AdminAccountDto`/session fields to the generated `Session`/`AuthStatus` API
  models; never maps a raw or hashed password onto anything logged) (depends on T005).
- [X] T020 Implement `Application/src/main/java/at/ymeri/my/finance/controller/auth/AuthController.java`
  implementing the generated `AuthApi` delegate — `getAuthStatus` (public, calls
  `GetAdminAccountPersistencePort` — or a small pass-through, whichever keeps Domain/Application
  boundaries clean — to report whether an account exists), `setupAdminAccount` (calls
  `SetupAdminAccountService`, then `JwtTokenService.issue`, returns 409 via `@ExceptionHandler` on
  `IllegalStateException`), `login` (calls `AuthenticateService`, 401 via `@ExceptionHandler` on
  empty result, else `JwtTokenService.issue`), `logout` (calls `LogoutService`), `changePassword`
  (calls `ChangePasswordService`, 401 on the current-password mismatch) (depends on T009-T013,
  T018, T019).
- [X] T021 Implement
  `Application/src/main/java/at/ymeri/my/finance/security/JwtAuthenticationFilter.java` — a
  `OncePerRequestFilter` that reads the `Authorization: Bearer <token>` header, calls
  `JwtTokenService.parse`, and — if present — calls `ValidateSessionService.isValid`; on success
  sets an authenticated `SecurityContext`; on any failure (missing header, bad token, stale
  `tokenVersion`) leaves the context unauthenticated and lets Spring Security's own entry point
  return 401 (depends on T011, T018).
- [X] T022 Implement `Application/src/main/java/at/ymeri/my/finance/security/SecurityConfig.java` —
  a `SecurityFilterChain` bean: stateless session policy, CSRF disabled (a bearer-token API, not
  cookie-based — no CSRF surface), `permitAll` on `/auth/status`, `/auth/setup`, `/auth/login`,
  `/swagger-ui/**`, `/v3/api-docs/**` (research.md R5), `authenticated()` on everything else,
  registers `JwtAuthenticationFilter` before Spring Security's standard authentication filter
  (depends on T021).
- [X] T023 [P] Create
  `Application/src/test/java/at/ymeri/my/finance/security/SecurityConfigTest.java` — a
  `@WebMvcTest`-slice test (no database, no Docker) asserting: a request to an arbitrary existing
  endpoint (e.g. `GET /accounts`) with no `Authorization` header returns 401; the same request
  with a valid token (obtained by mocking `ValidateSessionService` to return true) reaches the
  controller; `GET /auth/status` succeeds with no header at all (depends on T020-T022).

### Frontend shared session plumbing

- [X] T024 [P] Add `AuthStatus` and `Session` types to `frontend/src/types/index.ts`, mirroring
  `auth-model.yaml` field-for-field.
- [X] T025 [P] Create `frontend/src/auth/authToken.ts` — `getToken()`/`setToken()`/`clearToken()`
  over `localStorage`, plus a small `EventTarget`-based "session died" signal
  (`sessionDied()`/`onSessionDied(listener)`) other modules can dispatch/subscribe to without a
  new state-management dependency (research.md R6).
- [X] T026 Extend `frontend/src/api/client.ts`: add `fetchAuthStatus()`, `setupAdminAccount(req)`,
  `login(req)`, `logoutRequest()`, `changePasswordRequest(req)`; modify the existing `request`,
  `post`, `postAndReturn`, `putAndReturn`, `del` helpers to attach
  `Authorization: Bearer <token>` (from `authToken.ts`) to every call, and to call
  `sessionDied()` + `clearToken()` whenever a response is `401` (depends on T024, T025).

**Checkpoint**: Every existing endpoint in this app now rejects an unauthenticated request
(T022/T023 prove it), `/auth/*` is fully functional end-to-end, and the frontend has everything it
needs to hold and attach a session — but nothing in the UI shows a login or setup screen yet. Every
user story below is a frontend increment on top of this already-correct, already-enforced backend.

---

## Phase 3: User Story 1 - Protect a Freshly Deployed Instance (Priority: P1) 🎯 MVP

**Goal**: Before any dashboard content loads, an operator either sets up the one admin account
(fresh instance) or is shown a login screen (already-configured instance) — and there is no way
around either.

**Independent Test**: Point the frontend at a fresh backend with no admin account; confirm the
setup screen appears instead of the dashboard; complete setup; confirm a new session (private
window, no stored token) is shown a login screen, not the dashboard.

- [X] T027 [US1] Create `frontend/src/components/SetupPage.tsx` — username/password form, calls
  `setupAdminAccount`, stores the returned token via `authToken.ts`, then calls an
  `onAuthenticated` callback (depends on T024-T026).
- [X] T028 [US1] Create `frontend/src/auth/AuthGate.tsx` — on mount, calls `fetchAuthStatus()`;
  while loading, renders nothing (no flash of dashboard content, per FR-009); if
  `!adminAccountConfigured`, renders `SetupPage`; if configured but no stored token (or a stored
  token that a first authenticated call rejects — checked in Phase 4/5), renders a placeholder
  gate for now; if authenticated, renders `children` (depends on T027).
- [X] T029 [US1] Mount `<AuthGate>` around the existing app root in `frontend/src/main.tsx` (or
  `App.tsx`, whichever currently owns the top-level render) so nothing in the existing dashboard,
  including its own data-fetching `useEffect`s, ever runs before `AuthGate` decides the user is
  authenticated (depends on T028).

**Checkpoint**: User Story 1 is independently testable per its Independent Test above (the login
screen itself is still an unstyled placeholder until Phase 4 — that placeholder is only what
blocks Story 1's own acceptance scenario 2, not Story 1 as a whole, since scenario 2 only requires
that the dashboard is *not* shown).

---

## Phase 4: User Story 2 - Log In and Work Without Re-Authenticating Constantly (Priority: P1)

**Goal**: A returning operator logs in once and uses the app normally without repeated prompts.

**Independent Test**: With an admin account already configured, log in with correct credentials
and confirm the dashboard loads; attempt several unrelated actions and confirm none re-prompt;
attempt login with a wrong password and confirm a generic, non-revealing error.

- [X] T030 [US2] Create `frontend/src/components/LoginPage.tsx` — username/password form, calls
  `login`, stores the returned token, calls `onAuthenticated`; on a `401` shows one generic
  "Incorrect username or password" message (FR-012), never distinguishing which field was wrong
  (depends on T024-T026).
- [X] T031 [US2] Replace `AuthGate.tsx`'s Phase-3 placeholder gate with `LoginPage` when configured
  but unauthenticated (depends on T028, T030).

**Checkpoint**: User Stories 1 AND 2 both independently functional — the full login gate now works
end-to-end, and a session, once established, is not re-prompted for on ordinary use (no code path
in this app calls `login`/`setup` except these two screens).

---

## Phase 5: User Story 3 - Session Expires Gracefully (Priority: P2)

**Goal**: An expired or invalidated session — or an explicit logout — returns the operator cleanly
to the login screen from anywhere in the app, never a broken dashboard or raw error.

**Independent Test**: Log in, corrupt/clear the stored token (or wait for expiry), trigger any
backend-calling action, and confirm a clean return to the login screen; log in, log out, and
confirm the app returns to the login screen with the old token no longer accepted.

- [X] T032 [US3] Wire `AuthGate.tsx` to subscribe to `authToken.ts`'s `onSessionDied` (T025) for
  its whole lifetime (not just on mount) and re-render as unauthenticated the moment it fires —
  T026's `401` handling in `api/client.ts` is what triggers this from anywhere any component calls
  the backend (depends on T025, T026, T028).
- [X] T033 [US3] Add a "Log out" action reachable from the main app UI (e.g. the app bar, next to
  the other settings-style buttons already there) that calls `logoutRequest()`, then `clearToken()`
  and `sessionDied()` regardless of whether the request itself succeeded, so the frontend always
  ends up back at the login screen (depends on T026, T032).

**Checkpoint**: All of User Stories 1, 2, and 3 independently functional.

---

## Phase 6: User Story 4 - Change the Admin Password (Priority: P3)

**Goal**: A logged-in operator can change their password by re-confirming the current one.

**Independent Test**: While logged in, change the password; confirm the old password is rejected
and the new one works on the next login; confirm an incorrect current password is rejected without
changing anything.

- [X] T034 [US4] Create `frontend/src/components/ChangePasswordDialog.tsx` — current-password +
  new-password form, calls `changePasswordRequest`; a `401` (wrong current password) shows an
  inline error and leaves the dialog open; on success, since a password change invalidates every
  session including the caller's own (research.md R2), calls `clearToken()` + `sessionDied()` and
  closes, returning the operator to the login screen to log in with the new password (depends on
  T026).
- [X] T035 [US4] Surface a "Change Password" action reachable from the main app UI (e.g. the app
  bar) opening `ChangePasswordDialog` (depends on T034).

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T036 [P] Run `./mvnw -pl Domain test` and confirm T014's five new test classes, plus every
  pre-existing Domain test, all pass.
- [X] T037 [P] Run `./mvnw -pl Application test` and confirm T023's `SecurityConfigTest` passes.
- [X] T038 [P] Run `cd frontend && npx tsc --noEmit` to typecheck the new frontend code (this repo
  has no frontend test runner anywhere — a pre-existing gap this feature does not introduce or
  worsen).
- [ ] T039 Execute `specs/020-single-user-authentication/quickstart.md`'s 4 manual scenarios
  end-to-end against a running stack. Expected BLOCKED in this development sandbox (no Docker
  daemon available, consistent with every prior feature) — must be run manually once
  implementation lands in an environment with Docker; report honestly rather than marking complete
  if not actually run.
- [X] T040 Mark all tasks in this file `[X]` (except any genuinely not run, per T039), then commit
  and push the implementation to `claude/project-status-s0au7m`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T018-T023 need the generated `AuthApi`/models from
  T005) — BLOCKS all four user stories in full, same reasoning as feature 019: even User Story 1's
  simplest case (show a setup screen) needs `GET /auth/status` and `POST /auth/setup` fully
  working and every other endpoint already enforcing the gate they're about to be shown behind.
- **User Story 1 (Phase 3)**: Depends on Foundational completion. No dependency on US2/US3/US4
  beyond the shared `AuthGate.tsx` file it creates (which US2 then edits, not races).
- **User Story 2 (Phase 4)**: Depends on Foundational completion, and edits the `AuthGate.tsx` file
  T028 created — start after T028, not merely after Phase 2.
- **User Story 3 (Phase 5)**: Depends on Foundational completion and on `AuthGate.tsx` existing
  (T028) — start after T028; independent of US2's own T030/T031 (different branch of the same
  file's logic, sequence to avoid a same-file conflict, not because either depends on the other).
- **User Story 4 (Phase 6)**: Depends on Foundational completion (T026) only — genuinely
  independent of US1/US2/US3's frontend files.

### Within Foundational

- T006-T008 (different files, no dependencies) run in parallel; T009-T013 each need all of
  T006-T008; T014 needs T009-T013.
- T015, T017 (different files) run in parallel; T016 needs T007 + T015.
- T018, T019 (different files, both depend only on T003/T005) run in parallel; T020 needs
  T009-T013 + T018 + T019; T021 needs T011 + T018; T022 needs T021; T023 needs T020-T022.
- T024, T025 (different files) run in parallel; T026 needs both.

### Parallel Opportunities

- Phase 1: T001 ∥ T002 ∥ T003 ∥ T004.
- Phase 2: T006 ∥ T007 ∥ T008; T015 ∥ T017; T018 ∥ T019; T024 ∥ T025.
- Phase 7: T036 ∥ T037 ∥ T038.

---

## Implementation Strategy

### MVP First

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks every user story, and is where essentially
   all of this feature's real complexity and risk lives, exactly like feature 019's merge engine).
3. Complete Phase 3: User Story 1 — a freshly deployed instance is now genuinely protected.
4. **STOP and VALIDATE**: confirm Story 1's Independent Test passes.
5. Continue with Phases 4-6 in priority order; each is a small, additive frontend increment.

### Incremental Delivery

Each user-story phase is deployable on its own once Foundational is done — Story 1 alone already
delivers the feature's core promise (no unauthenticated access to a freshly deployed instance);
Stories 2-4 are refinements to the experience of using and maintaining that gate.
