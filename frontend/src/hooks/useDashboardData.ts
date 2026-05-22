import { useEffect, useState } from 'react';
import {
  fetchBills,
  fetchBudgetStatus,
  fetchCategories,
  fetchIncomes,
  fetchMonthlySummary,
} from '../api/client';
import type {
  BudgetStatusEntry,
  CategoryNameMap,
  DashboardData,
  MonthlySummary,
  Transaction,
} from '../types';

function inMonth(isoTime: string, year: number, month: number): boolean {
  const d = new Date(isoTime);
  return d.getFullYear() === year && d.getMonth() + 1 === month;
}

export function useDashboardData(year: number, month: number): DashboardData {
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [budgetEntries, setBudgetEntries] = useState<BudgetStatusEntry[]>([]);
  const [budgetLoading, setBudgetLoading] = useState(true);
  const [budgetError, setBudgetError] = useState<string | null>(null);

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [transactionsLoading, setTransactionsLoading] = useState(true);
  const [transactionsError, setTransactionsError] = useState<string | null>(null);

  const [categoryNames, setCategoryNames] = useState<CategoryNameMap>(new Map());
  const [categoriesLoading, setCategoriesLoading] = useState(true);

  // Fetch categories once on mount
  useEffect(() => {
    fetchCategories()
      .then((cats) => {
        const map: CategoryNameMap = new Map(cats.map((c) => [c.id, c.name]));
        setCategoryNames(map);
      })
      .catch(() => {/* non-fatal: fallback to IDs */})
      .finally(() => setCategoriesLoading(false));
  }, []);

  // Fetch all period data in parallel when year/month changes
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
      } else {
        const billTxns: Transaction[] = bills
          .filter((b) => inMonth(b.time, year, month))
          .map((b) => ({ id: b.id, description: b.description, amount: b.amount, time: b.time, type: 'BILL' as const }));
        const incomeTxns: Transaction[] = incomes
          .filter((i) => inMonth(i.time, year, month))
          .map((i) => ({ id: i.id, description: i.description, amount: i.amount, time: i.time, type: 'INCOME' as const }));
        const merged = [...billTxns, ...incomeTxns]
          .sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime())
          .slice(0, 10);
        setTransactions(merged);
      }
      setTransactionsLoading(false);
    });
  }, [year, month]);

  return {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, transactionsLoading, transactionsError,
    categoryNames, categoriesLoading,
  };
}
