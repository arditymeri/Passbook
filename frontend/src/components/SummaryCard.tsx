import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import type { MonthlySummary } from '../types';

interface SummaryCardProps {
  summary: MonthlySummary | null;
  loading: boolean;
  error: string | null;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function SummaryCard({ summary, loading, error }: SummaryCardProps) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <Skeleton variant="rectangular" height={28} />
          <Skeleton variant="rectangular" height={28} />
          <Skeleton variant="rectangular" height={28} />
        </Stack>
      </Paper>
    );
  }
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!summary) return null;

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 160 }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Income</Typography>
          <Typography variant="h6" color="success.main" sx={{ fontWeight: 700 }}>
            {fmt.format(summary.totalIncome)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 160 }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Expenses</Typography>
          <Typography variant="h6" color="error.main" sx={{ fontWeight: 700 }}>
            {fmt.format(summary.totalExpenses)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 160 }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Net Balance</Typography>
          <Typography variant="h6" color={summary.netBalance >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 700 }}>
            {fmt.format(summary.netBalance)}
          </Typography>
        </Stack>
      </Stack>
    </Paper>
  );
}
