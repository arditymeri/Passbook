import type { BudgetStatusEntry, CategoryNameMap } from '../types';

interface BudgetStatusProps {
  entries: BudgetStatusEntry[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function BudgetStatus({ entries, categoryNames, loading, error }: BudgetStatusProps) {
  if (loading) return <div className="card"><p className="loading">Loading…</p></div>;
  if (error) return <div className="card"><p className="error">{error}</p></div>;
  if (entries.length === 0) {
    return <div className="card"><p className="empty">No budget data for this month.</p></div>;
  }

  return (
    <div className="card">
      <h2 className="card-title">Budget vs. Actual</h2>
      <ul className="budget-list">
        {entries.map((entry) => (
          <li key={entry.categoryId} className="budget-item">
            <div className="budget-row">
              <span className="category-name" title={entry.categoryId}>
                {categoryNames.get(entry.categoryId) ?? entry.categoryId}
              </span>
              <span className={`budget-badge ${entry.status === 'UNDER_BUDGET' ? 'badge-green' : 'badge-red'}`}>
                {entry.status === 'UNDER_BUDGET' ? 'UNDER' : 'OVER'}
              </span>
            </div>
            <div className="budget-numbers">
              <span>Budget: {fmt.format(entry.budgeted)}</span>
              <span>Actual: {fmt.format(entry.actual)}</span>
              <span className={entry.remaining >= 0 ? 'positive' : 'negative'}>
                Remaining: {fmt.format(entry.remaining)}
              </span>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
