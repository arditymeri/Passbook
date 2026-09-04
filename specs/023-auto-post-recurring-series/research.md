# Phase 0 Research: Auto-Post Confirmed Recurring Series

**Feature**: 023-auto-post-recurring-series | **Date**: 2026-09-04

Decisions reached against the actual codebase. The two findings that most shaped this design (R2 and
R3) were not visible from the spec and would each have produced a quietly wrong feature.

---

## R1: Posting is stateless — catch-up is not a separate mechanism

**Decision**: every run computes *all* occurrences currently due for a series and attempts to post
each. There is no "last run" timestamp, no cursor, and no catch-up code path.

The occurrences due for a series are: every date on its cadence strictly after its **latest real
occurrence**, up to and including today. Feature 022's uniqueness guarantee means attempting one that
is already posted writes nothing. So:

| Situation | What happens | Why |
|---|---|---|
| Ran an hour ago | Same set recomputed, all already present | Idempotent no-op |
| Off for three weeks | The whole gap recomputed and posted | Nothing to "remember" |
| Two runs overlap | Both compute the same set; the database arbitrates | 022's index |
| Operator triggers manually mid-run | Same | Same |

**Why this is better than tracking progress**: a `lastPostedThrough` timestamp is state that can be
wrong — written but not committed, committed but the posting rolled back, reset by a restore from
backup. Every one of those failure modes silently skips or repeats an operator's rent. Deriving the
due set from the ledger itself has no such state to corrupt.

**Accepted cost**: a monthly series a year past its last real occurrence re-attempts twelve inserts
per run. That is one round trip of a dozen rows against an indexed column, daily. Not worth
optimising away, and certainly not worth introducing state for.

---

## R2: Auto-posted rows must not become the anchor — the feedback loop

**This is the finding that would have silently broken the feature.**

`RecurringSeriesMembers.membersOf` returns every bill (or income) matching the series' group key and
normalised description. It filters nothing else. Once this feature starts writing transactions, its
own predictions match that filter and become "occurrences" of the series.

Left alone, that gives a system that learns from its own guesses:

- **The anchor drifts onto a prediction.** Next month's due date is computed from a date the app
  invented, not from anything that happened.
- **The amount perpetuates.** Each post copies the previous post's figure, so a rent increase is
  never picked up even after the bank reports it — the app is averaging its own output.
- **Reversal rows count as occurrences.** After a supersession (US2), the compensating entry also
  matches the description filter and is treated as another occurrence of the series.

**Decision**: the anchor and the derived values come from a filtered view — occurrences that are
neither auto-posted nor reversals. A prediction is never evidence.

**Why this reads oddly but is right**: the anchor then stays put until a *real* transaction arrives,
so the due set grows by one each period. That is fine, and self-correcting: every already-posted
period is a no-op (R1), and the moment an import brings the bank's real rent, the anchor jumps
forward to it. The system converges on fact and never on its own output.

**Decision on where the filter lives**: a new, dedicated method rather than changing `membersOf`.
That method is shared with feature 015's forecast and 018's recurring dashboard, and for *those*
features an auto-posted transaction genuinely is an occurrence — it is a real ledger entry. Changing
shared semantics to suit this feature would quietly alter two others.

---

## R3: For a bill series the category is already known

`membersOf` groups a bill series by `series.groupKey == bill.categoryId`. The group key *is* the
category. For incomes the group key is `source.name()`, and incomes carry no category at all.

So FR-006's "derive the values from past occurrences" needs less derivation than the spec assumed:

| Value | Source |
|---|---|
| Category (bills) | The series' group key — no derivation |
| Account | Latest real occurrence's `accountId` |
| Amount | Latest real occurrence's `amount` |
| Description | The series' own description |
| Direction | The series' `transactionType` |

`MemberOccurrence` already carries `(id, time, amount, accountId)` — everything needed, with no
change to the record.

**FR-006 is a refusal requirement and this is where it bites**: a series whose filtered occurrence
list is empty has no account and no amount. It MUST be skipped. This is reachable in practice — a
series every one of whose occurrences was auto-posted and then superseded — so it is a real branch,
not a defensive one.

---

## R4: How far back posting may reach

**Decision**: an occurrence is posted only if its due date is **on or after the date the series was
confirmed**, as well as after the latest real occurrence and not in the future.

Without the confirmation bound, confirming a series detected from two-year-old data would
immediately fabricate twenty-four months of rent. Detection looks backwards over existing history;
posting must not.

**Confirmation is consent, and consent is not retroactive.** The operator confirming a series today
is saying "expect this from now on", not "invent the past". The bound is drawn there because that is
where the operator's intent actually starts.

**Where the date comes from**: the series' `updatedAt`, which the confirm path sets when it moves the
status to `CONFIRMED`. That is approximate — any later edit moves it — but it errs toward posting
*less*, which is the safe direction: a wrongly-late bound skips a period the operator can enter by
hand; a wrongly-early one invents transactions they never had.

---

## R5: Superseding, and where reconciliation hooks in

