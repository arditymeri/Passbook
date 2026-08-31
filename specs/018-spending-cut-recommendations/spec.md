# Feature Specification: Spending Cut Recommendations

**Feature Branch**: `018-spending-cut-recommendations`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "Help users identify spending they can cut. Surface actionable opportunities to reduce spend by analyzing data the app already computes: recurring subscriptions/bills (from the existing recurring-series detection) ranked by cost, especially ones that are small individually but add up, or where the price has crept up over time; discretionary categories that are trending upward (from spending trends) or that consistently run over their budgeted amount (from envelope budgeting); and a summed 'potential monthly savings' if flagged items were cut or reduced back to a typical/target level. This is about turning existing analytics into a prioritized, actionable list rather than requiring the user to spot patterns themselves across separate dashboards."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See recurring costs ranked by size (Priority: P1)

A user wants to know which of their confirmed recurring bills and subscriptions cost them the most each month, so they can decide which ones are worth keeping. They open the recommendations view and see every confirmed recurring series listed from highest to lowest monthly cost, along with the total they spend on recurring items every month.

**Why this priority**: This is the core, highest-value slice — it turns data the app already has (confirmed recurring series) into a single prioritized list without requiring any new analysis logic beyond ranking. It delivers value on its own even before price-creep or budget signals are added.

**Independent Test**: Confirm three or more recurring bill series with different amounts, open the recommendations view, and verify they appear ordered from most to least expensive with a correct running total.

**Acceptance Scenarios**:

1. **Given** three confirmed recurring bill series costing 15, 45, and 8 per month, **When** the user opens the recommendations view, **Then** the items appear in order 45, 15, 8, and the displayed total recurring spend is 68.
2. **Given** a recurring series that is still only "proposed" (not yet confirmed) and one that is confirmed, **When** the user opens the recommendations view, **Then** only the confirmed series appears in the ranked list.
3. **Given** no recurring series have been confirmed yet, **When** the user opens the recommendations view, **Then** the view explains there are no recurring costs to show yet instead of an empty or broken list.

---

### User Story 2 - Tag transactions by necessity (Priority: P2)

A user reviewing their spending wants to mark individual transactions with their own judgment of whether the money was well spent — Necessary, Avoidable, or Unnecessary. They can apply this tag wherever they view a bill transaction. The recommendations view then lists every Avoidable- or Unnecessary-tagged transaction from the recent period and folds its amount into the potential savings total, alongside the automatically-detected recurring and category signals.

**Why this priority**: A tag the user applies themselves is the most direct, highest-confidence "I could have skipped this" signal the app can get — more reliable than any inference — so it belongs early, right after the recurring-cost ranking that gives the feature its initial shape.

**Independent Test**: Tag a handful of bill transactions with each of the three labels, open the recommendations view, and verify the Avoidable/Unnecessary-tagged transactions and their total amount are shown, while the Necessary-tagged one is excluded.

**Acceptance Scenarios**:

1. **Given** an untagged bill transaction, **When** the user opens it and selects "Unnecessary", **Then** the tag is saved and reflected wherever that transaction is shown.
2. **Given** three bill transactions this month tagged Unnecessary (12), Avoidable (20), and Necessary (50), **When** the user opens the recommendations view, **Then** the Unnecessary and Avoidable transactions (totaling 32) are listed and counted toward potential savings, and the Necessary one is not.
3. **Given** a transaction previously tagged Avoidable, **When** the user changes its tag to Necessary, **Then** it no longer appears in the recommendations view.
4. **Given** a bill transaction the user has never tagged, **When** they open the recommendations view, **Then** that transaction does not appear in the tag-based section (untagged is not treated as unnecessary).

---

### User Story 3 - Catch recurring charges that have crept up in price (Priority: P3)

A user wants to know which of their recurring bills or subscriptions have gotten more expensive since they started, so they can question or renegotiate the price increase. For each confirmed recurring series where the most recent charge is higher than its earliest recorded charge, the view calls out the original amount, the current amount, and the size of the increase.

**Why this priority**: Price creep is easy to miss charge-by-charge but adds up over time; surfacing it explicitly is high-value but depends on User Story 1's ranked list already existing as the place to show it.

**Independent Test**: Confirm a recurring series whose amount increased between its first and most recent occurrence, open the recommendations view, and verify the increase is called out with the correct before/after amounts.

**Acceptance Scenarios**:

