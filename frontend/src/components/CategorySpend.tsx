import type { CategoryNameMap } from '../types';

interface CategorySpendProps {
  spendingByCategory: Record<string, number>;
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function CategorySpend({ spendingByCategory, categoryNames, loading, error }: CategorySpendProps) {
  if (loading) return <div className="card"><p className="loading">Loading…</p></div>;
  if (error) return <div className="card"><p className="error">{error}</p></div>;

  const entries = Object.entries(spendingByCategory).sort((a, b) => b[1] - a[1]);

  if (entries.length === 0) {
    return <div className="card"><p className="empty">No spending data for this month.</p></div>;
  }

  const maxAmount = entries[0][1];

  return (
    <div className="card">
      <h2 className="card-title">Spending by Category</h2>
      <ul className="category-list">
        {entries.map(([catId, amount]) => (
          <li key={catId} className="category-item">
            <div className="category-row">
              <span className="category-name" title={catId}>
                {categoryNames.get(catId) ?? catId}
              </span>
              <span className="category-amount">{fmt.format(amount)}</span>
            </div>
            <div className="progress-track">
              <div
                className="progress-fill"
                style={{ width: `${(amount / maxAmount) * 100}%` }}
              />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
