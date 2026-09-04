# Feature Specification: Exposed Instance Hardening

**Feature Branch**: `claude/project-status-s0au7m`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "024-exposed-instance-hardening: Make Passbook safe to leave on a URL that strangers can reach..."

## Context

Feature 023 shipped a deployable stack — a compose file, a devcontainer, and a deployment guide —
which made an internet-reachable Passbook instance easy to create. That changed who can reach the
login page. Until then the constitution's authentication gate was protecting an instance on a
trusted network; now it is the only thing between a stranger and someone's entire financial
history.

The four gaps below were found by reading the deployed surface, not inferred from a checklist. Each
is a real, current property of the running application.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Guessing the password stops being free (Priority: P1)

Someone who finds the instance's URL tries password after password against the login form. Today
nothing counts, slows or refuses them: they can try as fast as the network allows, indefinitely,
and the operator has no way to know it is happening. After this feature, repeated failures stop
being answered, and the attacker's cost stops being zero.

The operator must never be the casualty. This app has one account and no password-reset email — if
a lockout could be permanent, the app would have converted a possible break-in into a certain loss
of the operator's own records. Any refusal therefore expires by itself.

**Why this priority**: It is the only item here that turns an exposed instance from *attackable* to
*expensively attackable*. Everything else in this feature reduces the odds; this one changes the
economics.

**Independent Test**: Make repeated failed logins against a running instance and confirm they stop
being accepted; wait out the window and confirm the correct password works again. Delivers value
alone — the other three stories can ship later or not at all.

**Acceptance Scenarios**:

1. **Given** a fresh instance, **When** wrong passwords are submitted repeatedly past the
   threshold, **Then** further attempts are refused without being checked against the real
   password.
2. **Given** attempts have been refused, **When** the refusal window has passed, **Then** the
   correct password is accepted again with no operator intervention and no manual reset.
3. **Given** several failures below the threshold, **When** the correct password is submitted,
   **Then** it is accepted and the failure count is cleared, so an operator who mistypes twice is
   not one typo away from being locked out later.
4. **Given** a refusal is in force, **When** any username is submitted — one that exists and one
   that does not — **Then** the responses are indistinguishable, so probing cannot enumerate
   whether an account name is real.
5. **Given** an attacker is being refused, **When** the operator is looking at the instance,
   **Then** the attempts are visible to them in the instance's own record of events, rather than
   silently absorbed.

---

### User Story 2 - A password long enough to be worth guarding (Priority: P2)

The operator sets their password during first-run setup, or changes it later. Today the app accepts
a single character. On a URL strangers can reach, that is a door with a one-digit combination. After
this feature the app refuses to *accept* a password too short to matter, at the moment it is set.

Logging in is deliberately not constrained: an existing password's length is not the API's business,
and rejecting a short one before checking it would tell an attacker the password is short.

**Why this priority**: It multiplies User Story 1 — a throttle protects a strong password well and a
one-character password barely at all. It is second because a determined operator can already choose
a good password; this only removes the option of not doing so.

**Independent Test**: Attempt setup with a too-short password and confirm refusal with a message
saying why; complete setup with an adequate one; confirm an existing account with a short password
can still log in.

**Acceptance Scenarios**:

1. **Given** an instance with no admin account, **When** setup is submitted with a password below
   the minimum, **Then** it is refused, nothing is created, and the message states the requirement.
2. **Given** an instance with no admin account, **When** setup is submitted with an adequate
   password, **Then** the account is created and the operator is signed in.
3. **Given** an existing account whose password predates this rule, **When** the operator logs in
   with it, **Then** they are signed in — the rule constrains setting a password, never using one.
4. **Given** an existing account, **When** a password change is submitted with a new password below
   the minimum, **Then** it is refused and the current password remains in force.

---

### User Story 3 - A deployment stops publishing its own API surface (Priority: P3)

The interactive API browser and the machine-readable API description are currently served to anyone
who reaches the instance, without a login. The contract is already public in this project's
repository, so this is disclosure rather than vulnerability — but a deployed instance has no reason
to serve it, and an operator has no way to turn it off short of editing the app's security rules.

After this feature, whether those are served is a deployment setting. Turning it off must not change
who can reach anything else: an operator hardening their instance must not be able to lock
themselves out of it, or silently open something, by flipping this.

