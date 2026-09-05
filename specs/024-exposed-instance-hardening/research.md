# Phase 0 Research: Exposed Instance Hardening

**Feature**: 024-exposed-instance-hardening | **Date**: 2026-09-04

Eight decisions. Two of them (R2 and R3) are where this feature is either right or wrong; the rest
are mostly bookkeeping.

---

## R1 — Where the throttle lives

**Decision**: The counting and the decision are Domain (`service/auth/LoginThrottle`), pure and
exercisable in plain JUnit. Resolving *who is calling* is Application, because that is an HTTP
question. Application hands Domain an opaque key string and the current `Instant`.

**Rationale**: Principle VIII forbids Domain depending on HTTP. It also makes the interesting part —
"after five failures, refuse for fifteen minutes, unless the window has passed" — testable in
milliseconds with a controlled clock, exactly as feature 023 made `OccurrenceSchedule` testable by
taking `today` as a parameter. That pattern is established here; reuse it rather than inventing a
second one.

**Alternatives considered**: A servlet filter would catch every route rather than one. Rejected:
there is one route to protect, and a filter puts the logic where the delegate-pattern controllers
cannot reach it for testing. Putting the whole thing in `AuthController` was rejected for the
opposite reason — it would make the rules only testable through a running web context.

---

## R2 — What the attempt is counted against

**The question the spec refused to answer for us**, quoted from its Edge Cases: a counter keyed only
to apparent origin is trivially evaded, and one keyed only to the account name lets anyone lock the
operator out at will.

**This app makes that worse than usual.** There is one account. "Per-username" and "global" are the
same thing here, so any account-keyed counter is a lever a stranger can pull to lock the operator
out of their own financial history — and there is no reset email to undo it.

**Decision**: Two tiers.

| Tier | Key | Effect | Threshold |
|---|---|---|---|
| Per-caller | client address (see R3) | hard refusal | low (default 5) |
| Instance-wide | none — one counter | hard refusal | high (default 20) |

Plus one rule that matters more than either threshold: **attempts made while a refusal is in force
are refused but not counted.** Without it, an attacker who never stops extends the window forever
and the lockout becomes permanent — the exact outcome the spec forbids (FR-003, SC-002).

**Rationale**: The per-caller tier stops the ordinary case, where guesses come from one place. The
instance-wide tier is what a rotating-address attacker still runs into. Both expire unattended.

**Residual risk, stated rather than hidden**: a determined attacker distributing guesses across many
addresses can keep the instance-wide counter tripped and so keep the operator out for as long as
they care to continue. That is a denial of service, not a break-in, and the operator's recourse is
to stop publishing the URL. The alternative — no instance-wide tier — trades that for letting
distributed guessing run unbounded, which is worse for an app holding financial records. The
threshold is configurable (FR-007) precisely so an operator who disagrees can move it.

**Alternatives considered**:

- *Account-keyed only*: the classic answer, and the wrong one here, because with a single account it
  is a public button labelled "lock the owner out".
- *Address-keyed only*: no protection at all against the one attacker who thinks to rotate.
- *A fixed delay instead of a refusal for the instance-wide tier*: attractive — it caps guessing
  without ever locking anyone out. Rejected because a delay holds a request thread, and enough
  concurrent slow requests exhaust the server's thread pool, converting a mild control into a
  general outage of the whole app rather than of login alone.

---

## R3 — Resolving the client address behind our own proxy

**Decision**: Take the **rightmost** entry of `X-Forwarded-For`, falling back to the socket's remote
address when the header is absent.

**Rationale, and the trap**: In feature 023's deployment the backend is only reachable through
Caddy, so the socket address is *always Caddy* — every request looks like the same caller, and
address-keyed throttling would collapse into one bucket. So the header must be consulted. But
`X-Forwarded-For` is attacker-supplied: anyone can send `X-Forwarded-For: 1.2.3.4`. Caddy's
`reverse_proxy` **appends** the real peer to whatever arrived, so a spoofed request becomes
`1.2.3.4, <real>`. The rightmost entry is the one our own proxy wrote; the leftmost is the one the
attacker chose. Taking the leftmost — the common, natural-looking choice — would hand the attacker a
fresh bucket per request and defeat the entire per-caller tier while appearing to work.

**Consequence to accept**: an operator who fronts the app with *two* proxies, or one that overwrites
rather than appends, gets a wrong answer from this rule. That degrades the per-caller tier to a
single bucket; it does not open anything, and the instance-wide tier still applies.

**Alternatives considered**: Spring's `ForwardedHeaderFilter` with a trusted-proxy list. Rejected as
configuration an operator would have to get right for a control they cannot see working, when the
deployment's topology is known and fixed.

