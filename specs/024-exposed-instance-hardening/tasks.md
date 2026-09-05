---
description: "Task list for 024-exposed-instance-hardening"
---

# Tasks: Exposed Instance Hardening

**Input**: Design documents from `/specs/024-exposed-instance-hardening/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included and non-optional. Principle VI is NON-NEGOTIABLE, and this feature's rules are
business rules. The Domain tests are also the only ones that run in this environment.

**Organization**: By user story, so each can be implemented, tested and shipped on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md
- **Runs locally / CI-only**: no Docker daemon in development. Say which, always.

## Path Conventions

Hexagonal Maven modules plus a React SPA, per plan.md. Domain holds the rules; Application holds
anything that knows what HTTP is.

---

## Phase 1: Setup

**Purpose**: There is none.

No new dependency (research R4), no new module, no migration (data-model.md). The last applied
migration stays `V4`. This phase exists to say so: if a task here seems to be missing, it is
because this feature deliberately adds nothing to install.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The contract, and the code generation that follows from it. Everything else depends on
the generated models carrying the new constraint.

- [X] T001 Add a `"429"` response to `/auth/login` in `Application/src/main/resources/swagger/auth/auth-api-controller.yaml`, with the description from `contracts/auth-contract-delta.md`. Additive — no existing response changes.
- [X] T002 Change `minLength` from 1 to 12 on `setupRequest.password` and `changePasswordRequest.newPassword` in `Application/src/main/resources/swagger/auth/auth-model.yaml`. **Do not touch `loginRequest`** — it carries no minimum today and must keep carrying none (FR-010, research R6). Constraining it would reject a short password before checking it, which tells the caller the stored password is short, and would lock out every account created before this rule.
- [X] T003 Run `./mvnw -pl Application clean generate-sources` and confirm the generated `SetupRequest` and `ChangePasswordRequest` carry `@Size(min = 12)` while `LoginRequest` carries no size constraint. `skipOverwrite=true` means `clean` is required, not optional.
- [X] T004 Run `./mvnw clean install -pl '!integration-tests'` and confirm green. Nothing has changed behaviourally yet; this is the baseline the rest is measured against.

**Checkpoint**: The contract states the new rule. No behaviour has changed.

---

## Phase 3: User Story 1 — Guessing the password stops being free (P1)

**Goal**: Repeated failed logins stop being answered, and stop being free.

**Independent test**: Make repeated failed logins against a running instance, confirm they stop
being accepted, wait out the window, confirm the correct password works again with no intervention.

**This phase is the feature.** US2–US4 reduce the odds; this one changes the economics.

### Tests first (Principle VI)

- [X] T005 [P] [US1] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/auth/LoginThrottleTest.java` covering quickstart scenarios 1, 2, 3, 4, 6 and 11 **before** T006 exists: the threshold refuses; a success clears the count; a refusal expires by the clock alone; **attempts during a refusal neither count nor extend the window**; the instance-wide tier catches what the per-caller tier does not; setup is never throttled. **Runs locally — write this first.** Scenarios 3 and 4 are the two a correct-looking implementation gets wrong, and they are the difference between a lockout that ends and one that does not.

### Domain

