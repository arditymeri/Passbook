import type { CategoryNameMap, Transaction } from '../types';

interface RecentTransactionsProps {
  transactions: Transaction[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const amtFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('default', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function RecentTransactions({ transactions, categoryNames, loading, error }: RecentTransactionsProps) {
  if (loading) return <div className="card"><p className="loading">Loading…</p></div>;
  if (error) return <div className="card"><p className="error">{error}</p></div>;
  if (transactions.length === 0) {
    return <div className="card"><p className="empty">No transactions for this month.</p></div>;
  }

  return (
    <div className="card">
      <h2 className="card-title">Recent Transactions</h2>
      <ul className="transaction-list">
        {transactions.map((t) => (
          <li key={t.id} className="transaction-item">
            <span className="txn-date">{formatDate(t.time)}</span>
            <div className="txn-main">
              <span className="txn-description">{t.description ?? '—'}</span>
              {t.categoryId && (
                <span className="txn-category">{categoryNames.get(t.categoryId) ?? t.categoryId}</span>
              )}
            </div>
            <span className="txn-amount">{amtFmt.format(t.amount)}</span>
            <span className={`txn-badge ${t.type === 'INCOME' ? 'badge-green' : 'badge-red'}`}>
              {t.type === 'INCOME' ? 'INCOME' : 'EXPENSE'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
