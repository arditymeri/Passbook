# Quickstart: Single-User Authentication

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`) against a
**fresh** database (no admin account yet).

## Scenario 1 — First-run setup (US1)

1. Open the app against a fresh database with no admin account configured.
2. **Expected**: a one-time setup screen is shown, not the dashboard (US1.1).
3. Complete setup with a chosen username and password.
4. **Expected**: the dashboard loads immediately — no separate login step required.
5. Open the app in a new private/incognito window (no stored session).
6. **Expected**: a login screen is shown, not the dashboard or the setup screen (US1.2).
7. Try navigating directly to whatever URL/route the setup screen used.
8. **Expected**: redirected to the login screen — setup cannot be repeated (US1.3).
9. With `curl`, call any data endpoint directly (e.g. `GET /api/v1/accounts`) with no
   `Authorization` header.
10. **Expected**: rejected (401), confirming there is no back door around the frontend (FR-003,
    Edge Cases).

## Scenario 2 — Log in and work without re-authenticating (US2)

1. From the login screen, enter the username/password chosen in Scenario 1.
2. **Expected**: taken to the dashboard (US2.1).
3. Browse the dashboard, add a bill, open a settings-style page (e.g. Categories), run a sync
   export — several unrelated actions in sequence.
4. **Expected**: none of them re-prompt for credentials (US2.3).
5. Log out, then try logging in with the correct username but a wrong password.
6. **Expected**: a clear "incorrect credentials" error, remaining on the login screen, not
   revealing whether the username or password was wrong (US2.2, FR-012).

## Scenario 3 — Session expires gracefully (US3)

1. Log in, then explicitly log out.
2. **Expected**: back at the login screen; the token used before logout no longer works (verify
   with `curl` + the old token against a data endpoint → 401) (US3.2, FR-008).
3. Log in again, then (e.g. via browser dev tools) corrupt or delete the stored token, or wait for
   it to expire.
4. Trigger any action that calls the backend (e.g. change month, open a page).
5. **Expected**: returned cleanly to the login screen — no broken dashboard, no raw error message
   shown anywhere in the UI (US3.1, FR-010).

## Scenario 4 — Change password (US4)

1. Log in, open the password-change action, submit the current password and a new one.
2. **Expected**: subsequent logins require the new password; the old password is rejected (US4.1).
3. Log in with the new password from a second browser/session first, then attempt a password
   change elsewhere using the *old* password as the "current password".
4. **Expected**: rejected, existing password unchanged (US4.2).
5. After a successful password change, check the session that made the change.
6. **Expected**: that session's own prior token is now also invalid — a password change ends every
   outstanding session, not just other ones (research.md R2).

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up` or a full stack against a real Postgres instance, consistent with every prior
feature (007-019). This walkthrough should be executed manually once implementation lands in an
environment with Docker available.