- [X] T006 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/LoginThrottle.java`: two tiers per research R2 (per-caller, low threshold; instance-wide, high threshold), a bounded map with expiry-based eviction (research R4 — the per-caller key is attacker-chosen, so an unbounded map is a memory-exhaustion vector this feature would have introduced itself), and **`Instant now` as a method parameter, never a clock read inside** (research R1). Reading the clock internally makes quickstart scenario 3 a fifteen-minute test that nobody keeps.
- [X] T007 [US1] Create `Domain/src/main/java/at/ymeri/my/finance/domain/data/auth/ThrottleDecision.java` (or an equivalent return type) carrying whether to refuse and, when refusing, which tier tripped — the controller needs the first for its response and the log needs the second.

### Application

- [X] T008 [P] [US1] Create `Application/src/main/java/at/ymeri/my/finance/security/ClientAddressResolver.java`: the **rightmost** entry of `X-Forwarded-For`, falling back to the socket remote address when the header is absent. **Rightmost, not leftmost** (research R3) — Caddy appends the real peer to whatever arrived, so the leftmost entry is attacker-chosen. Taking it would hand an attacker a fresh bucket per request, pass every test that does not send the header, and protect nothing in production.
- [X] T009 [P] [US1] Write `Application/src/test/java/at/ymeri/my/finance/security/ClientAddressResolverTest.java` for quickstart scenario 7: a header-only value is ignored in favour of the peer; a proxied `"1.2.3.4, 10.0.0.9"` resolves to `10.0.0.9`; an absent header falls back. **Runs locally.**
- [X] T010 [US1] Wire the throttle into `Application/src/main/java/at/ymeri/my/finance/controller/auth/AuthController.java#login`: resolve the caller, ask the throttle, and **return 429 before the credentials are examined at all**. A refusal that still runs the password hash is a timing oracle (research R5) — put the reason in a comment where the ordering lives, because it is exactly what a later refactor loses. Record failures and successes back to the throttle.
- [X] T011 [US1] Log a refusal at WARN with the resolved caller, the consecutive-failure count and the tier, per research R8. No password in any form. Principle V's masking rule covers account identifiers and amounts, neither of which appears.
- [X] T012 [US1] Add throttle settings to `Launcher/src/main/resources/application.properties` with the defaults from data-model.md §2 (per-caller 5, instance-wide 20, window 15 minutes, enabled true). FR-007 — an operator must be able to move these without rebuilding.
- [X] T013 [US1] Set the throttle off in `integration-tests/src/test/resources/application.yaml`, **in this same task**. Every integration test boots the full app against a shared instance; a live throttle would let one test's failed-login assertions refuse another test's login, and it would surface as unrelated features failing authentication for no visible reason. This is the same trap feature 023 hit with its scheduler.
- [X] T014 [US1] Integration test `integration-tests/src/test/java/at/ymeri/my/finance/integration/tests/LoginThrottleIntegrationTest.java` with the throttle explicitly enabled for this class only: a refusal holds across real HTTP requests, and a refused attempt returns 429 identically for a real and an unknown username (quickstart 5). **CI-only.**

**Checkpoint**: Guessing is bounded. US1 ships alone if the rest slips.

---

## Phase 4: User Story 2 — A password worth guarding (P2)

**Goal**: The app refuses to accept a password too short to matter, at the moment it is set.

**Independent test**: Setup with a short password is refused with a message saying why; setup with
an adequate one succeeds; an account created before the rule still logs in.

- [X] T015 [P] [US2] Write `Domain/src/test/java/at/ymeri/my/finance/domain/service/auth/PasswordPolicyTest.java` for quickstart scenario 8: below the minimum is refused, at and above is accepted, and the refusal names the requirement. **Runs locally.**
- [X] T016 [US2] Create `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/PasswordPolicy.java` — the minimum, with the reasoning in a comment: 12 rather than NIST's floor of 8 because this is one credential, chosen once, protecting everything, on a URL strangers can reach.
- [X] T017 [P] [US2] Enforce it in `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/SetupAdminAccountServiceImpl.java`, rejecting with a message that states the requirement (FR-011).
- [X] T018 [P] [US2] Enforce it in `Domain/src/main/java/at/ymeri/my/finance/domain/service/auth/ChangePasswordServiceImpl.java`, same message. On refusal the current password must remain in force.
- [X] T019 [US2] Confirm `AuthenticateServiceImpl` is **not** touched. The rule constrains setting a password, never using one — an existing short password must keep working (FR-010, SC-004). If a task appears to require a change there, that task is wrong.
- [X] T020 [US2] Update the existing `SetupAdminAccountServiceImplTest` and `ChangePasswordServiceImplTest` in `Domain/src/test/.../auth/` — their fixtures almost certainly use short passwords and will now fail. Failing tests here are the feature working; fix the fixtures, do not weaken the rule. **Runs locally.**
- [X] T021 [US2] Integration test in `integration-tests/src/test/java/.../AuthControllerIntegrationTest.java` (or a new class): an account created with a short password directly in the database still authenticates, proving the rule never reaches login (quickstart 9). **CI-only.**

**Checkpoint**: A new instance cannot be created with a weak password; every old one still works.

---

## Phase 5: User Story 3 — A deployment stops publishing its API surface (P3)

**Goal**: The API browser and description are a deployment setting, not a code change.

**Independent test**: An instance with the setting off serves neither, while every endpoint's
authentication requirement is exactly what it was.

- [X] T022 [US3] Add `-Dspringdoc.api-docs.enabled=false -Dspringdoc.swagger-ui.enabled=false` to `APP_OPTS` in `docker-compose.deploy.yaml`. The development `docker-compose.yaml` and `application.properties` keep the default, which is on (FR-015).
- [X] T023 [US3] **Do not edit `Application/src/main/java/at/ymeri/my/finance/security/SecurityConfig.java`.** With springdoc disabled its `permitAll` entries for `/swagger-ui/**` and `/v3/api-docs/**` become inert — they permit routes that no longer exist. Leaving them means an operator hardening their instance is not editing the authorization rules at all, which is precisely FR-014. This task is a verification that the diff for this story touches no security rule.
- [X] T024 [US3] Integration test for quickstart scenario 10: with the setting off, neither path is served, **and** `/auth/status` still answers publicly while `/bills` without a token is still refused. The last two assertions are the ones that matter — they prove no authorization boundary moved. **CI-only.**

