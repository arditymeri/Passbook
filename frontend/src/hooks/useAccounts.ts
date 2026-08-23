import { useEffect, useState, useCallback } from 'react';
import { fetchAccounts } from '../api/client';
import type { Account, AccountType } from '../types';

interface UseAccountsResult {
  accounts: Account[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useAccounts(type?: AccountType): UseAccountsResult {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchAccounts()
      .then((all) => {
        if (cancelled) return;
        const filtered = type ? all.filter((a) => a.type === type) : all;
        setAccounts(filtered);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load accounts — please try again');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [type, refreshKey]);

  return { accounts, loading, error, refresh };
}
