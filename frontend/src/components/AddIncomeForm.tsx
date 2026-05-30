import { useState } from 'react';
import { createIncome } from '../api/client';
import type { CreateIncomeRequest, IncomeSource } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface AddIncomeFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const INCOME_SOURCES: IncomeSource[] = ['SALARY', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'GIFT', 'OTHER'];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function AddIncomeForm({ open, onClose, onSuccess }: AddIncomeFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(today());
  const [description, setDescription] = useState('');
  const [source, setSource] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [amountError, setAmountError] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  function reset() {
    setAmount('');
    setDate(today());
    setDescription('');
    setSource('');
    setAmountError('');
    setServerError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = parseFloat(amount);
    if (!amount || isNaN(parsed) || parsed <= 0) {
      setAmountError('Amount must be greater than zero');
      return;
    }
    setAmountError('');
    setServerError(null);
    setSubmitting(true);
    try {
      const req: CreateIncomeRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        source: (source as IncomeSource) || undefined,
      };
      await createIncome(req);
      reset();
      onSuccess();
      onClose();
    } catch {
      setServerError('Could not save — please try again');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add Income">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
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
          <TextField
            label="Date *"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional"
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel id="income-source-label">Source</InputLabel>
            <Select
              labelId="income-source-label"
              value={source}
              label="Source"
              onChange={(e) => setSource(e.target.value)}
            >
              <MenuItem value="">No source</MenuItem>
              {INCOME_SOURCES.map((s) => (
                <MenuItem key={s} value={s}>{s}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button variant="outlined" onClick={handleClose}>Cancel</Button>
            <Button variant="contained" type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save'}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