**Checkpoint**: A deployed instance tells an anonymous visitor nothing it does not have to.

---

## Phase 6: User Story 4 — Nobody else claims the instance first (P3)

**Goal**: The deployment guide gives the safe order as a step, not as a warning beside the steps.

**Independent test**: Follow `docs/DEPLOYING.md` in order; the instance is never reachable by anyone
else while setup is still open.

- [X] T025 [US4] Rewrite the Codespaces walkthrough in `docs/DEPLOYING.md` so completing setup comes **before** making the port public, as a numbered step (FR-016). Today "complete setup immediately" sits in a warnings section below the steps, which is read afterwards or not at all — and the mitigation only works if it is done in order.
- [X] T026 [US4] State the stake in the same place (FR-017): if the order is reversed, another party becomes the owner of the instance and everything later put into it. Not a caveat — the consequence is total and the window is minutes.
- [X] T027 [P] [US4] Apply the same ordering to the "Anywhere else with Docker" section of `docs/DEPLOYING.md`, where the equivalent step is binding the port publicly.

**Checkpoint**: An operator following the guide cannot lose their instance to a stranger.

---

## Phase 7: Polish & Cross-Cutting

- [X] T028 [P] Surface a 429 distinctly in `frontend/src/api/client.ts` and `frontend/src/components/LoginPage.tsx` — "too many attempts, try again shortly", never "incorrect username or password". Today a 429 would render as a wrong-password message, which tells an operator who is being attacked exactly the wrong thing.
- [X] T029 [P] State the 12-character minimum in `frontend/src/components/SetupPage.tsx` and `frontend/src/components/ChangePasswordDialog.tsx` **before** submission, so the operator meets the rule as guidance rather than as a rejection.
- [X] T030 Update `SECURITY.md`: remove from Known and Accepted Limitations anything this feature closes, and **keep** what it does not — the session token still lives in browser storage, and that stays listed (FR-018, SC-007). A limitations list that quietly drops what is still true is worse than none.
- [X] T031 Add a `CHANGELOG.md` entry under `[Unreleased]`: failed logins are now refused after repeated failures and recover on their own; new passwords need 12 characters; a deployment no longer serves its API browser. Under operator action, note that **an existing short password keeps working but no longer meets the rule**, and that a locked-out operator waits rather than doing anything.
- [X] T032 Update the throttle defaults in `docs/DEPLOYING.md`'s "Before you put it on the internet" section, and replace the standing advice to complete setup immediately with a pointer to the now-ordered steps.
- [X] T033 Run `./mvnw clean install -pl '!integration-tests'`, `./mvnw -pl integration-tests test-compile`, and `cd frontend && npm run build`. Report the local result and the CI dependency **separately**.
- [X] T034 Walk `quickstart.md` against what was delivered and correct any drift, particularly that the locally-executable scenarios were actually executed and that scenario 4's no-extension rule is genuinely asserted rather than assumed.
- [X] T035 Mark completed tasks `[X]` in this file, add an Implementation Outcome section recording any divergence, then commit and push to `claude/project-status-s0au7m`.

---

## Dependencies

```
Phase 2 (T001–T004)  ─── blocks everything: the generated models carry the constraint
      │
      ├─► Phase 3 US1 (T005–T014)   the feature; ships alone
      ├─► Phase 4 US2 (T015–T021)   needs T003's generated @Size
      ├─► Phase 5 US3 (T022–T024)   independent of US1 and US2
      └─► Phase 6 US4 (T025–T027)   documentation only; independent of all code
                    │
                    └─► Phase 7 Polish (T028–T035)
```

US3 and US4 depend on nothing in US1 or US2 and can be done in any order, or first, or by someone
else. US4 needs no code at all.

## Parallel Execution

Within US1, after T005 is written:

```bash
Task: "Create LoginThrottle in Domain/src/main/java/.../service/auth/LoginThrottle.java"      # T006
Task: "Create ClientAddressResolver in Application/src/main/java/.../security/"                # T008
Task: "Write ClientAddressResolverTest in Application/src/test/java/.../security/"             # T009
```

Within US2:

```bash
Task: "Enforce PasswordPolicy in SetupAdminAccountServiceImpl"                                  # T017
Task: "Enforce PasswordPolicy in ChangePasswordServiceImpl"                                     # T018
```