**Decision**: reconciliation runs inside the ingestion path (feature 022's
`IngestTransactionsService`), after rows are recorded. It does **not** run when a transaction is
entered by hand.

**Why not on manual entry** — the question left open at the end of spec-writing. A hand-entered
transaction is the operator typing while looking at their own history, where the auto-posted row is
visible and marked as such. Silently reversing something because they typed a similar amount would be
the app second-guessing a person who can already see the situation. An imported booking is different:
it arrives in bulk, unattended, and the operator never sees the collision happen. If manual entry
should reconcile too, it is a small extension to the same service — but doing it now would be
guessing at intent the spec does not express.

**How supersession is written**: the existing correction path (feature 008). A compensating reversal
referencing the auto-posted transaction, exactly as a manual correction produces. Nothing new is
invented, Principle I holds by construction, and existing history views already know how to explain
a reversal.

**Matching rule** (FR-007, FR-011): an incoming transaction supersedes an auto-posted one when it is
on the same account, belongs to the same series, falls within the cadence tolerance of the posted
occurrence's date, and is within the amount tolerance. Both tolerances are
`RecurringMatching`'s existing ones — a 5% proportional band with a €2.00 absolute floor, and a
cadence-scaled timing window. **Reusing them is deliberate**: a second, different notion of "close
enough" would let the app recognise a series it then refuses to reconcile, which is the kind of
inconsistency nobody would ever debug successfully.

Ambiguity is resolved by closest date, ties to the earlier period (FR-011), and an entry already
superseded is not eligible (FR-010).

---

## R6: Telling the three origins apart

There are now three ways a transaction can exist, and they must be distinguishable (FR-012, FR-013):

| Origin | How it is recognised |
|---|---|
| Entered by hand | `external_id` null, `recurring_series_id` null |
| Imported from a statement | `external_id` set, `recurring_series_id` null |
| Auto-posted | `recurring_series_id` set |

**Decision**: `V3` adds a nullable `recurring_series_id` to `bill` and `income`. The external identity
stays `recurring:<seriesId>:<period-date>`, which is what 022's unique index enforces.

**Why a column rather than parsing the identity string**: the identity already encodes series and
period, and it would be tempting to read them back out of it. That makes a string format into a
schema — every reader would have to agree on the prefix, and a future change to the identity scheme
would break code that has nothing to do with identity. An indexed column also makes "find this
series' auto-posted rows for reconciliation" a real query rather than a `LIKE 'recurring:%'` scan.

**Why not a third `origin` enum column**: it would be derivable from the two nullability facts above,
and a second encoding of the same truth is a second thing that can disagree. Feature 022 rejected an
`origin` column on the same grounds; that reasoning still holds with three origins.

The period is recoverable from the transaction's own date, since an auto-posted transaction is dated
at its occurrence. No second column needed.

---

## R7: Where the schedule lives, and keeping the logic testable

**Decision**: the scheduled trigger goes in `Launcher`, alongside `DemoDataSeeder` — the existing
precedent for a non-HTTP driver in this project. `@EnableScheduling` is new to the codebase; nothing
uses `@Scheduled` today.

**Decision that matters more**: the Domain service takes the date to post up to as a parameter.

```
postDueOccurrences(LocalDate today)   not   postDueOccurrences()
```

Everything interesting about this feature is calendar behaviour — three weeks of downtime, a monthly
series due on the 31st, the confirmation bound — and none of it is testable if "today" is read from
the system clock inside the logic. With the date as a parameter, all of it is plain JUnit and runs
in this sandbox with no database and no scheduler.

**On the 31st** (FR-017): stepping a month from 31 January with `java.time` yields 28 or 29 February
— the last valid day — and never throws or skips. That is the deterministic answer FR-017 asks for,
and it comes free rather than needing a rule of our own.

---

## R8: Risks to check early

1. **`@EnableScheduling` in a test context.** Every integration test boots the full application. If
   the scheduler starts there, posting fires during unrelated tests and pollutes the shared database
   — plausibly making other features' assertions flaky in ways that look unrelated. The schedule must
   be disabled by default in tests, or gated behind a property.
2. **The reconciliation hook changes 022's ingestion result.** Rows that supersede something are
   still `RECORDED`; the supersession is an additional effect. Existing ingestion tests must stay
   green, which is the check that this was done additively.
3. **`membersOf` is shared.** Feature 015's forecast and 018's dashboard both call it. The new
   filtered view must be additive (R2), and both features' tests must still pass.
4. **Demo data.** `app.demo-data.enabled` seeds a confirmed recurring series. On a fresh instance the
   scheduler will then post against it — which is arguably a nice demonstration, but it must be
   *intended* rather than a surprise, and it must not make the seeded data look wrong.

**Verification reality**: unchanged from 021 and 022 — no Docker daemon here. The good news is that
this feature's hard parts are calendar arithmetic and matching rules, all pure Domain logic that runs
locally. The `V3` migration, the reconciliation-on-import path, and the scheduler are CI-only.
