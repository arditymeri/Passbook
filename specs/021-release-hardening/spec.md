# Feature Specification: Release Hardening

**Feature Branch**: `021-release-hardening`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Close the remaining Self-Hosting Obligations from the constitution so this instance can responsibly be handed to someone other than its author: explicit schema migrations replacing Hibernate's ddl-auto=update (with a baseline that adopts an already-running instance's data without loss), credentials out of version control and sourced from the environment, a documented and tested backup/restore procedure, and versioned releases with a documented upgrade path. Packaging concerns (production compose, bundling the frontend, one-click installer) are deliberately out of scope."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upgrade an Existing Instance Without Losing Data (Priority: P1)

An operator already running Passbook, with months of their own financial history in it, moves to a newer version. Their database is brought up to the new version's expected shape automatically, their data is untouched, and if anything about the upgrade doesn't line up, the app refuses to start rather than quietly changing their schema.

**Why this priority**: This is the whole point of the feature. Financial history is irreplaceable, the app is the only copy, and today the schema is reshaped by inference at every startup — the single highest-risk thing about handing this to anyone.

**Independent Test**: Start an instance on the current version, enter real data, upgrade to the new version, and confirm every account, category, budget, bill, income, recurring series and savings goal is still present and unchanged.

**Acceptance Scenarios**:

1. **Given** an instance whose database was created by the pre-migration version and contains real data, **When** the operator upgrades and starts the new version, **Then** the app starts successfully and every existing row is preserved — no table is dropped or recreated.
2. **Given** a brand-new, completely empty database, **When** the operator starts the app for the first time, **Then** the full schema is created and the result is indistinguishable from an upgraded existing instance.
3. **Given** an instance whose database does not match what the running code expects, **When** the app starts, **Then** it fails to start with a message identifying the mismatch, and makes no attempt to alter the schema to fit.
4. **Given** an upgrade whose schema change fails partway through, **When** the operator inspects the database, **Then** it is left in its prior consistent state rather than half-changed, and the app does not serve requests against it.

---

### User Story 2 - Run an Instance Without Credentials From the Repository (Priority: P1)

A new operator clones the project and runs their own instance. Every secret it needs comes from their environment; nothing shipped in the source grants access to their database. The author's own previously-published password is called out plainly so it gets rotated rather than silently inherited.

**Why this priority**: Equally blocking for distribution: a project that ships a working database password means every install shares a known credential. The constitution states this as a flat prohibition.

**Independent Test**: Search the repository for any credential value and find none; then start a fresh instance supplying only environment-provided secrets and confirm it works.

**Acceptance Scenarios**:

1. **Given** the repository as published, **When** anyone inspects its tracked files, **Then** no usable credential value is present in any of them.
2. **Given** a fresh checkout, **When** the operator follows the documented setup, **Then** they supply their own secrets once and the instance starts — local development remains a single command after that.
3. **Given** required secrets are not configured, **When** the app starts, **Then** it fails immediately with a message naming what is missing, rather than falling back to a built-in default.
4. **Given** an operator upgrading from a version that shipped a password, **When** they read the upgrade notes, **Then** they are told explicitly that the old value is in published history and must be rotated, not merely removed.

---

### User Story 3 - Back Up and Actually Restore (Priority: P2)

An operator takes a backup of their instance before an upgrade — or on a schedule — and can later restore it into a working instance and find their financial history intact.

**Why this priority**: Necessary for the upgrade path in Story 1 to be safe advice ("back up first" is only meaningful if restore is proven), but the instance is still runnable and upgradable without it, so it follows rather than blocks.

**Independent Test**: Take a backup of an instance containing data, destroy the database, restore from the backup, and confirm the app runs against it with the same data.

**Acceptance Scenarios**:

1. **Given** a running instance with data, **When** the operator follows the documented backup procedure, **Then** they obtain a single artifact that contains their complete financial history.
2. **Given** a backup artifact and an empty database, **When** the operator follows the documented restore procedure, **Then** the app starts against the restored database and shows exactly the data captured at backup time.
3. **Given** a backup taken from a newer version than the app being restored into, **When** the operator attempts to run against it, **Then** the mismatch is reported rather than the older code operating on a newer schema.

---

### User Story 4 - Know What Version You Run and What Changed (Priority: P3)

An operator can tell which version their instance is, read what changed between versions, and follow a documented sequence to move from one to the next.

**Why this priority**: Makes the previous three stories usable in practice — but an operator who knows nothing about versions can still upgrade safely, because Stories 1–3 make the mechanics safe regardless.

**Independent Test**: On a running instance, determine the version without reading source code; then find, for that version, both a description of what changed and the steps to upgrade to it.

**Acceptance Scenarios**:

1. **Given** a running instance, **When** the operator looks for its version, **Then** they can determine it without inspecting source or build files.
2. **Given** a published release, **When** the operator reads its notes, **Then** they can see what changed and whether anything requires action on their part.
3. **Given** an operator on an older release, **When** they follow the documented upgrade steps, **Then** the sequence includes taking a backup before anything else, and requires no hand-written database commands.

