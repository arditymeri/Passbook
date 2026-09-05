import type { Transaction, TransactionHistoryEntry } from '../types';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import { useIsPhone } from '../hooks/useIsPhone';

interface TransactionHistoryDialogProps {
  open: boolean;
  transaction: Transaction | null;
  history: TransactionHistoryEntry[];
  loading: boolean;
  onClose: () => void;
}

const amtFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('default', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function TransactionHistoryDialog({
  open,
  transaction,
  history,
  loading,
  onClose,
}: TransactionHistoryDialogProps) {
  const isPhone = useIsPhone();

  return (
    // Full screen on a phone for the same reason Modal.tsx is: a correction history is a list to
    // read through, not a question to answer. This dialog does not use Modal because it has its
    // own actions row.
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" fullScreen={isPhone}>
      <DialogTitle sx={{ fontWeight: 700 }}>Correction history</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {transaction && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip label="Current" color="primary" size="small" />
              <Typography>
                <strong>{amtFmt.format(transaction.amount)}</strong>
                {' on '}
                {formatDate(transaction.time)}
                {transaction.description ? ` — ${transaction.description}` : ''}
              </Typography>
            </Stack>
          )}

          {loading && (
            <Stack spacing={1}>
              {[0, 1].map((i) => <Skeleton key={i} variant="rectangular" height={48} />)}
            </Stack>
          )}

          {!loading && history.length === 0 && (
            <Typography color="text.secondary">
              This transaction has never been corrected.
            </Typography>
          )}

          {!loading && history.length > 0 && (
            <>
              <Typography variant="subtitle2" color="text.secondary">
                Previous values (newest first)
              </Typography>
              <List disablePadding>
                {history.map((entry) => (
                  <ListItem key={entry.id} divider>
                    <ListItemText
                      primary={`${amtFmt.format(entry.amount)} on ${formatDate(entry.time)}`}
                      secondary={entry.description || 'No description'}
                    />
                  </ListItem>
                ))}
              </List>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="outlined" onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
