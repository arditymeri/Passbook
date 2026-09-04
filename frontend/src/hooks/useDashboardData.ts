import { useEffect, useState } from 'react';
import {
  fetchAccounts,
  fetchBills,
  fetchBudgetStatus,
  fetchCategories,
  fetchIncomes,
  fetchMonthlySummary,
} from '../api/client';
import type {
  Account,
  BudgetStatusEntry,
  Category,
  CategoryNameMap,
  DashboardData,
  MonthlySummary,
  Transaction,
} from '../types';

function inMonth(isoTime: string, year: number, month: number): boolean {
  const d = new Date(isoTime);
  return d.getFullYear() === year && d.getMonth() + 1 === month;
}

export function useDashboardData(year: number, month: number, refreshKey: number = 0): DashboardData {
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [budgetEntries, setBudgetEntries] = useState<BudgetStatusEntry[]>([]);
  const [budgetLoading, setBudgetLoading] = useState(true);
  const [budgetError, setBudgetError] = useState<string | null>(null);

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [allTransactions, setAllTransactions] = useState<Transaction[]>([]);
  const [transactionsLoading, setTransactionsLoading] = useState(true);
  const [transactionsError, setTransactionsError] = useState<string | null>(null);

  const [categoryNames, setCategoryNames] = useState<CategoryNameMap>(new Map());
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(true);

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountsLoading, setAccountsLoading] = useState(true);

  // Fetch categories once on mount
  useEffect(() => {
    fetchCategories()
      .then((cats) => {
        setCategories(cats);
        const map: CategoryNameMap = new Map(cats.map((c) => [c.id, c.name]));
        setCategoryNames(map);
      })
      .catch(() => {/* non-fatal: fallback to IDs */})
      .finally(() => setCategoriesLoading(false));
  }, []);

  // Fetch accounts once on mount
  useEffect(() => {
    fetchAccounts()
      .then(setAccounts)
      .catch(() => {/* non-fatal: forms fall back to "No account" only */})
      .finally(() => setAccountsLoading(false));
  }, []);

  // Fetch all period data in parallel when year, month, or refreshKey changes
  useEffect(() => {
    setSummaryLoading(true);
    setSummaryError(null);
    setBudgetLoading(true);
    setBudgetError(null);
    setTransactionsLoading(true);
    setTransactionsError(null);

    Promise.allSettled([
      fetchMonthlySummary(year, month),
      fetchBudgetStatus(year, month),
      fetchBills(),
      fetchIncomes(),
    ]).then(([summaryResult, budgetResult, billsResult, incomesResult]) => {
      if (summaryResult.status === 'fulfilled') {
        setSummary(summaryResult.value);
      } else {
        setSummaryError('Could not load summary');
        setSummary(null);
      }
      setSummaryLoading(false);

      if (budgetResult.status === 'fulfilled') {
        setBudgetEntries(budgetResult.value);
      } else {
        setBudgetError('Could not load budget data');
        setBudgetEntries([]);
      }
      setBudgetLoading(false);

      const bills = billsResult.status === 'fulfilled' ? billsResult.value : [];
      const incomes = incomesResult.status === 'fulfilled' ? incomesResult.value : [];

      if (billsResult.status === 'rejected' && incomesResult.status === 'rejected') {
        setTransactionsError('Could not load transactions');
        setTransactions([]);
        setAllTransactions([]);
      } else {
        const billTxns: Transaction[] = bills.map((b) => ({
          id: b.id,
          description: b.description,
          amount: b.amount,
          time: b.time,
          type: 'BILL' as const,
          categoryId: b.categoryId ?? undefined,
          accountId: b.accountId ?? undefined,
          correctsTransactionId: b.correctsTransactionId ?? undefined,
          recurringSeriesId: b.recurringSeriesId ?? undefined,
          necessityTag: b.necessityTag ?? undefined,
        }));
        const incomeTxns: Transaction[] = incomes.map((i) => ({
          id: i.id,
          description: i.description,
          amount: i.amount,
          time: i.time,
          type: 'INCOME' as const,
          accountId: i.accountId ?? undefined,
          source: i.source ?? undefined,
          correctsTransactionId: i.correctsTransactionId ?? undefined,
          recurringSeriesId: i.recurringSeriesId ?? undefined,
        }));
        const merged = [...billTxns, ...incomeTxns]
          .sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime());
        setAllTransactions(merged);
        setTransactions(merged.filter((t) => inMonth(t.time, year, month)).slice(0, 10));
      }
      setTransactionsLoading(false);
    });
  }, [year, month, refreshKey]);

  return {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, allTransactions, transactionsLoading, transactionsError,
    categoryNames, categories, categoriesLoading,
    accounts, accountsLoading,
  };
}
