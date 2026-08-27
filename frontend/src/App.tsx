import { useState } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import { AddBillForm } from './components/AddBillForm';
import { AddIncomeForm } from './components/AddIncomeForm';
import { CorrectBillForm } from './components/CorrectBillForm';
import { CorrectIncomeForm } from './components/CorrectIncomeForm';
import { RemoveConfirmDialog } from './components/RemoveConfirmDialog';
import { TransactionHistoryDialog } from './components/TransactionHistoryDialog';
import { AccountsPage } from './components/AccountsPage';
import { BudgetingPage } from './components/BudgetingPage';
import { BudgetStatus } from './components/BudgetStatus';
import { CategoriesPage } from './components/CategoriesPage';
import { CategorySpend } from './components/CategorySpend';
import { MonthNav } from './components/MonthNav';
import { RecentTransactions } from './components/RecentTransactions';
import { PriceChangeAlerts } from './components/PriceChangeAlerts';
import { RecurringSeriesProposals } from './components/RecurringSeriesProposals';
import { SavingsGoalsPage } from './components/SavingsGoalsPage';
import { TransactionFilterBar } from './components/TransactionFilterBar';
import { UpcomingRecurring } from './components/UpcomingRecurring';
import { SummaryCard } from './components/SummaryCard';
import { useDashboardData } from './hooks/useDashboardData';
import { fetchBillHistory, fetchIncomeHistory, removeBill, removeIncome } from './api/client';
import { filterTransactions } from './utils/transactionFilters';
import { theme } from './theme';
import { EMPTY_TRANSACTION_FILTERS } from './types';
import type { Period, Transaction, TransactionFilters, TransactionHistoryEntry } from './types';

