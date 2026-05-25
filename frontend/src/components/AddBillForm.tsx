import { useState } from 'react';
import { createBill } from '../api/client';
import type { Category, CreateBillRequest } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface AddBillFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function AddBillForm({ open, onClose, onSuccess, categories }: AddBillFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(today());
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [amountError, setAmountError] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  function reset() {
    setAmount('');
    setDate(today());
    setDescription('');
    setCategoryId('');
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
      const req: CreateBillRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        categoryId: categoryId || undefined,
      };
      await createBill(req);
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
    <Modal open={open} onClose={handleClose} title="Add Expense">
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
            <InputLabel id="bill-category-label">Category</InputLabel>
            <Select
              labelId="bill-category-label"
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