1. **Given** a confirmed recurring series billed at 10 originally and 13 most recently, **When** the user opens the recommendations view, **Then** that series is flagged with "price increased" showing 10 → 13 (a 3 increase).
2. **Given** a confirmed recurring series whose amount has stayed the same across all occurrences, **When** the user opens the recommendations view, **Then** that series is not flagged as a price increase.
3. **Given** a confirmed recurring series whose amount has decreased since it started, **When** the user opens the recommendations view, **Then** that series is not flagged as a price increase (a price decrease is not a cut opportunity).

---

### User Story 4 - Spot categories trending up or running over budget (Priority: P4)

A user wants to know which everyday spending categories (like Dining Out or Groceries) are costing more than usual, so they know where to cut back. The view highlights categories whose recent spend is trending upward compared to their own recent history, and categories that exceeded their budgeted limit in the most recently completed month, each with the excess amount versus their typical/target level.

**Why this priority**: This extends the recommendations beyond recurring bills into everyday discretionary spending, but it's lower priority than Stories 1-2 because it depends on the user already having budgets and enough spending history for a trend to be meaningful.

**Independent Test**: Set a budget for a category, record spend in that category above the budget for the most recent completed month, open the recommendations view, and verify the category appears with the correct excess amount.

**Acceptance Scenarios**:

1. **Given** a category budgeted at 150 that was actually spent at 210 last month, **When** the user opens the recommendations view, **Then** that category appears flagged with an excess of 60.
2. **Given** a category with no budget set but whose spend has been trending upward over recent months, **When** the user opens the recommendations view, **Then** that category still appears flagged, based on the trend rather than the budget.
3. **Given** a category that is under budget and not trending upward, **When** the user opens the recommendations view, **Then** that category does not appear in this section.

---

### Edge Cases

- What happens when a user has no confirmed recurring series, no budgets, no tagged transactions, and no spending history at all? The view shows a clear empty state rather than a blank or broken screen.
- How does the system handle a recurring series or category that doesn't have enough history to produce a reliable estimate (e.g., a series with only two occurrences, or a category with a budget but no prior-month actuals)? It is left out of the recommendations rather than shown with a fabricated or misleading number.
- What happens when every flagged item's cut would eliminate 100% of a recurring cost, versus a category excess where only part of the spend is "excess"? The potential-savings total sums whichever amount each item type contributes (full recurring cost, the tagged amount, or the excess-over-typical amount) rather than double-counting.
- What happens when the same category's overspend is both "trending up" and "over budget" in the same period? It is shown once, not twice, in the category section.
- What happens if a user tries to tag an income transaction? Necessity tagging only applies to bill (expense) transactions, since the feature is about spend that could be cut.
- What happens to a transaction's necessity tag when the transaction's amount is later corrected? The tag stays attached to the transaction. If the transaction is deleted, its tag is discarded along with it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST list every confirmed recurring bill series with its current (most recent) monthly amount.
- **FR-002**: System MUST rank confirmed recurring series from highest to lowest monthly amount.
- **FR-003**: System MUST display the total monthly spend across all confirmed recurring series.
- **FR-004**: Users MUST be able to tag any individual bill transaction with exactly one necessity label: Necessary, Avoidable, or Unnecessary.
- **FR-005**: Users MUST be able to change or remove a transaction's necessity tag at any time.
- **FR-006**: System MUST persist each transaction's necessity tag so it is retained across sessions and shown consistently everywhere that transaction appears.
- **FR-007**: System MUST list, in the recommendations view, every bill transaction tagged Avoidable or Unnecessary within the recent period, together with its amount.
- **FR-008**: System MUST NOT include Necessary-tagged or untagged transactions in the tag-based section of the recommendations view.
- **FR-009**: System MUST detect, for each confirmed recurring series, whether its most recent occurrence amount is higher than its earliest recorded occurrence amount, and if so flag it as a price increase showing the original amount, the current amount, and the size of the increase.
- **FR-010**: System MUST NOT flag a recurring series as a price increase when its amount has stayed the same or decreased.
- **FR-011**: System MUST identify expense categories whose actual spend in the most recently completed month exceeds their budgeted limit for that month, using existing budget data, and show the excess amount (actual minus budgeted).
- **FR-012**: System MUST identify expense categories whose spend is trending upward across recent months (using the same comparison window as the existing spending-trends feature), independent of whether a budget exists, and show the excess amount versus that category's own recent typical spend.
- **FR-013**: System MUST NOT list the same category more than once in the same view when it qualifies under both the over-budget and trending-up conditions in the same period.
- **FR-014**: System MUST display one combined "potential monthly savings" total, summing the monthly cost of every flagged recurring series, the amount of every Avoidable- or Unnecessary-tagged transaction in the recent period, and the excess amount of every flagged category.
- **FR-015**: System MUST exclude any recurring series or category that lacks enough underlying data to produce a reliable figure (proposed-but-not-confirmed series; a category with no budget and no prior-month history) rather than displaying an estimate for it.
- **FR-016**: System MUST compute the recurring, category, and combined-savings recommendations automatically from existing bills, income, budgets, and recurring-series data, requiring no manual data entry beyond the necessity tags the user chooses to apply.
- **FR-017**: System MUST recompute recommendations from current data, including current tags, each time the view is opened, rather than showing a stale, previously cached result.
- **FR-018**: System MUST present an explanatory empty state when there is not yet enough data (no confirmed recurring series, no tagged transactions, and no flagged categories) to show any recommendation.

