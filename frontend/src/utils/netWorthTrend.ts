import type { Account, NetWorthRangeMonths, NetWorthTrendPoint, Transaction } from '../types';

export function currentNetWorth(accounts: Account[]): number {
  return accounts.reduce((sum, a) => sum + a.balance, 0);
}

function lastInstantOfMonthUtc(year: number, month0: number): Date {
  // One millisecond before the 1st of the next month is the last instant of this one.
  // JS Date normalizes an out-of-range month (negative or >11) into the correct adjacent year.
  return new Date(Date.UTC(year, month0 + 1, 1, 0, 0, 0, 0) - 1);
}

function monthLabelUtc(year: number, month0: number): string {
  return new Date(Date.UTC(year, month0, 1)).toLocaleDateString('en', { month: 'short', timeZone: 'UTC' });
}

export function computeNetWorthTrend(
  accounts: Account[],
  allTransactions: Transaction[],
  monthsBack: NetWorthRangeMonths
): NetWorthTrendPoint[] {
  const total = currentNetWorth(accounts);
  const now = new Date();
  const nowYear = now.getUTCFullYear();
  const nowMonth0 = now.getUTCMonth();

  const points: NetWorthTrendPoint[] = [];
  for (let i = monthsBack - 1; i >= 0; i--) {
    const targetMonth0 = nowMonth0 - i;
    const cutoff = i === 0 ? now : lastInstantOfMonthUtc(nowYear, targetMonth0);
    const cutoffMillis = cutoff.getTime();
    const label = monthLabelUtc(nowYear, targetMonth0);

    let futureIncome = 0;
    let futureBills = 0;
    // Compared as parsed instants, not raw strings: a transaction time with zero sub-second
    // precision (e.g. midnight-only dates, common for bill/income entries) serializes without a
    // fractional part, which would otherwise sort incorrectly against a cutoff that always has one.
    for (const t of allTransactions) {
      if (new Date(t.time).getTime() <= cutoffMillis) continue;
      if (t.type === 'INCOME') futureIncome += t.amount;
      else futureBills += t.amount;
    }

    points.push({ label, cutoff: cutoff.toISOString(), netWorth: total - futureIncome + futureBills });
  }
  return points;
}