**Why this priority**: Real but modest. It narrows what a stranger learns for free; it does not
change what they can do.

**Independent Test**: Start an instance with the setting off and confirm the API browser and
description are not served, while every other endpoint behaves exactly as before — including which
ones need a login.

**Acceptance Scenarios**:

1. **Given** a deployment with the setting off, **When** anyone requests the API browser or the API
   description, **Then** neither is served.
2. **Given** a deployment with the setting off, **When** the login, setup and status endpoints are
   requested without credentials, **Then** they behave exactly as they did before.
3. **Given** a deployment with the setting off, **When** any data endpoint is requested without
   credentials, **Then** it is still refused — turning documentation off has not opened anything.
4. **Given** a development instance, **When** it starts with default settings, **Then** the API
   browser is served as it is today.

---

### User Story 4 - Nobody else claims the instance first (Priority: P3)

First-run setup is open until it succeeds once — by necessity, since there is no account yet to
authorise it. On a public URL, that means whoever loads the page first becomes the owner of that
instance and everything later put into it. An operator who publishes the URL and then goes to make
coffee can return to an instance that is no longer theirs.

There is a mitigation that costs nothing and that nobody would think of unaided: reach the instance
privately, complete setup, and only then publish the URL. The deployment documentation must give
that as an ordered step, not as a warning beside the steps.

**Why this priority**: The window is real and the consequence is total, but it is minutes long and
closed entirely by ordering — no code changes hands.

**Independent Test**: Follow the deployment guide as written and confirm the instance is never
reachable by anyone else while setup is still open.

**Acceptance Scenarios**:

1. **Given** the deployment guide, **When** an operator follows it in order, **Then** setup is
   completed before the instance is made reachable by anyone else.
2. **Given** setup has been completed, **When** anyone attempts setup again, **Then** it is refused
   — as it is today.

---

### Edge Cases

- **The operator is the one being refused.** They mistype past the threshold and are now locked out
  of their own records. The refusal must expire on its own; there must be no state that only a
  developer can clear, and no path where an operator's only option is deleting their database.
- **An attacker keeps trying during the refusal window.** Continued attempts must not extend the
  window indefinitely into a permanent lockout of the real operator.
- **Attempts come from many sources at once.** A refusal keyed only to where a request appears to
  come from is trivially evaded; one keyed only to the account name lets anyone lock the operator
  out at will. The design must not make either failure the whole story.
- **The instance restarts mid-attack.** Restarting must not be a way to clear the refusal
  instantly — but neither may a restart lock out an operator who was not being refused.
- **An operator upgrades with a one-character password already set.** They must not be locked out;
  they should be told, once, that it no longer meets the rule.
- **Setup is attempted while a refusal is in force.** Setup is not login, and this feature must not
  accidentally make a first-run instance unclaimable by its own operator.

## Requirements *(mandatory)*

### Functional Requirements

**Throttling authentication attempts (US1)**

- **FR-001**: The system MUST count consecutive failed authentication attempts.
- **FR-002**: The system MUST refuse further attempts once the count passes a threshold, without
  evaluating the submitted credentials.
- **FR-003**: A refusal MUST expire on its own after a bounded period, with no operator action, no
  administrative endpoint, and no database edit required to recover.
- **FR-004**: A successful authentication MUST clear the failure count.
- **FR-005**: The response to a refused attempt MUST NOT reveal whether the submitted account name
  exists, and MUST NOT differ observably from an ordinary failure in a way that distinguishes the
  two cases for an attacker.
- **FR-006**: Refused attempts MUST be recorded in the instance's own event record, so an operator
  can see that it happened.
- **FR-007**: The threshold and the refusal period MUST be configurable by the operator without
  rebuilding the application, and MUST have defaults that are safe on a public URL.
- **FR-008**: Throttling MUST NOT apply to first-run setup, which has no account to protect and
  refuses itself permanently after one success.

**Password strength at the point it is set (US2)**

- **FR-009**: The system MUST refuse a new password below a minimum length when an account is
  created or its password changed.
- **FR-010**: The system MUST NOT apply the minimum to authentication, so that an existing shorter
  password continues to work and no response reveals a password's length.
