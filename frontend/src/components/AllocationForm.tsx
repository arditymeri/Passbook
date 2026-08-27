import { useState } from 'react';
import { createOrUpdateBudget } from '../api/client';
import type { Allocation, Category, CreateAllocationRequest } from '../types';
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

interface AllocationFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (allocation: Allocation) => void;
  year: number;
  month: number;
  categories: Category[];
  /** Preselect a category (e.g. editing an existing allocation's amount) and prefill its amount. */
  initialCategoryId?: string;
  initialAmount?: string;
}

export function AllocationForm({
  open,
  onClose,
  onSuccess,
  year,
  month,
  categories,
  initialCategoryId,
  initialAmount,
}: AllocationFormProps) {
  const [categoryId, setCategoryId] = useState(initialCategoryId ?? '');
  const [amount, setAmount] = useState(initialAmount ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [amountError, setAmountError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const allocationTargets = categories.filter((c) => c.type === 'EXPENSE' || c.type === 'BOTH');

  function reset() {
    setCategoryId(initialCategoryId ?? '');
    setAmount(initialAmount ?? '');
    setSubmitting(false);
    setCategoryError(null);
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
    if (!categoryId) {
      setCategoryError('Category is required');
      valid = false;
    } else {
      setCategoryError(null);
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
      const req: CreateAllocationRequest = {
        categoryId,
        year,
        month,
        limitAmount: parsedAmount,
      };
      const saved = await createOrUpdateBudget(req);
      reset();
      onSuccess(saved);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('400')) {
        setServerError('This category cannot receive an allocation (income-only categories are excluded)');
      } else {
        setServerError('Could not save — please try again');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Assign to a Category">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <FormControl fullWidth error={!!categoryError} disabled={!!initialCategoryId}>
            <InputLabel id="allocation-category-label">Category *</InputLabel>
            <Select
              labelId="allocation-category-label"
              value={categoryId}
              label="Category *"
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <MenuItem value="">Select a category</MenuItem>
              {allocationTargets.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
              ))}
            </Select>
            {categoryError && <FormHelperText>{categoryError}</FormHelperText>}
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
              {submitting ? 'Saving…' : 'Save'}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