### Key Entities

- **Transaction Necessity Tag**: A label attached to one bill transaction — Necessary, Avoidable, or Unnecessary — reflecting the user's own judgment of whether the expense was worth it. Persisted, and editable at any time.
- **Recurring Cost Opportunity**: A derived (not persisted) view of one confirmed recurring series — its description, current monthly amount, and, when applicable, its original amount and price-increase size. Ranked by current monthly amount.
- **Category Spending Opportunity**: A derived view of one expense category currently over budget and/or trending upward — the category, its actual spend, its budgeted or typical baseline, and the excess amount versus that baseline.
- **Potential Savings Summary**: A derived total combining the monthly cost of all flagged recurring items, the amount of all Avoidable/Unnecessary-tagged transactions, and the excess amount of all flagged categories.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can see their full prioritized list of cost-cutting opportunities on a single screen, without cross-referencing separate budget, trends, and recurring-transaction pages themselves.
- **SC-002**: Recurring items are always ordered strictly from highest to lowest monthly cost, verified against at least three items of different amounts.
- **SC-003**: Every recurring series whose most recent amount is higher than its earliest recorded amount is called out with the correct original amount, current amount, and increase size, with zero false negatives or false positives in a data set of known price changes.
- **SC-004**: A user sees one clear number representing the total they could plausibly save per month by acting on every flagged item, including their own tagged transactions.
- **SC-005**: No recurring series or category lacking sufficient history is ever shown with a fabricated figure — items without enough data are always omitted, not estimated.
- **SC-006**: A user can tag a transaction and see it reflected in the recommendations view without extra manual steps beyond opening that view.
- **SC-007**: The potential-savings total always includes the full amount of every Avoidable- or Unnecessary-tagged transaction from the recent period, verified against a data set of known tags.

## Assumptions

- Only recurring series with status "confirmed" are treated as recurring for this feature; "proposed" series are excluded until the user confirms them, consistent with how the existing cash-flow forecast and upcoming-recurring features already treat series status.
- "Typical/target level" for a trending-up category is that category's own recent historical average, reusing the same comparison window already established by the existing Spending Trends feature, rather than an externally defined benchmark.
- This version is read-only for the automatically-detected recurring and category signals: there is no dismiss/snooze action on those. The one user-driven action in scope is applying/changing a necessity tag on a bill transaction. Acting further on a recommendation (cancelling a subscription, adjusting a budget) happens through the existing bill, category, and budget screens.
- The necessity tag is a fixed three-value label (Necessary, Avoidable, Unnecessary) rather than free-form user-defined tags, consistent with how the app already models fixed choices elsewhere (category type, budget status, recurring-series status). It applies only to bill (expense) transactions, not income.
- Necessity tagging is the one piece of new backend persistence this feature introduces (a tag per bill transaction); the recurring, price-creep, and category recommendations remain computed on demand from existing bills, income, budgets, and recurring-series data, mirroring how the existing Spending Trends and Cash Flow Forecast features are built.
- "Recent period" for tag-based savings reuses the same recent-months window as the trending-category logic, so all three signal types (recurring, tags, categories) reason about a consistent time horizon.
- "Expense categories" means categories of type EXPENSE (and BOTH, where applicable); income categories are out of scope for cut recommendations.
