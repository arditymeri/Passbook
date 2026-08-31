# Feature Specification: Device Sync via File Export

**Feature Branch**: `019-device-sync-export`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "Allow a user running multiple independent Passbook instances (e.g. a desktop self-hosted instance and, eventually, a local-data mobile app) to keep them in sync by manually exporting a full-state snapshot file from one device and importing it into another - no server-to-server connection required, matching the self-hosted 'data never leaves the operator's devices' trust model. The export/import must merge rather than overwrite: append-only transaction data (bills, incomes, including corrections and reversals) reuses the existing idempotent-ingestion identity so re-importing an already-seen transaction is a no-op, matching Constitution Principle II. Mutable data (categories, budgets, accounts, recurring series, necessity tags) merges by last-modified-wins, which requires each of those entities to carry a last-modified timestamp today most don't have. The hard case is two devices independently correcting the same original bill differently before ever syncing - the merge needs an explicit, well-defined tie-breaker rather than silently producing two rows that both claim to replace the same original."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Export a full snapshot (Priority: P1)

A user wants to capture everything Passbook currently knows on this device — accounts, categories, budgets, bills, income, recurring series, and necessity tags — into a single file they control, so they can move it to another device or simply keep it as a manual backup.

**Why this priority**: Nothing else in this feature works without an export to import, and it delivers standalone value as a manual backup even before any import/merge behavior exists.

**Independent Test**: Trigger an export on a device with existing data and confirm a single file is produced containing that data.

**Acceptance Scenarios**:

1. **Given** a device with existing accounts, bills, income, categories, budgets, recurring series, and savings goals, **When** the user exports, **Then** a single downloadable file is produced containing all of that data.
2. **Given** a brand-new device with no data yet, **When** the user exports, **Then** a validly-structured (empty-but-well-formed) file is produced rather than an error.

---

### User Story 2 - Bootstrap a new device from an export (Priority: P2)

A user setting up a second device (for example, a new mobile app instance) wants to import a snapshot exported from their existing device, ending up with a faithful copy of that data — this is how two devices establish a shared starting point before ongoing sync becomes meaningful.

**Why this priority**: Enables the primary use case (a second device) via the simplest possible import, with nothing yet to conflict with.

**Independent Test**: Export from a device with data, import into a fresh device with none, and verify the fresh device's data now matches the source, entity for entity.

**Acceptance Scenarios**:

1. **Given** a fresh device with no data, **When** the user imports a snapshot exported from another device, **Then** every account, category, budget, bill, income, recurring series, and savings goal from the snapshot appears on the fresh device.
2. **Given** a fresh device that already imported a snapshot once, **When** the user imports that same snapshot again, **Then** no duplicates are created.

---

### User Story 3 - Reconcile two devices that changed independently (Priority: P3)

A user who has been using two already-linked devices independently for a while — adding transactions, correcting bills, adjusting budgets, tagging necessity on one or both — wants to bring them back in sync by exporting from one and importing into the other, ending up with the combined, correctly-merged result rather than either device's data simply overwriting the other's.

**Why this priority**: This is the feature's actual ongoing value — a one-time bootstrap (User Story 2) isn't "sync"; repeatable reconciliation is.

**Independent Test**: Starting from two devices with shared lineage, make different changes on each (new transactions on one, a budget edit on the other), export from one and import into the other, and verify the result contains both sets of changes with nothing lost and no transaction duplicated.

**Acceptance Scenarios**:

1. **Given** device A recorded three new bills since the last sync and device B recorded none of them, **When** device B imports an export from device A, **Then** all three bills appear on device B exactly once.
2. **Given** device A changed a category's budget limit more recently than device B's own last change to that same budget, **When** device B imports device A's export, **Then** device B's budget limit is updated to device A's value.
3. **Given** device B changed a category's color more recently than device A's export was taken, **When** device B imports device A's export, **Then** device B's more recent color change is preserved, not overwritten by the older value in the import file.
4. **Given** device A and device B each independently corrected the same original bill with different amounts before ever syncing, **When** either device imports the other's export, **Then** exactly one of the two corrections — the more recently made one — is treated as that bill's current value, and the other remains visible only in that bill's correction history, never as two simultaneously "current" values for the same bill.

---

### User Story 4 - Review before applying an import (Priority: P4)

Because importing into a device that already has its own data is higher-stakes than the first bootstrap import, the user sees a summary of what an import will do — how many items will be added, how many existing items will be updated, how many conflicts will be resolved and how — before confirming, rather than changes being applied silently.

**Why this priority**: A safety and trust layer around User Stories 2-3, valuable but not itself the core mechanism.

**Independent Test**: Select an export file to import, see a before/after summary, and confirm that only after accepting does local data actually change — cancelling leaves it untouched.

**Acceptance Scenarios**:

1. **Given** a chosen import file, **When** the user reviews it before confirming, **Then** they see counts of new items, updated items, and resolved conflicts, broken down by data type.
2. **Given** the user cancels after reviewing, **When** they check their data afterward, **Then** nothing has changed.

---

### Edge Cases

