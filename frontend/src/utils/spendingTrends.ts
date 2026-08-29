import type {
  CategoryNameMap,
  CategorySpendingTrend,
  SpendingMover,
  SpendingTrendRangeMonths,
  Transaction,
} from '../types';

function monthLabelUtc(year: number, month0: number): string {
  return new Date(Date.UTC(year, month0, 1)).toLocaleDateString('en', { month: 'short', timeZone: 'UTC' });
}

function lastInstantOfMonthUtc(year: number, month0: number): Date {
  return new Date(Date.UTC(year, month0 + 1, 1, 0, 0, 0, 0) - 1);
}

function monthKey(year: number, month0: number): string {
  return `${year}-${month0}`;
}

function categoryDisplayName(categoryId: string, categoryNames: CategoryNameMap): string {
  return categoryNames.get(categoryId) ?? categoryId;
}

export function computeSpendingTrends(
  allTransactions: Transaction[],
  categoryNames: CategoryNameMap,
  monthsBack: SpendingTrendRangeMonths
): { trends: CategorySpendingTrend[]; movers: SpendingMover[] } {
  const now = new Date();
  const nowYear = now.getUTCFullYear();
  const nowMonth0 = now.getUTCMonth();

  // One pass over allTransactions, bucketing every BILL transaction's amount into
  // perCategoryPerMonth[categoryId][year-month0], regardless of the selected window — movers
  // needs the fixed current/previous month totals independent of how far back the trend looks.
  const perCategoryPerMonth = new Map<string, Map<string, number>>();
  for (const t of allTransactions) {
    if (t.type !== 'BILL' || !t.categoryId) continue;
    const d = new Date(t.time);
    const key = monthKey(d.getUTCFullYear(), d.getUTCMonth());
    let byMonth = perCategoryPerMonth.get(t.categoryId);
    if (!byMonth) {
      byMonth = new Map<string, number>();
      perCategoryPerMonth.set(t.categoryId, byMonth);
    }
    byMonth.set(key, (byMonth.get(key) ?? 0) + t.amount);
  }

  // The window: monthsBack months ending at the current UTC month, oldest to newest.
  const windowMonths: { year: number; month0: number; label: string; cutoff: string; key: string }[] = [];
  for (let i = monthsBack - 1; i >= 0; i--) {
    const targetMonth0 = nowMonth0 - i;
    const cutoffDate = i === 0 ? now : lastInstantOfMonthUtc(nowYear, targetMonth0);
    // Date.UTC normalizes an out-of-range month (negative or >11) into the correct adjacent year,
    // so derive the window month's actual year from the same construction used for the cutoff.
    const normalized = new Date(Date.UTC(nowYear, targetMonth0, 1));
    windowMonths.push({
      year: normalized.getUTCFullYear(),
      month0: normalized.getUTCMonth(),
      label: monthLabelUtc(normalized.getUTCFullYear(), normalized.getUTCMonth()),
      cutoff: cutoffDate.toISOString(),
      key: monthKey(normalized.getUTCFullYear(), normalized.getUTCMonth()),
    });
  }

  const trends: CategorySpendingTrend[] = [];
  for (const [categoryId, byMonth] of perCategoryPerMonth) {
    const points = windowMonths.map((m) => ({
      label: m.label,
      cutoff: m.cutoff,
      amount: byMonth.get(m.key) ?? 0,
    }));
    if (points.every((p) => p.amount === 0)) continue;
    trends.push({ categoryId, categoryName: categoryDisplayName(categoryId, categoryNames), points });
  }

  const currentKey = monthKey(nowYear, nowMonth0);
  const previousNormalized = new Date(Date.UTC(nowYear, nowMonth0 - 1, 1));
  const previousKey = monthKey(previousNormalized.getUTCFullYear(), previousNormalized.getUTCMonth());

  const movers: SpendingMover[] = [];
  for (const [categoryId, byMonth] of perCategoryPerMonth) {
    const currentAmount = byMonth.get(currentKey) ?? 0;
    const previousAmount = byMonth.get(previousKey) ?? 0;
    if (currentAmount === previousAmount) continue;
    const change = currentAmount - previousAmount;
    movers.push({
      categoryId,
      categoryName: categoryDisplayName(categoryId, categoryNames),
      previousAmount,
      currentAmount,
      change,
      percentChange: previousAmount === 0 ? null : (change / previousAmount) * 100,
    });
  }
  movers.sort((a, b) => Math.abs(b.change) - Math.abs(a.change));

  return { trends, movers };
}
