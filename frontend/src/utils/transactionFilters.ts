import type { Transaction, TransactionFilters } from '../types';

function normalize(s: string | null | undefined): string {
  return (s ?? '').trim().toLowerCase();
}

/**
 * startDate/endDate are plain YYYY-MM-DD calendar-date strings (the native value of
 * <input type="date">). Expanding to explicit UTC day boundaries here — rather than comparing
 * the raw date string directly against t.time's full ISO datetime — keeps the end date inclusive
 * of the whole day regardless of what time of day a transaction was recorded.
 */
export function filterTransactions(transactions: Transaction[], filters: TransactionFilters): Transaction[] {
  const searchText = normalize(filters.searchText);
  const startBound = filters.startDate !== undefined ? `${filters.startDate}T00:00:00.000Z` : undefined;
  const endBound = filters.endDate !== undefined ? `${filters.endDate}T23:59:59.999Z` : undefined;

  return transactions.filter((t) => {
    if (searchText !== '' && !normalize(t.description).includes(searchText)) return false;
    if (filters.categoryId !== undefined && t.categoryId !== filters.categoryId) return false;
    if (filters.source !== undefined && t.source !== filters.source) return false;
    if (filters.accountId !== undefined && t.accountId !== filters.accountId) return false;
    if (startBound !== undefined && t.time < startBound) return false;
    if (endBound !== undefined && t.time > endBound) return false;
    if (filters.minAmount !== undefined && t.amount < filters.minAmount) return false;
    if (filters.maxAmount !== undefined && t.amount > filters.maxAmount) return false;
    if (filters.type !== 'ALL' && t.type !== filters.type) return false;
    return true;
  });
}
