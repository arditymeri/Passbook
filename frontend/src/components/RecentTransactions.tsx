import { useState } from 'react';
import type * as React from 'react';
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
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import type { CategoryNameMap, Transaction } from '../types';
import { NecessityTagControl } from './NecessityTagControl';
import { useIsPhone } from '../hooks/useIsPhone';

interface RecentTransactionsProps {
  transactions: Transaction[];
  categoryNames: CategoryNameMap;
  loading: boolean;
  error: string | null;
  onCorrect: (t: Transaction) => void;
  onRemove: (t: Transaction) => void;
  onHistory: (t: Transaction) => void;
  onTagChanged: () => void;
  emptyMessage?: string;
}

const amtFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('default', { day: '2-digit', month: 'short', year: 'numeric' });
}

/**
 * The badges a transaction may carry. Extracted so the table and the stacked phone rendering show
 * the same information rather than drifting apart — a phone layout that quietly drops "Auto-posted"
 * is a phone layout that lies about where a transaction came from.
 */
function TransactionBadges({ t }: { t: Transaction }) {
  return (
    <>
      {t.correctsTransactionId && (
        <Tooltip title="This entry replaces an earlier, corrected one">
          <Chip label="Corrected" size="small" variant="outlined" />
        </Tooltip>
      )}
      {t.recurringSeriesId && (
        <Tooltip
          title={`Posted automatically by the recurring series "${t.description ?? 'this series'}" — nobody entered it and no statement reported it yet`}
        >
          <Chip label="Auto-posted" size="small" variant="outlined" color="info" />
        </Tooltip>
      )}
    </>
  );
}

export function RecentTransactions({
  transactions,
  categoryNames,
  loading,
  error,
  onCorrect,
  onRemove,
  onHistory,
  onTagChanged,
  emptyMessage = 'No transactions for this month.',
}: RecentTransactionsProps) {
  const isPhone = useIsPhone();
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [menuTarget, setMenuTarget] = useState<Transaction | null>(null);

  function openMenu(e: React.MouseEvent<HTMLElement>, t: Transaction) {
    setMenuAnchor(e.currentTarget);
    setMenuTarget(t);
  }

  function closeMenu() {
    setMenuAnchor(null);
    setMenuTarget(null);
  }

  function runAction(action: (t: Transaction) => void) {
    if (menuTarget) action(menuTarget);
    closeMenu();
  }

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
        <Typography color="text.secondary">{emptyMessage}</Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Recent Transactions</Typography>
      {/*
        Below sm the six-column table becomes one block per transaction.

        WHY NOT JUST LET THE TABLE SCROLL. MUI's TableContainer already does — which is why the
        page-level overflow check passed on this component while it was still unreadable. Six
        columns in 320px means the amount, the thing the operator opened the app to see, sits off
        the right edge behind a horizontal swipe nobody discovers. Stacking is what makes the
        description, the date and the amount visible at once (FR-005, SC-003).

        THE AMOUNT IS NEVER TRUNCATED OR ABBREVIATED. The description gets the ellipsis; the amount
        gets `whiteSpace: nowrap` and whatever width it needs. Rendering €1,234.56 as "€1.2k" to
        save space would be a Principle IV violation dressed up as a design decision, and a layout
        feature is exactly where that temptation arrives looking reasonable.
      */}
      {isPhone ? (
        <Stack divider={<Divider flexItem />}>
          {transactions.map((t) => (
            <Box key={t.id} sx={{ py: 1.25 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {/*
                  minWidth: 0 is what makes the ellipsis work at all: without it this flex item
                  refuses to shrink below its content, and one long merchant name from a bank
                  statement widens the whole page (quickstart scenario 4).
                */}
                <Typography sx={{ flexGrow: 1, minWidth: 0, fontWeight: 500 }} noWrap>
                  {t.description ?? '—'}
                </Typography>
                <Typography
                  component="span"
                  color={t.type === 'INCOME' ? 'success.main' : 'error.main'}
                  sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}
                >
                  {amtFmt.format(t.amount)}
                </Typography>
                <IconButton
                  aria-label={`Actions for ${t.description ?? 'transaction'}`}
                  onClick={(e) => openMenu(e, t)}
                  sx={{ minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}
                >
                  <MoreVertIcon />
                </IconButton>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.75, mt: 0.5 }}>
                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                  {formatDate(t.time)}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ minWidth: 0 }} noWrap>
                  · {t.categoryId ? (categoryNames.get(t.categoryId) ?? t.categoryId) : '—'}
                </Typography>
                <Chip
                  label={t.type === 'INCOME' ? 'INCOME' : 'EXPENSE'}
                  color={t.type === 'INCOME' ? 'success' : 'error'}
                  size="small"
                  variant="outlined"
                />
                <TransactionBadges t={t} />
                {t.type === 'BILL' && (
                  <NecessityTagControl billId={t.id} tag={t.necessityTag} onChanged={onTagChanged} />
                )}
              </Box>
            </Box>
          ))}
        </Stack>
      ) : (
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Category</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Type</TableCell>
              <TableCell align="right" sx={{ width: 48 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {transactions.map((t) => (
              <TableRow key={t.id} hover>
                <TableCell>{formatDate(t.time)}</TableCell>
                <TableCell>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <span>{t.description ?? '—'}</span>
                    <TransactionBadges t={t} />
                  </Stack>
                </TableCell>
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
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Chip
                      label={t.type === 'INCOME' ? 'INCOME' : 'EXPENSE'}
                      color={t.type === 'INCOME' ? 'success' : 'error'}
                      size="small"
                      variant="outlined"
                    />
                    {t.type === 'BILL' && (
                      <NecessityTagControl billId={t.id} tag={t.necessityTag} onChanged={onTagChanged} />
                    )}
                  </Stack>
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    aria-label={`Actions for ${t.description ?? 'transaction'}`}
                    onClick={(e) => openMenu(e, t)}
                  >
                    <MoreVertIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      )}

      <Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={closeMenu}>
        <MenuItem onClick={() => runAction(onCorrect)}>Correct</MenuItem>
        <MenuItem onClick={() => runAction(onRemove)}>Remove</MenuItem>
        <MenuItem onClick={() => runAction(onHistory)}>History</MenuItem>
      </Menu>
    </Paper>
  );
}
