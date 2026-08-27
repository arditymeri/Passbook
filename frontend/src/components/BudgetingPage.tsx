import { useEffect, useState } from 'react';
import { useBudgetAllocations } from '../hooks/useBudgetAllocations';
import { fetchCategories, fetchMonthlySummary } from '../api/client';
import { MonthNav } from './MonthNav';
import { AllocationForm } from './AllocationForm';
import { MoveAllocationDialog } from './MoveAllocationDialog';
import { RepeatAllocationsDialog } from './RepeatAllocationsDialog';
import type { Allocation, BudgetStatusEntry, Category, Period } from '../types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Button from '@mui/material/Button';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';

interface BudgetingPageProps {
  onBack: () => void;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function BudgetingPage({ onBack }: BudgetingPageProps) {
  const now = new Date();
  const [period, setPeriod] = useState<Period>({ year: now.getFullYear(), month: now.getMonth() + 1 });
  const { entries, allocations, unallocated, loading, error, refresh } = useBudgetAllocations(period.year, period.month);
  const [incomeThisMonth, setIncomeThisMonth] = useState<number | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);
  const [moveDialogOpen, setMoveDialogOpen] = useState(false);
  const [repeatDialogOpen, setRepeatDialogOpen] = useState(false);

  useEffect(() => {
    fetchCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchMonthlySummary(period.year, period.month)
      .then((summary) => { if (!cancelled) setIncomeThisMonth(summary.totalIncome); })
      .catch(() => { if (!cancelled) setIncomeThisMonth(null); });
    return () => { cancelled = true; };
  }, [period.year, period.month]);

  function handlePrevious() {
    setPeriod((p) => (p.month === 1 ? { year: p.year - 1, month: 12 } : { year: p.year, month: p.month - 1 }));
  }

  function handleNext() {
    setPeriod((p) => (p.month === 12 ? { year: p.year + 1, month: 1 } : { year: p.year, month: p.month + 1 }));
  }

  function handleAssignNew() {
    setEditingCategoryId(null);
    setFormOpen(true);
  }

  function handleEdit(categoryId: string) {
    setEditingCategoryId(categoryId);
    setFormOpen(true);
  }

  function handleFormSuccess() {
    setFormOpen(false);
    refresh();
  }

  function handleMoveSuccess() {
    setMoveDialogOpen(false);
    refresh();
  }

  function handleRepeatSuccess() {
    setRepeatDialogOpen(false);
    refresh();
  }

  const allocationTargets = categories.filter((c) => c.type === 'EXPENSE' || c.type === 'BOTH');
  const entryByCategory = new Map<string, BudgetStatusEntry>(entries.map((e) => [e.categoryId, e]));
  const editingAllocation: Allocation | undefined = editingCategoryId
    ? allocations.find((a) => a.categoryId === editingCategoryId)
    : undefined;

  const allocatedThisMonth = entries.reduce((sum, e) => sum + e.budgeted, 0);

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
        <IconButton onClick={onBack} aria-label="Back">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>Budgeting</Typography>
        <Button variant="outlined" onClick={() => setRepeatDialogOpen(true)}>
          Repeat Last Month
        </Button>
        <Button variant="outlined" onClick={() => setMoveDialogOpen(true)}>
          Move Money
        </Button>
        <Button variant="contained" startIcon={<AddIcon />} onClick={handleAssignNew}>
          Assign to Category
        </Button>
      </Stack>

      <Stack spacing={2.5}>
        <MonthNav year={period.year} month={period.month} onPrevious={handlePrevious} onNext={handleNext} />

        {loading && (
          <Paper sx={{ p: 2 }}>
            <Skeleton variant="rectangular" height={80} />
          </Paper>
        )}

        {!loading && error && <Alert severity="error">{error}</Alert>}

        {!loading && !error && (
          <>
            <Paper sx={{ p: 2 }}>
              <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="caption" color="text.secondary">Income this month</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {incomeThisMonth === null ? '—' : fmt.format(incomeThisMonth)}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">Allocated this month</Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{fmt.format(allocatedThisMonth)}</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">Unallocated</Typography>
                  <Typography
                    variant="h6"
                    sx={{ fontWeight: 700, color: unallocated < 0 ? 'error.main' : 'success.main' }}
                  >
                    {fmt.format(unallocated)}
                  </Typography>
                </Box>
              </Stack>
            </Paper>

            <Paper sx={{ p: 2 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Categories</Typography>
              {allocationTargets.length === 0 && (
                <Typography color="text.secondary">No expense categories yet.</Typography>
              )}
              <List disablePadding>
                {allocationTargets.map((cat) => {
                  const entry = entryByCategory.get(cat.id);
                  const envelope = entry?.envelopeBalance ?? 0;
                  return (
                    <ListItemButton key={cat.id} divider onClick={() => handleEdit(cat.id)}>
                      <ListItemText
                        primary={cat.name}
                        secondary={`Assigned this month: ${fmt.format(entry?.budgeted ?? 0)} · Spent: ${fmt.format(entry?.actual ?? 0)}`}
                      />
                      <Typography sx={{ color: envelope < 0 ? 'error.main' : 'text.primary', fontWeight: 600 }}>
                        {fmt.format(envelope)}
                      </Typography>
                    </ListItemButton>
                  );
                })}
              </List>
            </Paper>
          </>
        )}
      </Stack>

      <AllocationForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSuccess={handleFormSuccess}
        year={period.year}
        month={period.month}
        categories={categories}
        initialCategoryId={editingCategoryId ?? undefined}
        initialAmount={editingAllocation ? String(editingAllocation.limitAmount) : undefined}
      />

      <MoveAllocationDialog
        open={moveDialogOpen}
        onClose={() => setMoveDialogOpen(false)}
        onSuccess={handleMoveSuccess}
        year={period.year}
        month={period.month}
        categories={categories}
      />

      <RepeatAllocationsDialog
        open={repeatDialogOpen}
        onClose={() => setRepeatDialogOpen(false)}
        onSuccess={handleRepeatSuccess}
        toYear={period.year}
        toMonth={period.month}
        targetAllocations={allocations}
        categories={categories}
      />
    </Box>
  );
}
