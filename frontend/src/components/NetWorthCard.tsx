import { currentNetWorth } from '../utils/netWorthTrend';
import type { Account, Transaction } from '../types';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

interface NetWorthCardProps {
  accounts: Account[];
  allTransactions: Transaction[];
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function NetWorthCard({ accounts }: NetWorthCardProps) {
  if (accounts.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>Net Worth</Typography>
        <Typography color="text.secondary">No accounts yet — nothing to total.</Typography>
      </Paper>
    );
  }

  const total = currentNetWorth(accounts);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={0.5}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Net Worth</Typography>
        <Typography variant="h4" color={total >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 700 }}>
          {fmt.format(total)}
        </Typography>
      </Stack>
    </Paper>
  );
}
