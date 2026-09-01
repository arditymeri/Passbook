# Quickstart: Device Sync via File Export

Manual verification walkthrough. Requires the full stack running (`docker-compose up`, or
`./mvnw -pl Launcher spring-boot:run` against local infra + `cd frontend && npm run dev`) on **two**
separate database instances (simulating two devices) — e.g. two local Postgres containers, or the
same app pointed at two different databases in turn.

## Scenario 1 — Export a full snapshot (US1)

1. On "Device A", create an account, a category, a budget, a few bills and income entries, confirm
   a recurring series, and set a savings goal.
2. Trigger an export.
3. **Expected**: a single file downloads containing all of the above (US1.1).
4. On a brand-new "Device C" with no data at all, trigger an export.
5. **Expected**: a validly-structured, empty snapshot file downloads without error (US1.2).

## Scenario 2 — Bootstrap a fresh device (US2)

1. Export from Device A (per Scenario 1).
2. On a fresh "Device B" with no data, import that file.
3. **Expected**: every account, category, budget, bill, income, recurring series, and savings goal
   from Device A now appears on Device B (US2.1).
4. Import the same file into Device B again.
5. **Expected**: no duplicates — Device B's data is unchanged from step 3 (US2.2, FR-009).

## Scenario 3 — Reconcile two devices that changed independently (US3)

1. Starting from Device A and Device B linked per Scenario 2, record three new bills on Device A
   only.
2. Export from Device A, import into Device B.
3. **Expected**: all three bills now appear on Device B exactly once (US3.1, SC-002).
4. Change a category's budget limit on Device A; separately, on Device B, change that *same*
   category's budget limit to a different value, at a *later* wall-clock time than Device A's
   change.
5. Export from Device A, import into Device B.
6. **Expected**: Device B's budget limit is unchanged — its own later edit is preserved, not
   overwritten by the older value from the import (US3.3, FR-005).
7. Reverse the timing: change the budget limit on Device A *after* Device B's own last change to
   it, export from Device A, import into Device B.
8. **Expected**: Device B's budget limit is now updated to Device A's value (US3.2).
9. On Device A, correct a bill's amount. Separately on Device B (before ever syncing this change),
   correct the *same original* bill to a *different* amount.
10. Export from Device A, import into Device B.
11. **Expected**: exactly one of the two corrections is Device B's current value for that bill —
    whichever was made more recently — and the other is visible only in that bill's correction
    history (US3.4, FR-006).

## Scenario 4 — Review before applying (US4)

1. With Device A and Device B diverged as in Scenario 3, export from Device A and choose to import
   into Device B, but stop at the review step.
2. **Expected**: a summary shows counts of items to be added, updated, and resolved as conflicts,
   broken down by data type, before anything is applied (US4.1).
3. Cancel instead of confirming.
4. **Expected**: Device B's data is completely unchanged (US4.2, FR-008).
5. Repeat and confirm this time.
6. **Expected**: the changes previewed in step 2 are exactly the changes now visible on Device B.

## Scenario 5 — Additive-only, no encryption (Edge Cases / FR-011, FR-012)

1. Delete a category on Device A (one not referenced by any bill). Do not change anything on
   Device B.
2. Export from Device A, import into Device B.
3. **Expected**: Device B still has its own copy of that category — its absence from the import is
   never treated as a deletion instruction (FR-011).
4. Inspect the exported file directly (e.g., open it in a text editor).
5. **Expected**: it is plain, readable JSON — no passphrase was requested during export, and
   nothing about the file is encrypted (FR-012).

## Scenario 6 — Malformed / incompatible file (Edge Case / FR-010)

1. Hand-edit an exported file's `schemaVersion` to an unrecognized value (or truncate the file to
   make it invalid JSON).
2. Attempt to import it.
3. **Expected**: the import is rejected with a clear explanation; nothing on the receiving device
   changes.

---

**Status**: BLOCKED in this development sandbox — no Docker daemon is available to run
`docker-compose up`, the `integration-tests` module, or a second database instance to simulate a
second device, consistent with every prior feature (007-018). This walkthrough should be executed
manually once implementation lands in an environment with Docker available.
