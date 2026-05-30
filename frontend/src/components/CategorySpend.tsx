import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import type { CategoryNameMap } from '../types';

interface CategorySpendProps {
  spendingByCategory: Record<string, number>;
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function CategorySpend({ spendingByCategory, categoryNames, loading, error }: CategorySpendProps) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          {[0, 1, 2].map((i) => <Skeleton key={i} variant="rectangular" height={44} />)}
        </Stack>
      </Paper>
    );
  }
  if (error) return <Alert severity="error">{error}</Alert>;

  const entries = Object.entries(spendingByCategory).sort((a, b) => b[1] - a[1]);

  if (entries.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography color="text.secondary">No spending data for this month.</Typography>
      </Paper>
    );
  }

  const maxAmount = entries[0][1];

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Spending by Category</Typography>
      <List disablePadding>
        {entries.map(([catId, amount]) => (
          <ListItem key={catId} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.75 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
              <Typography variant="body2" noWrap sx={{ maxWidth: '65%' }} title={catId}>
                {categoryNames.get(catId) ?? catId}
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {fmt.format(amount)}
              </Typography>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={(amount / maxAmount) * 100}
              sx={{ mt: 0.5, borderRadius: 1 }}
            />
          </ListItem>
        ))}
      </List>
    </Paper>
  );
}
