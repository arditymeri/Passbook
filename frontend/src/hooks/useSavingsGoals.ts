import { useEffect, useState, useCallback } from 'react';
import { fetchSavingsGoals } from '../api/client';
import type { SavingsGoalStatus } from '../types';

interface UseSavingsGoalsResult {
  goals: SavingsGoalStatus[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useSavingsGoals(): UseSavingsGoalsResult {
  const [goals, setGoals] = useState<SavingsGoalStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchSavingsGoals()
      .then((result) => {
        if (cancelled) return;
        setGoals(result);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load savings goals — please try again');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [refreshKey]);

  return { goals, loading, error, refresh };
}
