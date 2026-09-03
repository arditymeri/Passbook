# Feature Specification: Single-User Authentication

**Feature Branch**: `020-single-user-authentication`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Add a login gate protecting this self-hosted Passbook instance, matching the single-operator trust model already established (data never leaves the operator's devices). One admin account (username + password, set up on first run) guards the whole app: every API endpoint requires a valid credential, and the React frontend shows a login screen before any dashboard content loads. Authentication is username/password verified server-side, with a JWT issued on successful login and sent as a bearer token on subsequent requests (chosen over server-side sessions to play nicer with a future mobile app that also needs to authenticate against the same backend). No multi-user accounts, no roles/permissions, no external identity provider (OAuth) in this version - this is a single shared credential gate, not a multi-tenant user system. Passwords must be stored hashed, never in plaintext. The JWT needs a sensible expiry and the frontend needs to handle an expired/invalid token by returning the user to the login screen rather than showing broken API-error states throughout the dashboard."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Protect a Freshly Deployed Instance (Priority: P1)

An operator deploys Passbook for the first time. Before any financial data can be viewed or entered, they are asked to create the one admin credential (username and password) that will protect this instance. From that point on, nobody can see or change any data without that credential.

**Why this priority**: This is the entire point of the feature — without it, a deployed instance is wide open to anyone who can reach it on the network. It is the minimum slice that delivers the feature's value.

**Independent Test**: Deploy a fresh instance with no admin account yet, confirm the app presents a "create your admin account" step before showing any dashboard content, complete it, and confirm the credential is now required to get back in.

**Acceptance Scenarios**:

1. **Given** a freshly deployed instance with no admin account yet configured, **When** the operator opens the app, **Then** they are shown a one-time setup screen to choose a username and password, not the dashboard.
2. **Given** the admin account has already been set up, **When** a new browser session opens the app without valid credentials, **Then** the user sees a login screen and no financial data or API responses are visible.
3. **Given** the admin account has already been set up, **When** someone tries to reach the one-time setup screen again, **Then** they are redirected to the login screen instead — setup cannot be repeated or used to create a second account.

---

### User Story 2 - Log In and Work Without Re-Authenticating Constantly (Priority: P1)

The operator, having already set up their credential, logs in with their username and password and then uses the app normally — browsing transactions, adding bills, running exports — without being asked to log in again on every action.

**Why this priority**: Equally essential to Story 1: a login gate that can't actually be logged into, or that demands re-authentication constantly, makes the app unusable. Both are needed for a minimally viable protected instance.

**Independent Test**: Log in once with valid credentials, then perform several unrelated actions across the app (view dashboard, add a bill, open settings) and confirm none of them re-prompt for credentials.

**Acceptance Scenarios**:

1. **Given** a configured admin account, **When** the operator enters the correct username and password, **Then** they are taken to the dashboard and can use the app normally.
2. **Given** a configured admin account, **When** the operator enters an incorrect username or password, **Then** they see a clear error and remain on the login screen, with no access granted.
3. **Given** a successfully logged-in session, **When** the operator performs any in-app action within the session's valid period, **Then** it succeeds without prompting for credentials again.

---

### User Story 3 - Session Expires Gracefully (Priority: P2)

After enough time has passed that the operator's session is no longer valid — or after they explicitly log out — the next action they take returns them cleanly to the login screen, rather than showing broken pages, spinning loaders, or raw error messages.

**Why this priority**: Sessions must expire for this to be meaningfully secure, but a harsh or confusing expiry experience would undermine trust in the whole feature. This builds directly on Stories 1 and 2 and is not independently useful without them.

**Independent Test**: Log in, invalidate the session (let it expire, or log out), then attempt an action that requires the backend and confirm the app returns to the login screen rather than showing an error state.

**Acceptance Scenarios**:

1. **Given** a session that has expired, **When** the operator takes any action that calls the backend, **Then** they are returned to the login screen instead of seeing a broken dashboard or raw error message.
2. **Given** a logged-in session, **When** the operator explicitly logs out, **Then** their credential is no longer accepted for further requests and they see the login screen.

---

### User Story 4 - Change the Admin Password (Priority: P3)

The operator wants to change their password (for example, after suspecting it may have been exposed) without needing to redeploy or manually edit the database.

**Why this priority**: A reasonable expectation for any account-holding system, but the app is already secure and usable without it — the operator can still get value from Stories 1-3 alone.

**Independent Test**: While logged in, change the password to a new value, then confirm the old password no longer works and the new one does.

**Acceptance Scenarios**:

1. **Given** a logged-in session, **When** the operator submits their current password and a new password, **Then** future logins require the new password and the old one is rejected.
2. **Given** a logged-in session, **When** the operator submits an incorrect current password while trying to change it, **Then** the change is rejected and the existing password remains in effect.

---

### Edge Cases

- What happens if the operator forgets their password and has no way to reset it in-app (no email/SMS available for a self-hosted, offline-first system)? A documented recovery path outside the app (e.g. a server-side reset procedure) is assumed necessary — see Assumptions.
- What happens if two browser tabs are open and the session expires while the operator is mid-edit in one of them? The in-progress action fails gracefully and the operator is returned to login without silently losing already-saved data (unsaved form input may be lost, which is acceptable).
- What happens if someone calls the API directly (not through the frontend) without a credential? Every endpoint must reject the request the same way the frontend flow does — there is no back door through the API.
- What happens on repeated failed login attempts? Out of scope for v1 beyond returning a clear "incorrect credentials" error each time; no lockout/throttling is required for a single-operator instance (see Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow exactly one admin account, created via a one-time setup step the first time the instance is run with no account configured.
- **FR-002**: System MUST prevent the one-time setup step from running again once an admin account exists, closing off any path to creating a second account.
- **FR-003**: System MUST require a valid credential for every piece of financial data and every action the app exposes — there is no unauthenticated read or write path, including data exported or imported via the existing device-sync feature.
- **FR-004**: System MUST verify the submitted username and password against the stored admin account before granting access.
- **FR-005**: System MUST store the admin password in a form that cannot be reversed to recover the original password, even by someone with direct access to the stored data.
- **FR-006**: System MUST issue proof of a successful login that the frontend then presents on subsequent requests, so the operator is not asked to re-enter credentials for every action within a valid session.
- **FR-007**: System MUST cause that proof of login to stop being valid after a bounded period of time, requiring the operator to log in again.
- **FR-008**: System MUST allow the operator to explicitly end their session (log out) such that the credential they were using no longer grants access afterward.
- **FR-009**: Frontend MUST show a login screen instead of any dashboard content whenever there is no valid session, and MUST NOT reveal financial data or send data-bearing requests before a valid session exists.
- **FR-010**: Frontend MUST detect a rejected or expired credential on any backend call and return the operator to the login screen instead of displaying that call's failure as an error within the dashboard.
- **FR-011**: System MUST let the logged-in operator change their password by re-confirming their current password.
- **FR-012**: System MUST give the operator a clear, generic error when login fails (wrong username or wrong password), without indicating which of the two was incorrect.

### Key Entities

- **Admin Account**: The single credential set protecting the instance — a username and a hashed password. Exactly one exists per instance, created once during first-run setup.
- **Session**: The proof of a successful login that the frontend holds and presents on each request. Has a bounded lifetime and can be ended early by explicit logout.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An unauthenticated request to any data-bearing endpoint is rejected 100% of the time — no financial data is ever readable without a valid credential.
- **SC-002**: A new operator can complete first-run setup and reach a usable dashboard in under one minute.
- **SC-003**: A returning operator can log in and reach a usable dashboard in under 15 seconds on a correct first attempt.
- **SC-004**: 100% of expired- or invalid-session responses result in the operator seeing the login screen, never a raw error or broken dashboard state.
- **SC-005**: Stored passwords remain unrecoverable even given full access to the underlying data store (verifiable by inspection: no plaintext or reversibly-encoded password is ever persisted).

## Assumptions

- Authentication is a single shared admin credential, not a multi-user or role-based system — matches this app's established single-operator, self-hosted trust model (see the device-sync feature's same assumption).
- Login is verified with a username and password issued by the app itself; no external identity provider (OAuth/SSO) is in scope, consistent with the "data never leaves the operator's devices" model raised when deployment options were discussed.
- Session proof is a bearer token (not a server-side session cookie), chosen so a future local-data mobile app can authenticate against the same backend the same way.
- Session lifetime is a single bounded expiry (on the order of a working day) with no silent background refresh in v1 — when it lapses, the operator simply logs in again. This can be revisited later if it proves too disruptive.
- No account lockout or login-attempt throttling in v1: a single-operator, typically home-network-only deployment has a very different threat profile than a multi-tenant public service, and adding throttling now would be premature hardening ahead of real deployment feedback.
- Password recovery when the operator is fully locked out is a server-side/operational procedure (e.g. a documented manual reset), not an in-app "forgot password" flow — the app has no outbound email/SMS capability and adding one solely for password reset is out of scope for v1.
- This feature protects the existing single Passbook instance; it does not introduce multi-tenancy, per-user data isolation, or any concept of "which user owns this transaction" — all data remains instance-wide, exactly as today.
