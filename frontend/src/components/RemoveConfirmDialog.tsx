import type { Transaction } from '../types';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';

interface RemoveConfirmDialogProps {
  open: boolean;
  transaction: Transaction | null;
  onClose: () => void;
  onConfirm: () => void;
  submitting: boolean;
}

const amtFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('default', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function RemoveConfirmDialog({
  open,
  transaction,
  onClose,
  onConfirm,
  submitting,
}: RemoveConfirmDialogProps) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ fontWeight: 700 }}>Remove this transaction?</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {transaction && (
            <Typography>
              <strong>{transaction.description || 'No description'}</strong>
              {' — '}
              {amtFmt.format(transaction.amount)}
              {' on '}
              {formatDate(transaction.time)}
            </Typography>
          )}
          <Alert severity="info">
            The original entry is kept for the record — this posts a reversal so it no longer counts
            toward any total.
          </Alert>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="outlined" onClick={onClose}>Cancel</Button>
        <Button variant="contained" color="error" onClick={onConfirm} disabled={submitting}>
          {submitting ? 'Removing…' : 'Remove'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