- **FR-011**: A refusal MUST state the requirement, so the operator knows what to do rather than
  guessing.
- **FR-012**: The published API description MUST express the new minimum, so the constraint is
  discoverable rather than only enforced.

**Not publishing the API surface from a deployment (US3)**

- **FR-013**: Whether the interactive API browser and the machine-readable API description are
  served MUST be an operator setting, not a code change.
- **FR-014**: Changing that setting MUST NOT change which endpoints require authentication, nor
  which are reachable without it.
- **FR-015**: The deployed configuration MUST default to not serving them; development MUST default
  to serving them.

**Closing the setup window (US4)**

- **FR-016**: The deployment documentation MUST present "complete setup before making the instance
  reachable by others" as an ordered step in the deployment procedure.
- **FR-017**: The documentation MUST state what is at stake if the order is reversed — that another
  party becomes the owner of the instance and its data.

**Honesty about what remains**

- **FR-018**: The project's security documentation MUST be updated so that the limitations this
  feature closes are no longer listed as accepted, and anything it deliberately does not close
  remains listed.

### Key Entities

- **Authentication attempt record**: the running count of consecutive failures and when a refusal,
  if any, expires. Ephemeral by nature — it describes a moment, not a fact about the operator's
  finances, and nothing about the ledger depends on it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Guessing passwords against an exposed instance is bounded: after a small number of
  consecutive failures no further guess is evaluated until a waiting period has passed, making
  exhaustive guessing impractical rather than merely slow.
- **SC-002**: An operator who locks themselves out regains access by waiting, within minutes,
  without help, without a support channel, and without touching their database. There is no state
  in the system that only its developer could clear.
- **SC-003**: An account name cannot be confirmed or ruled out by observing responses.
- **SC-004**: An instance cannot be created with a password shorter than the minimum, and every
  account that existed before this feature still works.
- **SC-005**: A deployed instance serves no API browser and no API description to an anonymous
  visitor, while every endpoint's authentication requirement is unchanged from before.
- **SC-006**: An operator following the deployment guide in order never has an unclaimed instance
  reachable by anyone else.
- **SC-007**: The security documentation lists no limitation this feature has closed, and still
  lists every one it has not.

## Assumptions

- **One account, one operator.** Passbook has a single shared credential by design (constitution,
  Deliberately Out of Scope). "Lock out the account" and "lock out the operator" are therefore the
  same event, which is why every rule here is written around recoverability rather than strictness.
- **No out-of-band recovery exists.** There is no password-reset email, no second factor and no
  recovery code, and none is being added. A permanent lockout would have no remedy, so none may be
  reachable.
- **Attempt state need not survive a restart.** Losing the count on restart costs an attacker a
  restart they cannot cause; persisting it would put a security control in the operator's financial
  database and into their backups, which is a worse trade. Restarts are assumed to be rare and not
  attacker-triggerable.
- **The threshold is a default, not a doctrine.** Operators differ in how often they mistype and how
  exposed their instance is, which is why FR-007 makes it configurable.
- **TLS is terminated in front of the app.** As documented for deployment; this feature does not
  add transport security and does not assume it is absent either.
- **The API contract is already public.** This project's OpenAPI YAML is in a public repository, so
  not serving it from an instance narrows convenience for an attacker, not their knowledge.
- **The single existing client is updated with the contract.** The password minimum tightens the
  contract rather than extending it (Principle VII). It is justified because the alternative is
  shipping a one-character admin password to the internet, and the practical blast radius is one
  frontend in this repository.

## Out of Scope

Deliberately excluded, each because it trades more than it buys for a single-user self-hosted app:

- **Moving the session token out of browser storage.** That means server-set cookies and the
  cross-site request forgery handling that follows — a redesign of how sessions work, not a
  hardening of what exists. It stays a known and accepted limitation.
- **Multi-factor authentication** and **multiple user accounts** — both excluded by the
  constitution.
- **IP allowlisting**, and **any external dependency** such as a log-scanning ban daemon or a web
  application firewall. An instance must remain something an operator starts with one command and
  no separately-run services.
- **Alerting the operator about attacks** beyond the instance's own event record. Notification
  needs a channel to notify over, which this project does not have.