---

## R4 — Where the counters live

**Decision**: In memory, in a bounded map, entries evicted when their window expires and by a hard
cap on size (oldest evicted first) if it is ever reached.

**Rationale**: The spec's Assumptions already accept losing counts on restart — the cost is a
restart the attacker cannot cause, and the alternative puts a security control inside the operator's
financial database and therefore inside their backups. The bound is not optional though: the
per-caller tier is keyed by an attacker-chosen value, so an unbounded map is a memory exhaustion
vector that this feature would have introduced itself.

**Alternatives considered**: A database table (rejected as above, plus it makes every login a
write); an off-the-shelf rate limiter such as Bucket4j (rejected — a dependency and a distributed-
cache mental model for what is a few dozen lines here, against the spec's out-of-scope rule that an
instance stays one container).

---

## R5 — What a refused attempt returns

**Decision**: HTTP 429, with no body distinguishing anything, for every refused attempt regardless
of the username submitted. Added to the contract as a new response on `/auth/login`.

**Rationale, because FR-005 is easy to misread**: the requirement is that a response must not reveal
*whether the account name exists*. It does not require hiding that throttling is happening. 429 for
all callers and all usernames satisfies it; what would violate it is 429 for a real username and 401
for an unknown one. Adding a response code to an operation is additive, so Principle VII is
untroubled.

**Also required**: the refusal must be issued **before** the password is checked, or the response
time itself becomes the oracle — a refusal that still runs a password hash takes measurably longer
than one that does not.

---

## R6 — Password minimum: value and placement

**Decision**: 12 characters, enforced in **two** places — `minLength: 12` in the OpenAPI schema for
`setupRequest.password` and `changePasswordRequest.newPassword`, and a check in the Domain services
that create or change a credential.

**Rationale**: The contract entry satisfies FR-012 (the constraint is discoverable, not merely
enforced) and gives edge validation for free through the generated model. The Domain check satisfies
Principle VI — the rule is business logic and must be covered by a plain-JUnit test, not left to live
only in a generated artifact that a future regeneration could silently change. 12 rather than 8
because this is one credential, chosen once, protecting everything, on a URL strangers can reach;
NIST's floor of 8 assumes an account among many behind other controls.

**`loginRequest` needs no change**: it carries no minimum today, which is already what FR-010 asks
for. Worth stating because it looks like an omission.

---

## R7 — Turning off the API browser without touching security rules

**Decision**: `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` in the
deployed configuration. **`SecurityConfig` is not touched.**

**Rationale**: This is exactly what FR-014 asks for. With springdoc disabled the paths serve nothing,
so the `permitAll` entries for them become inert — they permit access to routes that no longer
exist. Leaving them in place means an operator hardening their instance cannot accidentally change
who can reach anything else, because they are not editing the authorization rules at all. Deleting
the `permitAll` entries instead would achieve the same visible result while putting the security
filter chain in the blast radius of a documentation setting, which is the trade FR-014 exists to
prevent.

**Alternatives considered**: Requiring authentication for the docs instead of disabling them.
Rejected — it leaves the endpoints live, and "logged in" is the operator, who has the repository.

---

## R8 — Recording refusals for the operator

**Decision**: Log a refusal at WARN with the resolved caller, the consecutive-failure count, and
which tier tripped. No password, ever, in any form.

**Rationale**: FR-006 asks that refusals be visible rather than silently absorbed. Principle V bars
account identifiers and amounts from unstructured strings at WARN or above — a caller address and a
counter are neither, so no masking is required. The submitted username is included: it is not an
account identifier in Principle V's sense (that principle is about financial records), and for an
operator reading their own log after an attack, knowing what was tried is the useful part.

---

## Constitution notes carried into the plan

- **Principle VII and the password minimum.** The constitution enumerates breaking changes as *field
  removal, type change, endpoint removal*. Tightening `minLength` is none of those, so the `/v2`
  requirement is not triggered by the letter of the rule. It is still a restriction — a request that
  was valid before is now rejected — so the plan states it plainly rather than relying on the
  enumeration to look away. In practice it applies only when a password is *set*, never to an
  existing account, and the single client that sends these requests ships from this repository in
  the same change.
- **Principle VI.** The throttle policy and the password rule are both business rules and both get
  plain-JUnit Domain tests written alongside them. The parts that need a real environment — that a
  deployed instance serves no API description, that a refusal survives across requests — are
  integration tests, and CI-only, as ever.
- **Principle V.** Covered by R8.
- **Principle VIII.** Covered by R1: no HTTP type crosses into Domain.
- **Principles I–IV** are untouched: this feature writes no financial data.
