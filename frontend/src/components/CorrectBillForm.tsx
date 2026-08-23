import { useEffect, useState } from 'react';
import { correctBill } from '../api/client';
import type { Account, Category, CorrectBillRequest, Transaction } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface CorrectBillFormProps {
  open: boolean;
  transaction: Transaction | null;
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];
  accounts: Account[];
}

function toDateInput(iso: string): string {
  return new Date(iso).toISOString().slice(0, 10);
}

export function CorrectBillForm({
  open,
  transaction,
  onClose,
  onSuccess,
  categories,
  accounts,
}: CorrectBillFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState('');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
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
      setCategoryId(transaction.categoryId ?? '');
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
      const req: CorrectBillRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        categoryId: categoryId || undefined,
        accountId: accountId || undefined,
      };
      await correctBill(transaction.id, req);
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
    <Modal open={open} onClose={handleClose} title="Correct Expense">
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
            <InputLabel id="correct-bill-category-label">Category</InputLabel>
            <Select
              labelId="correct-bill-category-label"
              value={categoryId}
              label="Category"
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <MenuItem value="">No category</MenuItem>
              {categories.map((cat) => (
                <MenuItem key={cat.id} value={cat.id}>{cat.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl fullWidth>
            <InputLabel id="correct-bill-account-label">Account</InputLabel>
            <Select
              labelId="correct-bill-account-label"
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
