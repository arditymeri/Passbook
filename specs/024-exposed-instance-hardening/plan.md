# Implementation Plan: Exposed Instance Hardening

**Branch**: `claude/project-status-s0au7m` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-exposed-instance-hardening/spec.md`

## Summary

Four gaps that only matter because feature 023 made an internet-reachable instance easy to create:
unlimited password guessing, a one-character password minimum, an instance that publishes its own
API surface to anonymous visitors, and a first-run setup window that a stranger can claim.

The approach in one line each: a two-tier in-memory throttle whose decision is pure Domain logic
over a clock it is given; a real password minimum enforced where a password is *set*, never where
one is used; a configuration switch for the API browser that does not touch the security filter
chain; and an ordering step in the deployment guide.

**The design constraint that shapes everything**: this app has one account and no password reset, so
locking out the account and locking out the operator are the same event. Every rule here is built
for unattended recovery. Nothing is persisted, nothing needs an administrator, and one rule —
attempts made during a refusal are refused but not counted — exists solely to stop a continuous
attacker turning a fifteen-minute window into a permanent one.

## Technical Context

**Language/Version**: Java 21, TypeScript 4.6 (frontend)

**Primary Dependencies**: Spring Boot 3.4.0, Spring Security, springdoc-openapi (existing);
**no new dependency** — see research R4

**Storage**: None. No table, no column, no migration; the last applied migration stays `V4`

**Testing**: JUnit 5 + AssertJ (Domain, plain, no application context); TestContainers +
`@SpringBootTest` (integration, CI-only — no Docker daemon in development)

**Target Platform**: Self-hosted Linux container, reachable over the internet via a TLS-terminating
proxy (Codespaces port forwarding, a Cloudflare tunnel, or Caddy on a VPS)

**Project Type**: Web service (hexagonal Maven modules) + React SPA

**Performance Goals**: No measurable cost on a successful login; the throttle is a map lookup

**Constraints**: Counter state must be bounded (an attacker chooses the keys); a refusal must be
issued before password verification, or response time becomes an oracle; no external service an
operator has to run separately

**Scale/Scope**: One account, one operator, one instance

## Constitution Check

*GATE: passed before Phase 0; re-evaluated after Phase 1 design — result unchanged.*

| Principle | Verdict | Reasoning |
|---|---|---|
| I. Transaction Immutability | **N/A** | Writes no financial data |
| II. Ingestion Is Idempotent | **N/A** | Touches no ingestion path |
| III. Balance Derivation | **N/A** | Reads no balances |
| IV. Currency Precision | **N/A** | Handles no money |
| V. Audit Trail & Observability | **PASS** | FR-006 requires refusals to be recorded; research R8 places them at WARN with caller and count. Principle V's masking rule covers account identifiers and amounts — neither appears |
| VI. Test-First Development | **PASS** | The throttle decision and the password rule are business rules and get plain-JUnit Domain tests alongside them. Persistence is untouched, so the integration-test-against-a-real-database clause has nothing to bind to here; the integration tests that do exist run against the real app |
| VII. API Contract Stability | **PASS, with a stated restriction** | See below |
| VIII. Hexagonal Architecture | **PASS** | Research R1: counting and deciding are Domain, pure and context-free; resolving the caller is Application, because an address is an HTTP fact. No HTTP type crosses the boundary |

### Principle VII in detail

Two contract changes:

- **`/auth/login` gains a 429 response.** Additive. Non-breaking. Nothing to justify.
- **`setupRequest.password` and `changePasswordRequest.newPassword` gain `minLength: 12`.** This is
  a restriction: a request that was valid before is now rejected.

The constitution enumerates breaking changes as *field removal, type change, endpoint removal*.
Tightening a length constraint is none of the three, so the `/v2` path requirement is not triggered
by the letter of the rule. Recording that rather than leaning on it, because the spirit is what
matters: the restriction applies only when a password is **set**, never when one is used, so no
existing account is affected and no existing session breaks. The single client that sends these
requests ships from this repository in the same change. And the alternative is shipping a
one-character admin password to the internet.

`loginRequest` is deliberately not constrained (FR-010, research R6).

### Self-Hosting Obligations

This feature strengthens the last of them — *an instance-level authentication gate MUST be
enabled* — which 020 satisfied in form. A gate that accepts unlimited guesses at a one-character
password satisfies it thinly once the instance is reachable by strangers.

## Project Structure

### Documentation (this feature)

```text
specs/024-exposed-instance-hardening/
├── spec.md
├── plan.md              # This file
├── research.md          # R1–R8
├── data-model.md        # In-memory only; no migration
├── quickstart.md        # 12 scenarios, marked local or CI
├── checklists/
│   └── requirements.md
├── contracts/
│   └── auth-contract-delta.md
└── tasks.md             # /speckit-tasks output — not created here
```

### Source Code

```text
Domain/src/main/java/at/ymeri/my/finance/domain/
├── service/auth/
│   ├── LoginThrottle.java            # NEW — counting and the decision; pure, takes Instant
│   └── PasswordPolicy.java           # NEW — the minimum, as a rule with a reason
├── service/auth/SetupAdminAccountServiceImpl.java    # enforce the policy
└── service/auth/ChangePasswordServiceImpl.java       # enforce the policy

