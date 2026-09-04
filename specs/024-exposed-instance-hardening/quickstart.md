# Phase 1 Quickstart: Exposed Instance Hardening

**Feature**: 024-exposed-instance-hardening | **Date**: 2026-09-04

Validation scenarios, each mapped to a user story and marked **runs locally** or **CI-verified**.
No Docker daemon in the development environment, as in 021–023.

This feature is unusually favourable for local testing. Its hard parts — when to refuse, when to
stop refusing, and what not to count — are pure decisions over a clock, and all of them run here,
provided the throttle takes `Instant now` as a parameter rather than reading the clock (research
R1). If it reads the clock, scenario 3 needs a fifteen-minute test and nobody will keep it.

---

## Scenario 1 — Guessing stops being free (US1, FR-001, FR-002)

**Setup**: threshold 5, window 15 minutes, one caller.

```
5 wrong passwords   → each refused normally (401)
6th attempt         → refused without being checked (429)
```

**Expected**: the sixth attempt never reaches password verification. Asserting *that* — not just the
status code — is the point: a 429 returned after hashing the password still leaks timing (R5).

**Status**: **runs locally** as a Domain test.

---

## Scenario 2 — The operator's own typo is not a trap (US1, FR-004)

```
4 wrong passwords, then the correct one → accepted, count cleared
4 more wrong passwords                  → still not refused
```

**Expected**: success resets the counter to zero. Without this, an operator who mistypes twice this
morning and three times this afternoon is refused for reasons they cannot possibly connect.

**Status**: **runs locally**.

---

## Scenario 3 — The refusal ends by itself (US1, FR-003, SC-002)

```
6 wrong passwords at 09:00        → refusing
correct password at 09:10         → still refused
correct password at 09:16         → accepted
```

**Expected**: no operator action, no endpoint, no database edit. **The most important scenario in
the feature**, because the failure it guards against — a lockout with no way back — is worse than
the attack the throttle exists to prevent. This app has one account and no reset email.

**Status**: **runs locally**, with a controlled clock.

---

## Scenario 4 — A relentless attacker cannot make it permanent (US1, edge case)

```
6 wrong passwords at 09:00                       → refusing until 09:15
wrong passwords every second from 09:00 to 09:14 → still refusing until 09:15, NOT later
correct password at 09:16                        → accepted
```

**Expected**: attempts made during a refusal are refused and otherwise ignored — not counted, not
extending the window. Without this rule the window renews forever and the operator never gets back
in, which is scenario 3's failure arriving by a different route.

**Status**: **runs locally**. Write it immediately after scenario 3; they are the same requirement
seen from two sides.

---

## Scenario 5 — A refusal reveals nothing about the account (US1, FR-005)

```
refusal in force, username "admin"        (exists)     → 429
refusal in force, username "notarealuser" (does not)   → 429, indistinguishable
```

**Expected**: identical status, identical body. The requirement is that the response must not reveal
whether the *account name* exists — not that throttling must be hidden. A 429 for one and a 401 for
the other would be the violation.

**Status**: **runs locally** for the decision; **CI-verified** end to end.

---

## Scenario 6 — Rotating the caller does not reset the ceiling (US1, R2)

```
5 failures from caller A → A refused
5 failures from caller B → B refused; A still refused
...continue past the instance-wide threshold → all callers refused
```

**Expected**: the per-caller tier does not silently become the only tier. Note what this scenario
also demonstrates: an attacker *can* deny the operator access this way. That is a known and accepted
residual risk (research R2), not a bug to be surprised by later.

**Status**: **runs locally**.

---

## Scenario 7 — The forwarded address is not attacker-chosen (US1, R3)

```
request with X-Forwarded-For: "1.2.3.4"           → key is the peer, not 1.2.3.4
request through the proxy, XFF "1.2.3.4, 10.0.0.9" → key is 10.0.0.9 (rightmost)
```

**Expected**: the rightmost entry wins. Taking the leftmost is the natural-looking choice and would
hand an attacker a fresh bucket per request — the per-caller tier would appear to work in every test
that did not send the header, and protect nothing in production.

**Status**: **runs locally** for the resolution rule; **CI-verified** through the real proxy.

---

## Scenario 8 — A password too short to matter is refused when set (US2, FR-009)

```
POST /auth/setup            password "hunter2"        → refused, message states the minimum
POST /auth/setup            password "correct-horse-battery" → account created
POST /auth/change-password  newPassword "short"       → refused, current password still works
```

**Status**: **runs locally** for the rule; **CI-verified** for the endpoints.

---

## Scenario 9 — An existing short password still works (US2, FR-010, SC-004)

**Setup**: an account created before this feature, password "abc".

```
POST /auth/login  password "abc"  → accepted
```

**Expected**: the minimum constrains setting a password, never using one. An upgrade that locked
operators out of their own instances would be a far worse bug than the one this feature fixes.

**Status**: **runs locally**, as a Domain test on `AuthenticateServiceImpl` — changed from
CI-verified during implementation. The integration version would have had to overwrite the shared
admin account's password hash mid-suite and restore it afterwards; if it failed in between it would
break every test class that ran after it. The rule being tested lives in Domain (authentication
must not consult the policy at all), so that is where it is asserted.

---

## Scenario 10 — A deployment does not publish its API surface (US3, FR-013, FR-014)

```
deployed configuration:
  GET /swagger-ui/index.html   → not served
  GET /v3/api-docs             → not served
  GET /auth/status             → 200, exactly as before
  GET /bills without a token   → 401, exactly as before
```

**Expected**: the last two lines are the ones that matter. Turning documentation off must not have
moved a single authorization boundary — which is why `SecurityConfig` is not edited at all
(research R7).

**Status**: **CI-verified**.

---

## Scenario 11 — Setup is not throttled (US1, FR-008)

```
first-run instance, several malformed setup attempts → not refused
valid setup                                          → succeeds
```

**Expected**: setup has no account to protect and refuses itself permanently after one success.
Throttling it would let a stranger make a fresh instance unclaimable by its own operator — creating
the very lockout this feature is built to avoid.

**Status**: **runs locally**.

---

## Scenario 12 — The deployment guide states the order (US4, FR-016, FR-017)

**Expected**: `docs/DEPLOYING.md` gives "complete setup, then publish the port" as a numbered step
in the procedure, and says what happens if the order is reversed — another party owns the instance
and everything later put into it. Today that risk is a bullet in a warnings section, which is read
after the steps or not at all.

**Status**: reviewed by reading, not executed.

---

## What runs where

| Runs locally | Needs CI |
|---|---|
| When a refusal starts | A refusal holding across real HTTP requests |
| When it ends, unattended | The real proxy's forwarded header |
| That attempts during a refusal change nothing | A deployment serving no API description |
| Success clearing the count | Every authorization boundary unmoved |
| Both tiers, and their interaction | The 429 being identical for a real and an unknown username |
| Resolving the caller from a header | |
| The password rule itself | |
| That authentication ignores the password rule | |

Report the two columns separately. A green local build is evidence about the left column only —
though here, as in 023, the left column holds most of what can actually go wrong.
