import type {
  BudgetStatusEntry,
  CategoryNameMap,
  CategoryOpportunityReason,
  CategorySpendingOpportunity,
  RecurringCostSummaryItem,
  SpendingCutRecommendations,
  SpendingMover,
  TaggedTransactionOpportunity,
  Transaction,
} from '../types';

export function computeSpendingCutRecommendations(
  recurringItems: RecurringCostSummaryItem[],
  allTransactions: Transaction[] = [],
  budgetStatusEntries: BudgetStatusEntry[] = [],
  categoryTrendMovers: SpendingMover[] = [],
  categoryNames: CategoryNameMap = new Map()
): SpendingCutRecommendations {
  const rankedRecurring = [...recurringItems].sort(
    (a, b) => b.monthlyEquivalentAmount - a.monthlyEquivalentAmount
  );
  const totalMonthlyRecurringSpend = rankedRecurring.reduce(
    (sum, item) => sum + item.monthlyEquivalentAmount,
    0
  );

  const taggedTransactions: TaggedTransactionOpportunity[] = allTransactions
    .filter((t) => t.type === 'BILL' && (t.necessityTag === 'AVOIDABLE' || t.necessityTag === 'UNNECESSARY'))
    .map((t) => ({
      transactionId: t.id,
      description: t.description,
      amount: t.amount,
      tag: t.necessityTag as 'AVOIDABLE' | 'UNNECESSARY',
    }));
  const taggedTransactionsTotal = taggedTransactions.reduce((sum, t) => sum + t.amount, 0);

  const categoryOpportunities = mergeCategoryOpportunities(budgetStatusEntries, categoryTrendMovers, categoryNames);
  const categoryOpportunitiesTotal = categoryOpportunities.reduce((sum, c) => sum + c.excessAmount, 0);

  return {
    recurringItems: rankedRecurring,
    totalMonthlyRecurringSpend,
    taggedTransactions,
    categoryOpportunities,
    potentialMonthlySavings: totalMonthlyRecurringSpend + taggedTransactionsTotal + categoryOpportunitiesTotal,
  };
}

/**
 * A category qualifying under both over-budget and trending-up signals is emitted once, with the
 * larger of the two excess amounts (FR-008/FR-013) — never double-counted in the combined total.
 */
function mergeCategoryOpportunities(
  budgetStatusEntries: BudgetStatusEntry[],
  categoryTrendMovers: SpendingMover[],
  categoryNames: CategoryNameMap
): CategorySpendingOpportunity[] {
  const byCategory = new Map<string, { excessAmount: number; reason: CategoryOpportunityReason }>();

  for (const entry of budgetStatusEntries) {
    if (entry.status !== 'OVER_BUDGET') continue;
    const excess = entry.actual - entry.budgeted;
    if (excess <= 0) continue;
    byCategory.set(entry.categoryId, { excessAmount: excess, reason: 'OVER_BUDGET' });
  }

  for (const mover of categoryTrendMovers) {
    if (mover.change <= 0) continue;
    const existing = byCategory.get(mover.categoryId);
    if (!existing) {
      byCategory.set(mover.categoryId, { excessAmount: mover.change, reason: 'TRENDING_UP' });
    } else {
      byCategory.set(mover.categoryId, {
        excessAmount: Math.max(existing.excessAmount, mover.change),
        reason: 'BOTH',
      });
    }
  }

  return Array.from(byCategory.entries()).map(([categoryId, { excessAmount, reason }]) => ({
    categoryId,
    categoryName: categoryNames.get(categoryId) ?? categoryId,
    excessAmount,
    reason,
  }));
}
