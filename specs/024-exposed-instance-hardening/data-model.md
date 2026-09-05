# Phase 1 Data Model: Exposed Instance Hardening

**Feature**: 024-exposed-instance-hardening | **Date**: 2026-09-04

## Nothing is persisted

This feature adds **no table, no column and no migration.** Worth stating outright, because every
feature since 021 has added schema and the absence here is a decision rather than an oversight:
the only state this feature holds describes a moment (how many failures just happened, and until
when they are being refused), not a fact about the operator's finances. Putting it in the database
would place a security control inside their backups, and make every login a write. See research R4.

The last applied migration remains `V4`. The next feature that needs schema takes `V5`.

---

## 1. Attempt state (in memory)

One entry per counted key. The instance-wide tier is the same shape under a fixed key, so there is
one structure, not two.

| Field | Meaning |
|---|---|
| key | What is being counted: a resolved caller address, or the fixed instance-wide key |
| consecutiveFailures | Failed attempts since the last success or expiry |
| refusedUntil | When the current refusal ends; absent when not refusing |

### Rules

- A failed attempt **while not refusing** increments `consecutiveFailures`. Reaching the tier's
  threshold sets `refusedUntil` to now plus the window.
- A failed attempt **while refusing** is refused and **changes nothing** — it neither increments the
  count nor extends `refusedUntil`. This single rule is what stops a continuous attacker converting
  a temporary refusal into a permanent lockout (FR-003, SC-002).
- A **successful** authentication clears the entry entirely (FR-004).
- An entry whose `refusedUntil` has passed, and one that has been idle beyond the window, are both
  evicted. Eviction is what keeps an attacker-chosen key space from growing without bound.

### State transitions

```
                 failure (count < threshold)
        ┌──────────────────────────────────────┐
        │                                      │
        ▼                                      │
   ┌─────────┐  failure (count reaches N)  ┌───────────┐
   │ COUNTING│ ──────────────────────────► │  REFUSING │
   └─────────┘                             └───────────┘
        ▲                                      │    │
        │  success, or window expires          │    │ failure
        └──────────────────────────────────────┘    │ (ignored — no
                                                    │  count, no extension)
                                                    └───────────────┘
```

`REFUSING → COUNTING` happens on its own, by the clock. There is no transition that requires an
operator, an endpoint, or a database edit — that is FR-003, and it is the reason the diagram has no
fourth state.

---

## 2. Throttle configuration

Operator settings (FR-007), with defaults safe on a public URL:

| Setting | Default | Why this default |
|---|---|---|
| per-caller threshold | 5 failures | Survives ordinary mistyping; ends casual guessing |
| instance-wide threshold | 20 failures | High enough that one operator's bad morning cannot reach it |
| refusal window | 15 minutes | Long enough to make guessing impractical, short enough that a locked-out operator waits rather than despairs |
| enabled | true | Off is a choice an operator on a private network may reasonably make |

---

## 3. Password rule

Not an entity — a validation rule, recorded here because it has one property worth pinning:

**It applies when a password is set, never when one is used.** Setup and change-password enforce a
minimum of 12; authentication enforces nothing. An account created before this feature keeps working
with whatever it has (FR-010, SC-004), and no response ever reveals how long a password is.

---

## 4. What this feature does not touch

- **`AdminAccount`** — no new field. No "locked" flag, no "failed attempts" column, no
  "password last changed". A persisted lock is precisely the state that could outlive its own expiry
  and strand the operator.
- **Every financial entity** — untouched. This feature writes nothing to the ledger, which is why
  Principles I–IV do not engage.
