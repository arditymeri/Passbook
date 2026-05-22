import { useState } from 'react';
import { BudgetStatus } from './components/BudgetStatus';
import { CategorySpend } from './components/CategorySpend';
import { MonthNav } from './components/MonthNav';
import { RecentTransactions } from './components/RecentTransactions';
import { SummaryCard } from './components/SummaryCard';
import { useDashboardData } from './hooks/useDashboardData';
import type { Period } from './types';
import './App.css';

function App() {
  const now = new Date();
  const [period, setPeriod] = useState<Period>({ year: now.getFullYear(), month: now.getMonth() + 1 });

  const {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, transactionsLoading, transactionsError,
    categoryNames,
  } = useDashboardData(period.year, period.month);

  function handlePrevious() {
    setPeriod((p) =>
      p.month === 1 ? { year: p.year - 1, month: 12 } : { year: p.year, month: p.month - 1 }
    );
  }

  function handleNext() {
    setPeriod((p) =>
      p.month === 12 ? { year: p.year + 1, month: 1 } : { year: p.year, month: p.month + 1 }
    );
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>MyFinance Dashboard</h1>
      </header>

      <MonthNav
        year={period.year}
        month={period.month}
        onPrevious={handlePrevious}
        onNext={handleNext}
      />

      <SummaryCard
        summary={summary}
        loading={summaryLoading}
        error={summaryError}
      />

      <div className="middle-row">
        <CategorySpend
          spendingByCategory={summary?.spendingByCategory ?? {}}
          categoryNames={categoryNames}
          loading={summaryLoading}
          error={summaryError}
        />

        <BudgetStatus
          entries={budgetEntries}
          categoryNames={categoryNames}
          loading={budgetLoading}
          error={budgetError}
        />
      </div>

      <RecentTransactions
        transactions={transactions}
        loading={transactionsLoading}
        error={transactionsError}
      />
    </div>
  );
}

export default App;
