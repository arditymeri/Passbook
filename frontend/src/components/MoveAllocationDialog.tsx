import { useState } from 'react';
import { moveAllocation } from '../api/client';
import type { Category, TransferAllocationResponse } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface MoveAllocationDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (result: TransferAllocationResponse) => void;
  year: number;
  month: number;
  categories: Category[];
}

export function MoveAllocationDialog({ open, onClose, onSuccess, year, month, categories }: MoveAllocationDialogProps) {
  const [fromCategoryId, setFromCategoryId] = useState('');
  const [toCategoryId, setToCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [fromError, setFromError] = useState<string | null>(null);
  const [toError, setToError] = useState<string | null>(null);
  const [amountError, setAmountError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const allocationTargets = categories.filter((c) => c.type === 'EXPENSE' || c.type === 'BOTH');

  function reset() {
    setFromCategoryId('');
    setToCategoryId('');
    setAmount('');
    setSubmitting(false);
    setFromError(null);
    setToError(null);
    setAmountError(null);
    setServerError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    let valid = true;
    if (!fromCategoryId) {
      setFromError('Source category is required');
      valid = false;
    } else {
      setFromError(null);
    }
    if (!toCategoryId) {
      setToError('Destination category is required');
      valid = false;
    } else if (toCategoryId === fromCategoryId) {
      setToError('Choose a different category than the source');
      valid = false;
    } else {
      setToError(null);
    }
    const parsedAmount = parseFloat(amount);
    if (!amount || isNaN(parsedAmount) || parsedAmount <= 0) {
      setAmountError('Enter an amount greater than zero');
      valid = false;
    } else {
      setAmountError(null);
    }
    if (!valid) return;

    setServerError(null);
    setSubmitting(true);
    try {
      const result = await moveAllocation({
        fromCategoryId,
        toCategoryId,
        year,
        month,
        amount: parsedAmount,
      });
      reset();
      onSuccess(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('400')) {
        setServerError('This exceeds the source category\'s available balance');
      } else {
        setServerError('Could not move funds — please try again');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Move Money Between Categories">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <FormControl fullWidth error={!!fromError}>
            <InputLabel id="move-from-label">From category *</InputLabel>
            <Select
              labelId="move-from-label"
              value={fromCategoryId}
              label="From category *"
              onChange={(e) => setFromCategoryId(e.target.value)}
            >
              <MenuItem value="">Select a category</MenuItem>
              {allocationTargets.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
              ))}
            </Select>
            {fromError && <FormHelperText>{fromError}</FormHelperText>}
          </FormControl>
          <FormControl fullWidth error={!!toError}>
            <InputLabel id="move-to-label">To category *</InputLabel>
            <Select
              labelId="move-to-label"
              value={toCategoryId}
              label="To category *"
              onChange={(e) => setToCategoryId(e.target.value)}
            >
              <MenuItem value="">Select a category</MenuItem>
              {allocationTargets.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
              ))}
            </Select>
            {toError && <FormHelperText>{toError}</FormHelperText>}
          </FormControl>
          <TextField
            label="Amount *"
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            error={!!amountError}
            helperText={amountError || ' '}
            fullWidth
          />
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button variant="outlined" onClick={handleClose}>Cancel</Button>
            <Button variant="contained" type="submit" disabled={submitting}>
              {submitting ? 'Moving…' : 'Move'}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
