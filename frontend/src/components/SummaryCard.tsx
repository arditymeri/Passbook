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
      {/*
        THE PATTERN THIS FILE ESTABLISHES, used the same way in TransactionFilterBar and
        CashFlowForecastCard: a fixed minimum becomes a responsive one — `{ xs: 0, sm: 160 }`,
        never a smaller flat number. That is what makes "the desktop is unchanged" true by
        construction rather than by a test that might notice afterwards (SC-006): above 600px every
        value here is exactly what it was.

        Three 160px minimums in one row is a 480px floor, which is why this card was one of the
        components that overflowed a 375px screen. Below sm each figure takes a full line instead,
        so the amount is never squeezed — all three figures visible at once is the requirement
        (quickstart scenario 9), and shrinking them to a third of 320px would meet the letter of it
        and none of the point.
      */}
      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: { xs: '1 1 100%', sm: 1 }, minWidth: { xs: 0, sm: 160 } }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Income</Typography>
          <Typography variant="h6" color="success.main" sx={{ fontWeight: 700 }}>
            {fmt.format(summary.totalIncome)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: { xs: '1 1 100%', sm: 1 }, minWidth: { xs: 0, sm: 160 } }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Expenses</Typography>
          <Typography variant="h6" color="error.main" sx={{ fontWeight: 700 }}>
            {fmt.format(summary.totalExpenses)}
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: { xs: '1 1 100%', sm: 1 }, minWidth: { xs: 0, sm: 160 } }}>
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>Net Balance</Typography>
          <Typography variant="h6" color={summary.netBalance >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 700 }}>
            {fmt.format(summary.netBalance)}
          </Typography>
        </Stack>
      </Stack>
    </Paper>
  );
}
