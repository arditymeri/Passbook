import { useEffect, useState } from 'react';
import { fetchBudgets, repeatAllocations } from '../api/client';
import type { Allocation, Category, RepeatAllocationsResponse } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';

interface RepeatAllocationsDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (result: RepeatAllocationsResponse) => void;
  toYear: number;
  toMonth: number;
  targetAllocations: Allocation[];
  categories: Category[];
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function RepeatAllocationsDialog({
  open,
  onClose,
  onSuccess,
  toYear,
  toMonth,
  targetAllocations,
  categories,
}: RepeatAllocationsDialogProps) {
  const previousMonth = toMonth === 1 ? { year: toYear - 1, month: 12 } : { year: toYear, month: toMonth - 1 };
  const [fromYear, setFromYear] = useState(previousMonth.year);
  const [fromMonth, setFromMonth] = useState(previousMonth.month);
  const [sourceAllocations, setSourceAllocations] = useState<Allocation[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    fetchBudgets(fromYear, fromMonth)
      .then((result) => { if (!cancelled) setSourceAllocations(result); })
      .catch(() => { if (!cancelled) setSourceAllocations([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, fromYear, fromMonth]);

  function categoryName(id: string) {
    return categories.find((c) => c.id === id)?.name ?? id;
  }

  function handleClose() {
    setServerError(null);
    setSubmitting(false);
    onClose();
  }

  async function handleConfirm() {
    setServerError(null);
    setSubmitting(true);
    try {
      const result = await repeatAllocations({ fromYear, fromMonth, toYear, toMonth });
      onSuccess(result);
    } catch {
      setServerError('Could not repeat allocations — please try again');
    } finally {
      setSubmitting(false);
    }
  }

  const preview = sourceAllocations.map((source) => {
    const existing = targetAllocations.find((a) => a.categoryId === source.categoryId);
    const existingAmount = existing?.limitAmount ?? 0;
    return {
      categoryId: source.categoryId,
      amountAdded: source.limitAmount,
      existingAmount,
      newAmount: existingAmount + source.limitAmount,
      isTopUp: existingAmount > 0,
    };
  });

  return (
    <Modal open={open} onClose={handleClose} title="Repeat a Month's Assignments">
      <Stack spacing={2} sx={{ pt: 1 }}>
        {serverError && <Alert severity="error">{serverError}</Alert>}
        <Stack direction="row" spacing={2}>
          <TextField
            label="From year"
            type="text"
            inputMode="numeric"
            value={fromYear}
            onChange={(e) => setFromYear(Number(e.target.value) || fromYear)}
            fullWidth
          />
          <TextField
            label="From month"
            type="text"
            inputMode="numeric"
            value={fromMonth}
            onChange={(e) => setFromMonth(Number(e.target.value) || fromMonth)}
            fullWidth
          />
        </Stack>

        {loading && <Typography color="text.secondary">Loading…</Typography>}

        {!loading && preview.length === 0 && (
          <Alert severity="info">Nothing to repeat — that month has no allocations.</Alert>
        )}

        {!loading && preview.length > 0 && (
          <List disablePadding>
            {preview.map((p) => (
              <ListItem key={p.categoryId} divider>
                <ListItemText
                  primary={categoryName(p.categoryId)}
                  secondary={p.isTopUp ? `Already has ${fmt.format(p.existingAmount)} this month — will be topped up` : 'New allocation'}
                />
                <Typography sx={{ fontWeight: 600 }}>
                  +{fmt.format(p.amountAdded)} → {fmt.format(p.newAmount)}
                </Typography>
              </ListItem>
            ))}
          </List>
        )}

        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          <Button variant="outlined" onClick={handleClose}>Cancel</Button>
          <Button variant="contained" onClick={handleConfirm} disabled={submitting || loading || preview.length === 0}>
            {submitting ? 'Repeating…' : 'Repeat'}
          </Button>
        </Stack>
      </Stack>
    </Modal>
  );
}
