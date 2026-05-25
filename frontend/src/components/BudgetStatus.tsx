import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import type { BudgetStatusEntry, CategoryNameMap } from '../types';

interface BudgetStatusProps {
  entries: BudgetStatusEntry[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function BudgetStatus({ entries, categoryNames, loading, error }: BudgetStatusProps) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          {[0, 1, 2].map((i) => <Skeleton key={i} variant="rectangular" height={48} />)}
        </Stack>
      </Paper>
    );
  }
  if (error) return <Alert severity="error">{error}</Alert>;
  if (entries.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography color="text.secondary">No budget data for this month.</Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Budget vs. Actual</Typography>
      <List disablePadding>
        {entries.map((entry) => {
          const pct = entry.budgeted > 0 ? Math.min((entry.actual / entry.budgeted) * 100, 100) : 0;
          const over = entry.status !== 'UNDER_BUDGET';
          return (
            <ListItem key={entry.categoryId} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.75 }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="body2" title={entry.categoryId} noWrap sx={{ maxWidth: '60%' }}>
                  {categoryNames.get(entry.categoryId) ?? entry.categoryId}
                </Typography>
                <Chip
                  label={over ? 'OVER' : 'UNDER'}
                  color={over ? 'error' : 'success'}
                  size="small"
                />
              </Stack>
              <LinearProgress
                variant="determinate"
                value={pct}
                color={over ? 'error' : 'success'}
                sx={{ mt: 0.5, borderRadius: 1 }}
              />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.25 }}>
                <Typography variant="caption" color="text.secondary">
                  Budget: {fmt.format(entry.budgeted)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Actual: {fmt.format(entry.actual)}
                </Typography>
                <Typography variant="caption" color={entry.remaining >= 0 ? 'success.main' : 'error.main'}>
                  Rem: {fmt.format(entry.remaining)}
                </Typography>
              </Box>
            </ListItem>
          );
        })}
      </List>
    </Paper>
  );
}
