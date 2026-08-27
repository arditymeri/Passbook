import { useEffect, useState, useCallback } from 'react';
import { fetchRecurringSeries } from '../api/client';
import type { RecurringSeries } from '../types';

interface UseRecurringSeriesResult {
  series: RecurringSeries[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useRecurringSeries(): UseRecurringSeriesResult {
  const [series, setSeries] = useState<RecurringSeries[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchRecurringSeries()
      .then((result) => {
        if (cancelled) return;
        setSeries(result);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load recurring series — please try again');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [refreshKey]);

  return { series, loading, error, refresh };
}
