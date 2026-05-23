import { useState } from 'react';
import { AddBillForm } from './components/AddBillForm';
import { AddIncomeForm } from './components/AddIncomeForm';
import { BudgetStatus } from './components/BudgetStatus';
import { CategoriesPage } from './components/CategoriesPage';
import { CategorySpend } from './components/CategorySpend';
import { MonthNav } from './components/MonthNav';
import { RecentTransactions } from './components/RecentTransactions';
import { SummaryCard } from './components/SummaryCard';
import { useDashboardData } from './hooks/useDashboardData';
import type { Period } from './types';
import './App.css';

function App() {
  const now = new Date();
  const [view, setView] = useState<'dashboard' | 'categories'>('dashboard');
  const [period, setPeriod] = useState<Period>({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const [refreshKey, setRefreshKey] = useState(0);
  const [billFormOpen, setBillFormOpen] = useState(false);
  const [incomeFormOpen, setIncomeFormOpen] = useState(false);

  if (view === 'categories') {
    return <CategoriesPage onBack={() => setView('dashboard')} />;
  }

  const {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, transactionsLoading, transactionsError,
    categoryNames, categories,
  } = useDashboardData(period.year, period.month, refreshKey);

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

  function handleSaveSuccess() {
    setRefreshKey((k) => k + 1);
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>MyFinance Dashboard</h1>
        <div className="action-buttons">
          <button className="btn-secondary" onClick={() => setView('categories')}>Categories</button>
          <button className="btn-primary" onClick={() => setBillFormOpen(true)}>+ Add Expense</button>
          <button className="btn-primary" onClick={() => setIncomeFormOpen(true)}>+ Add Income</button>
        </div>
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

      <AddBillForm
        open={billFormOpen}
        onClose={() => setBillFormOpen(false)}
        onSuccess={handleSaveSuccess}
        categories={categories}
      />

      <AddIncomeForm
        open={incomeFormOpen}
        onClose={() => setIncomeFormOpen(false)}
        onSuccess={handleSaveSuccess}
      />
    </div>
  );
}

export default App;
