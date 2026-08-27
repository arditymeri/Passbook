import { useEffect, useState, useCallback } from 'react';
import { fetchBudgets, fetchBudgetStatusResponse } from '../api/client';
import type { Allocation, BudgetStatusEntry } from '../types';

interface UseBudgetAllocationsResult {
  allocations: Allocation[];
  entries: BudgetStatusEntry[];
  unallocated: number;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useBudgetAllocations(year: number, month: number): UseBudgetAllocationsResult {
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [entries, setEntries] = useState<BudgetStatusEntry[]>([]);
  const [unallocated, setUnallocated] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([fetchBudgets(year, month), fetchBudgetStatusResponse(year, month)])
      .then(([budgetsResult, statusResult]) => {
        if (cancelled) return;
        setAllocations(budgetsResult);
        setEntries(statusResult.entries ?? []);
        setUnallocated(statusResult.unallocated ?? 0);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load budgeting data — please try again');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [year, month, refreshKey]);

  return { allocations, entries, unallocated, loading, error, refresh };
}
