import { useEffect, useState } from 'react';
import { createSavingsGoal, updateSavingsGoal } from '../api/client';
import type { Account, SavingsGoalStatus } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface SavingsGoalFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  accounts: Account[];
  goal?: SavingsGoalStatus | null;
}

export function SavingsGoalForm({ open, onClose, onSuccess, accounts, goal }: SavingsGoalFormProps) {
  const isEdit = !!goal;
  const [name, setName] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [accountId, setAccountId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [amountError, setAmountError] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(goal?.name ?? '');
    setTargetAmount(goal ? String(goal.targetAmount) : '');
    setTargetDate(goal?.targetDate ? goal.targetDate.slice(0, 10) : '');
    setAccountId(goal?.accountId ?? '');
    setAmountError('');
    setServerError(null);
  }, [open, goal]);

  function handleClose() {
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = parseFloat(targetAmount);
    if (!targetAmount || isNaN(parsed) || parsed <= 0) {
      setAmountError('Target amount must be greater than zero');
      return;
    }
    if (!isEdit && !accountId) {
      setServerError('Please select an account');
      return;
    }
    setAmountError('');
    setServerError(null);
    setSubmitting(true);
    try {
      const targetDateIso = targetDate ? new Date(targetDate).toISOString() : undefined;
      if (isEdit && goal) {
        await updateSavingsGoal(goal.id, {
          name: name.trim(),
          targetAmount: parsed,
          targetDate: targetDateIso,
        });
      } else {
        await createSavingsGoal({
          name: name.trim(),
          targetAmount: parsed,
          targetDate: targetDateIso,
          accountId,
        });
      }
      onSuccess();
      onClose();
    } catch {
      setServerError(isEdit
        ? 'Could not save — please try again'
        : 'Could not create goal — the account may already fund another goal');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title={isEdit ? 'Edit Goal' : 'New Savings Goal'}>
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Name *"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Vacation Fund"
            fullWidth
          />
          <TextField
            label="Target Amount *"
            type="text"
            inputMode="decimal"
            value={targetAmount}
            onChange={(e) => setTargetAmount(e.target.value)}
            placeholder="0.00"
            error={!!amountError}
            helperText={amountError || ' '}
            fullWidth
          />
          <TextField
            label="Target Date"
            type="date"
            value={targetDate}
            onChange={(e) => setTargetDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Optional"
            fullWidth
          />
          <FormControl fullWidth disabled={isEdit}>
            <InputLabel id="goal-account-label">Account *</InputLabel>
            <Select
              labelId="goal-account-label"
              value={accountId}
              label="Account *"
              onChange={(e) => setAccountId(e.target.value)}
            >
              {accounts.map((acc) => (
                <MenuItem key={acc.id} value={acc.id}>{acc.name}</MenuItem>
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
