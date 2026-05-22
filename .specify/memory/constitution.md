<!--
SYNC IMPACT REPORT
==================
Version change: [placeholder] → 1.0.0
Modified principles: N/A (initial fill from template)
Added sections:
  - Core Principles (8 finance-specific principles)
  - Financial Data Standards
  - Development Workflow
  - Governance
Removed sections: N/A (template placeholders replaced)
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ Constitution Check gates align with principles below
  - .specify/templates/spec-template.md ✅ No finance-specific mandatory sections to add
  - .specify/templates/tasks-template.md ✅ Task categories reflect principle-driven types
Deferred TODOs: None
-->

# MyFinance Constitution

## Core Principles

### I. Transaction Immutability (NON-NEGOTIABLE)

Financial transactions MUST never be silently modified or deleted after they are persisted.
Corrections MUST be made via compensating/reversal entries that reference the original transaction.
The original record remains in the audit log untouched. Any soft-delete or status flag that hides a
transaction from balances MUST preserve the original row and create a reversal entry so the ledger
always nets to zero on the corrected amount.

**Rationale**: Regulatory compliance and auditability require a tamper-evident ledger. Silent edits
destroy the ability to reconstruct historical account states.

### II. Double-Entry Accounting

Every financial event MUST produce at least two journal lines: one debit and one credit.
The sum of all debits MUST equal the sum of all credits within any given transaction.
The system MUST reject any persistence attempt where debits ≠ credits.

**Rationale**: Double-entry is the mathematical foundation ensuring the accounting equation
(Assets = Liabilities + Equity) holds at all times. Violations indicate logic bugs, not edge cases.

### III. Account Integrity & Balance Derivation

Account balances MUST be computed by summing transaction lines, never stored as a mutable
running total in the primary record. A cached/materialized balance is permitted only as a
read-optimisation layer and MUST be invalidated atomically with the transaction that caused it.
The authoritative balance is always the aggregate of ledger entries.

**Rationale**: A mutable `currentBalance` column that drifts from the ledger sum is a data
integrity failure. Deriving balances from the ledger makes reconciliation straightforward and
prevents silent corruption.

### IV. Currency Precision (NON-NEGOTIABLE)

Monetary amounts MUST be represented as `java.math.BigDecimal` (backend) or an equivalent
fixed-point type in any other layer. Floating-point types (`float`, `double`, `number` in JS/TS
for money) are FORBIDDEN for any monetary value. Rounding MUST use `RoundingMode.HALF_EVEN`
(banker's rounding) and be applied only at the final presentation layer, never mid-calculation.
Currency codes MUST follow ISO 4217.

**Rationale**: IEEE-754 floating-point arithmetic produces rounding errors that compound over
thousands of transactions, leading to balance discrepancies. `BigDecimal` with `HALF_EVEN` is
the industry standard for financial arithmetic.

### V. Audit Trail & Observability

Every state-changing operation on financial data (create, reverse, categorise, reconcile) MUST
be recorded with: timestamp (UTC, ISO 8601), the actor/source, and the resulting state change.
Structured logging (JSON) is REQUIRED in production. Sensitive fields (account numbers, amounts)
MUST NOT appear in unstructured log strings at WARN or above without masking.

**Rationale**: Audits, disputes, and incident post-mortems all require a complete, queryable
timeline of who did what and when.

### VI. Test-First Development (NON-NEGOTIABLE)

All financial calculation and business-rule logic in the Domain module MUST be covered by unit
tests written before or alongside the implementation (TDD or test-concurrent). Integration tests
MUST verify persistence adapters against a real database (TestContainers). Mocks of the database
layer in integration tests are FORBIDDEN.

**Rationale**: Finance bugs are often invisible until they compound. Test-first ensures invariants
(balance derivation, immutability, double-entry) are encoded as executable contracts, not docs.

### VII. API Contract Stability

Public REST API contracts MUST be defined in OpenAPI YAML first, before implementation.
Breaking changes (field removal, type change, endpoint removal) MUST be introduced under a new
API version path (`/v2/...`). Additive changes (new optional fields) are non-breaking and do not
require a new version. Deprecated endpoints MUST remain functional for at least one release cycle.

**Rationale**: External consumers and the React frontend depend on stable contracts. Surprise
breakage causes data loss or UI failures in production.

### VIII. Hexagonal Architecture Compliance

The Domain module MUST have zero runtime dependencies on Spring, JPA, Kafka, or any infrastructure
framework. All I/O (persistence, messaging, HTTP) MUST be mediated through port interfaces defined
in Domain and implemented in Application or Infrastructure. Domain business logic MUST be
exercisable in plain JUnit tests with no application context.

**Rationale**: Isolating the domain from infrastructure keeps financial logic portable, testable
in milliseconds, and free from framework coupling that obscures invariants.

## Financial Data Standards

- **Date/Time**: All timestamps stored and transmitted as UTC. Display conversion happens at the
  presentation layer only. Use `java.time.Instant` or `OffsetDateTime` (UTC offset=0) in the
  domain; never `java.util.Date` or `Calendar`.
- **Account Types**: Recognised types are `ASSET`, `LIABILITY`, `EQUITY`, `INCOME`, `EXPENSE`.
  Normal balance rules (debit/credit increases) MUST be enforced per type.
- **Multi-Currency**: When a transaction involves more than one currency, an exchange rate
  snapshot MUST be persisted with the transaction. The functional currency for reporting is
  defined per account (defaultCurrency field, ISO 4217).
- **Reconciliation**: Accounts MUST support a reconciled/cleared status per transaction line.
  Reconciled lines MUST NOT be reversed without an explicit override and audit record.

## Development Workflow

- **Spec-first**: Every new feature MUST have a specification (`spec.md`) reviewed before
  any implementation begins. The OpenAPI YAML for new endpoints is part of the spec, not the
  implementation.
- **Constitution Check**: The plan for every feature MUST explicitly verify compliance with
  Principles I–VIII before Phase 0 research is considered complete.
- **Code Review gate**: PRs touching Domain financial logic require at least one reviewer to
  confirm Principles I (immutability), II (double-entry), and IV (currency precision) are upheld.
- **No direct SQL in Domain**: Domain services MUST use port interfaces; raw SQL or JPQL belongs
  exclusively in Infrastructure adapters.
- **No floating-point money**: CI MUST fail (via ArchUnit or equivalent) if `double`/`float`
  field types are introduced in Domain DTOs or entity money fields.

## Governance

This Constitution supersedes all other documented practices in this repository. Amendments require:
1. A written rationale explaining why the current principle is insufficient.
2. An impact assessment against existing features and tests.
3. A migration plan if existing code must change to comply.
4. Version increment per semantic rules (MAJOR for removals/redefinitions, MINOR for additions,
   PATCH for clarifications).

All PRs and feature reviews MUST verify compliance with this Constitution. Complexity MUST be
justified by a constitutional principle; gold-plating or speculative generality is prohibited.

Refer to `CLAUDE.md` for runtime development commands and project structure guidance.

**Version**: 1.0.0 | **Ratified**: 2026-05-23 | **Last Amended**: 2026-05-23