Domain/src/test/java/at/ymeri/my/finance/domain/service/auth/
├── LoginThrottleTest.java            # NEW — quickstart 1,2,3,4,6,11
└── PasswordPolicyTest.java           # NEW — quickstart 8

Application/src/main/
├── java/at/ymeri/my/finance/
│   ├── controller/auth/AuthController.java    # consult the throttle before verifying
│   └── security/ClientAddressResolver.java    # NEW — rightmost X-Forwarded-For
└── resources/swagger/auth/
    ├── auth-api-controller.yaml      # 429 on /auth/login
    └── auth-model.yaml               # minLength 12 where a password is set

Launcher/src/main/resources/application.properties   # throttle defaults

docker-compose.deploy.yaml            # springdoc off in the deployed configuration

frontend/src/
├── api/client.ts                     # surface 429 distinctly from 401
└── components/{LoginPage,SetupPage,ChangePasswordDialog}.tsx   # state the minimum up front

docs/DEPLOYING.md                     # setup-before-publish as a numbered step
SECURITY.md                           # limitations closed, limitations kept
```

**Structure Decision**: Existing hexagonal layout, no new module. The split that matters is
`LoginThrottle` in Domain and `ClientAddressResolver` in Application: the rules are testable in
milliseconds with a controlled clock, and the one part that must know about HTTP stays where HTTP
belongs (research R1, Principle VIII).

## Phase Ordering Notes

1. **Write `LoginThrottleTest` before `LoginThrottle`.** Quickstart scenarios 3 and 4 are the
   feature's whole risk surface, they need no database, and they are the two a correct-looking
   implementation gets wrong.
2. **`SecurityConfig` is not edited by this feature.** Turning springdoc off makes its `permitAll`
   entries inert without moving an authorization boundary (research R7). If a task appears to
   require editing it, that task is wrong.
3. **The 429 must precede password verification.** A refusal that still runs the hash is a timing
   oracle (research R5). This is an ordering requirement inside one method, easy to lose in a later
   refactor, and worth a comment where it lives.
4. **The frontend ships with the contract.** A 429 rendered as "incorrect username or password"
   would tell an operator who is being attacked exactly the wrong thing, and a minimum discovered
   through rejection is a worse first-run experience than one stated up front.

## Complexity Tracking

No constitutional violations to justify. One deliberate complexity, recorded because a reviewer
should see it was chosen rather than accumulated:

| Choice | Why | Simpler alternative rejected because |
|---|---|---|
| Two throttle tiers rather than one | A single-account app makes any account-keyed counter a public lockout button, and a caller-keyed one is defeated by rotation | Caller-only protects nothing against the one attacker who rotates; account-only hands a stranger the operator's front door. Research R2 states the residual risk that remains even with both |
