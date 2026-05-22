import type { MonthlySummary } from '../types';

interface SummaryCardProps {
  summary: MonthlySummary | null;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function SummaryCard({ summary, loading, error }: SummaryCardProps) {
  if (loading) return <div className="summary-card"><p className="loading">Loading…</p></div>;
  if (error) return <div className="summary-card"><p className="error">{error}</p></div>;
  if (!summary) return null;

  const netColor = summary.netBalance >= 0 ? 'positive' : 'negative';

  return (
    <div className="summary-card">
      <div className="summary-item">
        <span className="summary-label">Income</span>
        <span className="summary-value positive">{fmt.format(summary.totalIncome)}</span>
      </div>
      <div className="summary-item">
        <span className="summary-label">Expenses</span>
        <span className="summary-value negative">{fmt.format(summary.totalExpenses)}</span>
      </div>
      <div className="summary-item">
        <span className="summary-label">Net Balance</span>
        <span className={`summary-value ${netColor}`}>{fmt.format(summary.netBalance)}</span>
      </div>
    </div>
  );
}