---

### Edge Cases

- What if an existing operator's database drifted from what the code expects (a leftover column from an earlier inferred schema change)? Startup must fail with an actionable message naming the difference — never "fix" it silently, since inference is exactly what this feature removes.
- What if the operator restores a backup taken from a newer version into older code? The version mismatch must surface as a refusal, not as older code writing against a newer schema.
- What if an operator upgrades across several versions at once, skipping intermediate ones? Every intervening schema change must still be applied, in order, exactly once.
- What if two instances point at the same database and start simultaneously? Only one may apply schema changes; the other must wait or fail rather than applying them twice.
- What if the operator never sets a secret because they only ever run locally? They must still be forced to choose one — a built-in fallback is what makes every install share a credential.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every schema change MUST be an explicit, versioned, ordered step that is recorded once applied, so the same change is never applied twice and the sequence is reproducible on any instance.
- **FR-002**: Schema changes MUST be applied automatically when the app starts, so an operator upgrading never runs database commands by hand.
- **FR-003**: An existing instance whose schema was created by the previous (inference-based) version MUST adopt the current schema as its starting point without dropping, recreating, or emptying any table.
- **FR-004**: A brand-new empty database MUST end up with a schema identical to that of an upgraded existing instance.
- **FR-005**: The app MUST verify at startup that the database matches what the code expects, and MUST fail to start — without modifying the schema — when it does not.
- **FR-006**: A failed schema change MUST leave the database in its prior consistent state, and the app MUST NOT serve requests against a partially changed database.
- **FR-007**: The repository MUST NOT contain any usable credential value in any tracked file.
- **FR-008**: All secrets MUST be supplied by the operator's environment, with a tracked example file documenting which are required (and containing no real values).
- **FR-009**: The app MUST fail to start, naming what is missing, when a required secret is not configured — it MUST NOT substitute a built-in default.
- **FR-010**: Documentation MUST state that any credential previously published in this repository's history must be rotated, not merely removed.
- **FR-011**: A backup procedure MUST be documented that produces a single artifact containing the operator's complete financial history.
- **FR-012**: A restore procedure MUST be documented and MUST have been executed end-to-end successfully, not merely described.
- **FR-013**: Each release MUST carry a version identifier that an operator can determine from a running instance without reading source.
- **FR-014**: Each release MUST have notes stating what changed and whether it requires operator action.
- **FR-015**: Upgrade instructions MUST be documented, MUST place taking a backup before any other step, and MUST NOT require hand-written database commands.

### Key Entities

- **Schema version history**: The record, stored alongside the operator's data, of which schema steps have been applied to that particular database — what makes "apply exactly once" and "upgrade across several versions" possible.
- **Release**: A published version identifier plus its notes: what changed, and what (if anything) the operator must do.
- **Backup artifact**: A single restorable capture of an instance's complete financial history at a point in time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Upgrading an existing instance preserves 100% of its rows across every data type — verifiable by comparing counts and contents before and after.
- **SC-002**: A new operator goes from a fresh checkout to a running instance in under 10 minutes following only the documented steps, using no credential taken from the repository.
- **SC-003**: A restore from backup reproduces the instance's financial history exactly — verifiable by comparing the restored data against what existed at backup time.
- **SC-004**: 100% of startups where code and database disagree fail before serving any request, with a message that names the specific mismatch.
- **SC-005**: Zero credential values are present across all tracked files in the repository.
- **SC-006**: An operator can determine their running version, what changed in it, and how to upgrade, entirely from the running instance and published notes.

## Assumptions

- Migrations are handled by Flyway. The constitution permits Flyway or Liquibase; Flyway is chosen for plain-SQL migrations, which keep each schema step reviewable as the exact statements that will run against an operator's data.
- The baseline is the schema as the current version generates it — features 001–020 are collapsed into a single starting point rather than reconstructed as a historical sequence of migrations, since no released version ever had a different schema to migrate from.
- PostgreSQL remains the only supported database; portability to other engines is not a goal.
- Backup and restore use the database's own native dump/restore tooling. Feature 019's sync export is data portability between an operator's own devices, not a backup: it deliberately omits instance-level configuration such as the admin account, and it merges rather than restores.
- Deployment is a single instance against a single database. Upgrades are stop-then-start; zero-downtime or rolling upgrades are not a requirement, and no second instance is expected to be running during one.
- Releases are versioned semantically, consistent with how the constitution versions itself.
- Rotating the previously published database password is the operator's action to take; this feature's obligation is to tell them clearly and unambiguously that it is necessary.
- Out of scope, deliberately: trimming the development compose file for production, bundling the frontend into the backend artifact, and any one-click installer. Those are packaging and distribution concerns, separable from this feature's focus on not damaging an operator's data.
