import { useEffect, useState, useCallback } from 'react';
import { fetchCategories } from '../api/client';
import type { Category, CategoryType } from '../types';

interface UseCategoriesResult {
  categories: Category[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useCategories(type?: CategoryType): UseCategoriesResult {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchCategories()
      .then((all) => {
        if (cancelled) return;
        const filtered = type ? all.filter((c) => c.type === type) : all;
        setCategories(filtered);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load categories — please try again');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [type, refreshKey]);

  return { categories, loading, error, refresh };
}