- What happens when an import file is corrupted, unreadable, or was produced by an incompatible/future version of the app? It is rejected with a clear explanation; no partial import is ever applied.
- What happens when the same file is imported twice in a row? The second import is a no-op for everything already present, by construction of the identity- and timestamp-based merge rules.
- What happens when importing a snapshot from a device that was never bootstrapped from a common lineage with the receiving device (independently created entity identities)? This is out of scope for this feature (see Assumptions) — the import proceeds additively without attempting to match entities by name, which may produce apparent duplicates; this is documented, expected behavior, not a bug to silently "fix."
- What happens to reversal rows (system-generated bookkeeping entries created by a correction) during export/import? They are included and merged exactly like any other transaction row via the same stable identity, since downstream balance calculations depend on them being present.
- What happens when the same mutable entity was deleted on one device and independently edited on the other before syncing? Per FR-011, deletion never propagates — the receiving device keeps its (edited) copy regardless of what happened on the other device.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to trigger a manual export producing a single file that contains every account, category, budget, bill, income, recurring series, and necessity tag currently on that device.
- **FR-002**: Every append-only transaction record (bill or income, including corrections and reversals) in an export MUST carry the same stable identity it already carries today, so an importing device can recognize whether it has already seen that record.
- **FR-003**: Importing a snapshot into a device MUST NOT create a duplicate of any transaction record whose identity already exists on the receiving device.
- **FR-004**: Every mutable entity (category, budget, account, recurring series, necessity tag) included in an export MUST carry a last-modified marker sufficient to determine, at import time, whether the incoming value or the receiving device's current value is more recent.
- **FR-005**: When a mutable entity exists on both the receiving device and in an imported snapshot with different values, the import MUST keep whichever value has the later last-modified marker and discard the other.
- **FR-006**: When two devices have each independently created a different correction of the same original bill before ever syncing, importing MUST resolve this deterministically to a single current value — the more recently made correction — rather than leaving both visible as current simultaneously; the superseded correction MUST remain visible in that bill's correction history, never silently discarded.
- **FR-007**: The import flow MUST show the user a summary of pending changes — counts of new items, updated items, and resolved conflicts, broken down by data type — before any change is applied to local data.
- **FR-008**: The user MUST be able to cancel a reviewed import with no local data changed as a result.
- **FR-009**: Re-importing a snapshot that has already been imported before MUST leave the receiving device's data unchanged.
- **FR-010**: The system MUST reject an import file that is unreadable, malformed, or produced by an incompatible version, with a clear explanation, and MUST NOT apply a partial import in that case.
- **FR-011**: An entity absent from an imported snapshot MUST NOT be deleted or otherwise removed from the receiving device — the system never infers a deletion from absence. Deletion propagation across devices is out of scope for this feature; a user who deletes something on one device must delete it on the other device(s) themselves if they want it gone everywhere.
- **FR-012**: The exported file MUST be plain, unencrypted data. Protecting the file during transfer and storage is entirely the user's own responsibility, consistent with the file's transport itself (USB, email, cloud drive, etc.) already being out of scope for this feature.

### Key Entities

- **Sync Snapshot**: The exported file itself — a point-in-time, complete capture of one device's accounts, categories, budgets, bills, income, recurring series, savings goals, and necessity tags, each carrying its stable identity and, for mutable entities, a last-modified marker.
- **Last-Modified Marker**: A per-entity timestamp recorded whenever a mutable entity changes, used only to arbitrate merge conflicts at import time — not a user-facing feature in its own right.
- **Import Summary**: A derived (not persisted) description of what an import will do or did — counts of items added, updated, and resolved as conflicts, by data type — shown to the user before they confirm (User Story 4).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can move their full data set to a brand-new device (export on the source, import on the destination) in a few minutes of hands-on effort, excluding however long moving the file itself takes.
- **SC-002**: After two devices that diverged independently are reconciled via one export and import, every transaction that existed on either device before the sync exists on the receiving device afterward, with none duplicated.
- **SC-003**: Re-importing the same snapshot file any number of times never changes the result beyond what the first import produced.
- **SC-004**: When two devices each changed the same piece of mutable data before syncing, the receiving device always ends up with whichever change was made more recently, verified against a data set of known edit timestamps.
- **SC-005**: A user reviewing an import before confirming can see how many items will be added, updated, or resolved as conflicts without having to inspect the file's contents themselves.

## Assumptions

- Devices intending to sync must share a common lineage: every device other than the first must have been bootstrapped by importing an export from an already-existing device (User Story 2), so that entity identities (account ids, category ids, and so on) are shared from the start. Reconciling two independently-created instances that were never linked this way — matching entities by name or other heuristics — is out of scope for this feature.
- An export is a full, unfiltered snapshot of everything on the device that this feature covers (accounts, categories, budgets, bills, income, recurring series, savings goals, necessity tags); there is no partial or selective export in this version.
- Applying an import is all-or-nothing per the deterministic merge rules in this spec (identity-based dedup for transactions, last-modified-wins for mutable data, the correction tie-breaker in FR-006). The user reviews a summary (User Story 4) but does not selectively accept or reject individual changes within one import.
- Both export and import are actions the user explicitly and manually triggers; there is no automatic, scheduled, or background export/import in this version, matching the feature's own framing as device-to-device sync over a manually moved file.
- Moving the exported file between devices (by USB, the user's own cloud storage, AirDrop, email, or any other means) is entirely the user's responsibility and outside this feature's scope, consistent with the self-hosted "data never leaves devices the operator controls" posture — the app produces and consumes the file; it does not transport it.
- Sync is additive-only: nothing is ever deleted as a side effect of an import (FR-011). This is a deliberate v1 scope cut, not an oversight — cross-device deletion propagation would need an explicit tombstone concept this feature does not introduce. A future feature can add it without changing anything specified here.
- The exported file is not encrypted by the app (FR-012); the user is trusted to secure it during transfer and storage themselves, the same way they are already trusted to secure the transfer itself.