Polish T028 and T029 touch different frontend files and run together.

## Implementation Strategy

### The MVP is US1 alone

US1 is the only story that changes what an attacker can afford. US2 multiplies it, US3 narrows
disclosure, US4 costs no code. Ship US1 first and the instance is meaningfully safer even if the
rest waits.

1. Phase 2 — the contract, then regenerate
2. Phase 3 — **write T005 before T006**; scenarios 3 and 4 are the whole risk surface and need no
   database
3. Phase 4 — expect T020 to fail before you fix it; that is the rule working
4. Phases 5 and 6 — either order, any time
5. Phase 7 — polish, then report local and CI separately

### Notes

- **Commit per phase.** T006 and its test deserve their own commit and a careful read.
- **No migration.** If a task seems to want one, re-read data-model.md — nothing here is persisted,
  deliberately.
- **`SecurityConfig` is not edited by this feature** (T023). A task that wants to is wrong.
- Do not report a Docker-dependent task as locally verified. Say what ran, what did not, and why.

---

## Implementation Outcome

All 35 tasks completed.

### The two bugs the tests caught before the code shipped

T005 was written before T006, as the plan insisted, and **both of the scenarios it flagged as the
risk surface failed on the first run.** Neither was a test error.

1. **A refusal that had served its window did not clear the count.** After waiting out fifteen
   minutes the operator was refused again on their very next typo — and on every typo after that,
   forever, because the count only ever went up. `Attempts.isSpent` now treats a served refusal as
   a clean slate, which is what quickstart scenario 3's third assertion is for.

2. **The instance-wide tier was still counting attempts that were already being refused.** A single
   attacker from one address, refused after five failures, went on feeding the global counter until
   it tripped too — locking out every caller including the operator, from one address, with no
   distribution required. The per-tier version of "don't count during a refusal" is not enough:
   `recordFailure` now returns immediately if the caller is refused at all. An attempt that was
   never evaluated is not a failed authentication, and counting it is counting our own refusal.

Both would have shipped looking correct. Both are the permanent-lockout failure the whole feature
is shaped to avoid, arriving by two different routes.

### Divergences

1. **T021 moved from an integration test to a Domain test.** As written it needed to overwrite the
   shared admin account's password hash mid-suite and restore it afterwards; a failure in between
   would have broken every test class that ran later. The rule it proves — that authentication does
   not consult the password policy — lives in Domain, so it is asserted in
   `AuthenticateServiceImplTest` instead. Quickstart scenario 9 was updated to match.

2. **A new exception type, `WeakPasswordException`, which the plan did not anticipate.**
   `AuthController` already maps `IllegalArgumentException` to **401**, because the one place it
   was thrown is "your current password is wrong". A short new password reusing it would have told
   an operator with a perfectly valid session that they were unauthorized — which reads as having
   been logged out. It extends `IllegalArgumentException` so anything unaware still behaves
   sensibly, and has its own handler returning 400.

3. **`SecurityConfigTest` needed the two new beans.** It is a `@WebMvcTest` slice over
   `AuthController`, whose constructor grew, so the context stopped loading. `ClientAddressResolver`
   is imported and the throttle is supplied disabled: mocking the throttle instead would have let
   the test keep passing if the controller stopped consulting it altogether.

4. **The logger is declared, not generated.** `@Slf4j` produces nothing in Application: that module
   configures `annotationProcessorPaths` for MapStruct, which overrides default processor discovery,
   so Lombok never runs there. Hence the explicit `LoggerFactory` field, with a comment saying why.

5. **T023 was a verification and stayed one.** `SecurityConfig` is untouched by this feature, as
   `git status` confirms. Disabling springdoc leaves its `permitAll` entries permitting routes that
   no longer exist, which is exactly what FR-014 wanted.

### What ran where

**Locally verified** — `./mvnw clean install -pl '!integration-tests'`: BUILD SUCCESS, **353 Domain
tests** (13 for `LoginThrottle`, 6 for `PasswordPolicy`, plus the updated auth fixtures) and **9
Application tests** (6 for `ClientAddressResolver`, 3 for `SecurityConfig`). Plus
`./mvnw -pl integration-tests test-compile` and `cd frontend && npm run build`, both clean.

**CI-verified only** — no Docker daemon here, so neither `LoginThrottleIntegrationTest` nor
`ApiDocsDisabledIntegrationTest` has been executed: a refusal holding across real HTTP requests,
the 429 being byte-identical for a real and an unknown username, a deployment serving no API
description, and every authorization boundary being unmoved. They compile; whether they pass is
CI's answer, not this one.
