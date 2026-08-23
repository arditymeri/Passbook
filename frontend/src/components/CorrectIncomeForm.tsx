import { useEffect, useState } from 'react';
import { correctIncome } from '../api/client';
import type { Account, CorrectIncomeRequest, IncomeSource, Transaction } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface CorrectIncomeFormProps {
  open: boolean;
  transaction: Transaction | null;
  onClose: () => void;
  onSuccess: () => void;
  accounts: Account[];
}

const INCOME_SOURCES: IncomeSource[] = ['SALARY', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'GIFT', 'OTHER'];

function toDateInput(iso: string): string {
  return new Date(iso).toISOString().slice(0, 10);
}

export function CorrectIncomeForm({
  open,
  transaction,
  onClose,
  onSuccess,
  accounts,
}: CorrectIncomeFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState('');
  const [description, setDescription] = useState('');
  const [source, setSource] = useState('');
  const [accountId, setAccountId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [amountError, setAmountError] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  // Pre-fill from the transaction being corrected each time the dialog opens.
  useEffect(() => {
    if (open && transaction) {
      setAmount(String(transaction.amount));
      setDate(toDateInput(transaction.time));
      setDescription(transaction.description ?? '');
      setSource(transaction.source ?? '');
      setAccountId(transaction.accountId ?? '');
      setAmountError('');
      setServerError(null);
    }
  }, [open, transaction]);

  function handleClose() {
    setServerError(null);
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!transaction) return;

    const parsed = parseFloat(amount);
    if (!amount || isNaN(parsed) || parsed <= 0) {
      setAmountError('Amount must be greater than zero');
      return;
    }
    setAmountError('');
    setServerError(null);
    setSubmitting(true);
    try {
      const req: CorrectIncomeRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        source: (source as IncomeSource) || undefined,
        accountId: accountId || undefined,
      };
      await correctIncome(transaction.id, req);
      onSuccess();
      onClose();
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('409')) {
        setServerError('This transaction was already corrected or removed — please refresh and try again');
      } else {
        setServerError('Could not save — please try again');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Correct Income">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <Alert severity="info">
            The original entry is kept for the record — this posts a correction that replaces it.
          </Alert>
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
            <InputLabel id="correct-income-source-label">Source</InputLabel>
            <Select
              labelId="correct-income-source-label"
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
          <FormControl fullWidth>
            <InputLabel id="correct-income-account-label">Account</InputLabel>
            <Select
              labelId="correct-income-account-label"
              value={accountId}
              label="Account"
              onChange={(e) => setAccountId(e.target.value)}
            >
              <MenuItem value="">No account</MenuItem>
              {accounts.map((acc) => (
                <MenuItem key={acc.id} value={acc.id}>{acc.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button variant="outlined" onClick={handleClose}>Cancel</Button>
            <Button variant="contained" type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save Correction'}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
