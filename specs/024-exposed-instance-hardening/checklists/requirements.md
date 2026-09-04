# Specification Quality Checklist: Exposed Instance Hardening

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-04
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

### Validation iterations

**Iteration 1 — three failures, all fixed:**

1. *No implementation details* — FAILED. Draft requirements named the mechanism ("an in-memory
   attempt counter keyed by username and client IP"), which is a plan decision, not a requirement.
   Rewritten as behaviour: FR-001/002 say attempts are counted and refused, and the keying question
   is stated in Edge Cases as a constraint the design must satisfy ("keyed only to where a request
   appears to come from is trivially evaded; keyed only to the account name lets anyone lock the
   operator out") without dictating the answer.

2. *Success criteria are technology-agnostic* — FAILED. A draft criterion read "login returns HTTP
   429 after N attempts". Replaced by SC-001, which states the outcome — exhaustive guessing becomes
   impractical — and leaves the status code to the contract.

3. *Requirements are testable* — FAILED. "Passwords must be strong" is not testable. FR-009 now
   fixes the rule to a minimum length applied at the point a password is set, and FR-010 states
   where it must NOT apply, which is the part most likely to be got wrong.

**Iteration 2 — clean.**

### Deliberate notes for planning

- **No numbers are fixed here.** The spec says "a small number" of attempts and "a bounded period"
  rather than 5 and 15 minutes. Those are plan decisions; what the spec fixes is that both are
  configurable (FR-007) and that recovery is unattended (FR-003, SC-002).
- **FR-005 is the subtle one.** Making a refusal indistinguishable from an ordinary failure is
  easy to state and easy to violate accidentally — a different status code, a different message, or
  a measurably different response time all leak it. Worth an explicit test.
- **The password minimum is a contract tightening**, not an extension, so Principle VII's stability
  gate applies. The spec justifies it rather than waving it through; the plan must confirm the
  single existing client is updated in the same change.
