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
import { AccountsPage } from './components/AccountsPage';
import { BudgetStatus } from './components/BudgetStatus';
import { CategoriesPage } from './components/CategoriesPage';
import { CategorySpend } from './components/CategorySpend';
import { MonthNav } from './components/MonthNav';
import { RecentTransactions } from './components/RecentTransactions';
import { SummaryCard } from './components/SummaryCard';
import { useDashboardData } from './hooks/useDashboardData';
import { theme } from './theme';
import type { Period } from './types';

function App() {
  const now = new Date();
  const [view, setView] = useState<'dashboard' | 'categories' | 'accounts'>('dashboard');
  const [period, setPeriod] = useState<Period>({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const [refreshKey, setRefreshKey] = useState(0);
  const [billFormOpen, setBillFormOpen] = useState(false);
  const [incomeFormOpen, setIncomeFormOpen] = useState(false);

  const {
    summary, summaryLoading, summaryError,
    budgetEntries, budgetLoading, budgetError,
    transactions, transactionsLoading, transactionsError,
    categoryNames, categories,
    accounts,
  } = useDashboardData(period.year, period.month, refreshKey);

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
          </Box>

          <RecentTransactions
            transactions={transactions}
            categoryNames={categoryNames}
            loading={transactionsLoading}
            error={transactionsError}
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
    </ThemeProvider>
  );
}

export default App;