function App() {
  const now = new Date();
  const [view, setView] = useState<'dashboard' | 'categories' | 'accounts' | 'budgeting' | 'goals'>('dashboard');
  const [period, setPeriod] = useState<Period>({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const [refreshKey, setRefreshKey] = useState(0);
  const [billFormOpen, setBillFormOpen] = useState(false);
  const [incomeFormOpen, setIncomeFormOpen] = useState(false);
  const [correctingTransaction, setCorrectingTransaction] = useState<Transaction | null>(null);
  const [removingTransaction, setRemovingTransaction] = useState<Transaction | null>(null);
  const [removing, setRemoving] = useState(false);
  const [viewingHistoryFor, setViewingHistoryFor] = useState<Transaction | null>(null);
  const [history, setHistory] = useState<TransactionHistoryEntry[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [recurringProposalsOpen, setRecurringProposalsOpen] = useState(false);
  const [filters, setFilters] = useState<TransactionFilters>(EMPTY_TRANSACTION_FILTERS);

  const {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, allTransactions, transactionsLoading, transactionsError,
    categoryNames, categories,
    accounts,
  } = useDashboardData(period.year, period.month, refreshKey);

  const isFiltering = filters.searchText !== ''
    || filters.categoryId !== undefined
    || filters.source !== undefined
    || filters.accountId !== undefined
    || filters.startDate !== undefined
    || filters.endDate !== undefined
    || filters.minAmount !== undefined
    || filters.maxAmount !== undefined
    || filters.type !== 'ALL';

  const displayedTransactions = isFiltering ? filterTransactions(allTransactions, filters) : transactions;

  if (view === 'categories') {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <CategoriesPage onBack={() => { setView('dashboard'); setRefreshKey((k) => k + 1); }} />
      </ThemeProvider>
    );
  }

  if (view === 'accounts') {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <AccountsPage onBack={() => { setView('dashboard'); setRefreshKey((k) => k + 1); }} />
      </ThemeProvider>
    );
  }

  if (view === 'budgeting') {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BudgetingPage onBack={() => { setView('dashboard'); setRefreshKey((k) => k + 1); }} />
      </ThemeProvider>
    );
  }

  if (view === 'goals') {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <SavingsGoalsPage onBack={() => { setView('dashboard'); setRefreshKey((k) => k + 1); }} />
      </ThemeProvider>
    );
  }

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

  async function handleConfirmRemove() {
    if (!removingTransaction) return;
    setRemoving(true);
    try {
      if (removingTransaction.type === 'BILL') {
        await removeBill(removingTransaction.id);
      } else {
        await removeIncome(removingTransaction.id);
      }
      setRemovingTransaction(null);
      handleSaveSuccess();
    } finally {
      setRemoving(false);
    }
  }

  function handleOpenHistory(t: Transaction) {
    setViewingHistoryFor(t);
    setHistory([]);
    setHistoryLoading(true);
    const load = t.type === 'BILL' ? fetchBillHistory(t.id) : fetchIncomeHistory(t.id);
    load
      .then(setHistory)
      .catch(() => setHistory([]))
      .finally(() => setHistoryLoading(false));
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="static" color="primary" elevation={2}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            MyFinance Dashboard
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button color="inherit" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.5)' }} onClick={() => setView('accounts')}>
              Accounts
            </Button>
            <Button color="inherit" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.5)' }} onClick={() => setView('budgeting')}>
              Budgeting
            </Button>
            <Button color="inherit" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.5)' }} onClick={() => setRecurringProposalsOpen(true)}>
              Recurring
            </Button>
            <Button color="inherit" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.5)' }} onClick={() => setView('goals')}>
              Goals
            </Button>
            <Button color="inherit" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.5)' }} onClick={() => setView('categories')}>
              Categories
            </Button>
            <Button color="inherit" variant="contained" sx={{ bgcolor: 'secondary.main' }} onClick={() => setBillFormOpen(true)}>
              + Add Expense
            </Button>
            <Button color="inherit" variant="contained" sx={{ bgcolor: 'secondary.main' }} onClick={() => setIncomeFormOpen(true)}>
              + Add Income
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ py: 3 }}>
        <Stack spacing={2.5}>
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

          <Box sx={{ display: 'flex', gap: 2.5, flexWrap: 'wrap' }}>
            <Box sx={{ flex: 1, minWidth: 280 }}>
              <CategorySpend
                spendingByCategory={summary?.spendingByCategory ?? {}}
                categoryNames={categoryNames}
                loading={summaryLoading}
                error={summaryError}
              />
            </Box>
            <Box sx={{ flex: 1, minWidth: 280 }}>
              <BudgetStatus
                entries={budgetEntries}
                categoryNames={categoryNames}
                loading={budgetLoading}
                error={budgetError}
              />
            </Box>
            <Box sx={{ flex: 1, minWidth: 280 }}>
              <Stack spacing={2.5}>
                <UpcomingRecurring categories={categories} refreshKey={refreshKey} />
                <PriceChangeAlerts categories={categories} refreshKey={refreshKey} />
              </Stack>
            </Box>
          </Box>

          <TransactionFilterBar
            filters={filters}
            onFiltersChange={setFilters}
            categories={categories}
            accounts={accounts}
          />

          <RecentTransactions
            transactions={displayedTransactions}
            categoryNames={categoryNames}
            loading={transactionsLoading}
            error={transactionsError}
            onCorrect={setCorrectingTransaction}
            onRemove={setRemovingTransaction}
            onHistory={handleOpenHistory}
            emptyMessage={isFiltering ? 'No transactions found' : undefined}
          />
        </Stack>
      </Container>

      <AddBillForm
        open={billFormOpen}
        onClose={() => setBillFormOpen(false)}
        onSuccess={handleSaveSuccess}
        categories={categories}
        accounts={accounts}
      />

      <AddIncomeForm
        open={incomeFormOpen}
        onClose={() => setIncomeFormOpen(false)}
        onSuccess={handleSaveSuccess}
        accounts={accounts}
      />

      <CorrectBillForm
        open={correctingTransaction?.type === 'BILL'}
        transaction={correctingTransaction}
        onClose={() => setCorrectingTransaction(null)}
        onSuccess={handleSaveSuccess}
        categories={categories}
        accounts={accounts}
      />

      <CorrectIncomeForm
        open={correctingTransaction?.type === 'INCOME'}
        transaction={correctingTransaction}
        onClose={() => setCorrectingTransaction(null)}
        onSuccess={handleSaveSuccess}
        accounts={accounts}
      />

      <RemoveConfirmDialog
        open={removingTransaction !== null}
        transaction={removingTransaction}
        onClose={() => setRemovingTransaction(null)}
        onConfirm={handleConfirmRemove}
        submitting={removing}
      />

      <TransactionHistoryDialog
        open={viewingHistoryFor !== null}
        transaction={viewingHistoryFor}
        history={history}
        loading={historyLoading}
        onClose={() => setViewingHistoryFor(null)}
      />

      <RecurringSeriesProposals
        open={recurringProposalsOpen}
        onClose={() => setRecurringProposalsOpen(false)}
        onChanged={handleSaveSuccess}
        categories={categories}
      />
    </ThemeProvider>
  );
}

export default App;
