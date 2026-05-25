import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import type { CategoryNameMap, Transaction } from '../types';

interface RecentTransactionsProps {
  transactions: Transaction[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
}

const amtFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('default', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function RecentTransactions({ transactions, categoryNames, loading, error }: RecentTransactionsProps) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Recent Transactions</Typography>
        {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} variant="rectangular" height={36} sx={{ mb: 0.5 }} />)}
      </Paper>
    );
  }
  if (error) return <Alert severity="error">{error}</Alert>;
  if (transactions.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography color="text.secondary">No transactions for this month.</Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Recent Transactions</Typography>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Category</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Type</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {transactions.map((t) => (
              <TableRow key={t.id} hover>
                <TableCell>{formatDate(t.time)}</TableCell>
                <TableCell>{t.description ?? '—'}</TableCell>
                <TableCell>{t.categoryId ? (categoryNames.get(t.categoryId) ?? t.categoryId) : '—'}</TableCell>
                <TableCell align="right">
                  <Typography
                    variant="body2"
                    component="span"
                    color={t.type === 'INCOME' ? 'success.main' : 'error.main'}
                    sx={{ fontWeight: 600 }}
                  >
                    {amtFmt.format(t.amount)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={t.type === 'INCOME' ? 'INCOME' : 'EXPENSE'}
                    color={t.type === 'INCOME' ? 'success' : 'error'}
                    size="small"
                    variant="outlined"
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
