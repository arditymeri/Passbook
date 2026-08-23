import { useState } from 'react';
import { createAccount } from '../api/client';
import type { Account, AccountType, CreateAccountRequest } from '../types';
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

interface AddAccountFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (created: Account) => void;
}

const ACCOUNT_TYPES: AccountType[] = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH', 'INVESTMENT'];

export function AddAccountForm({ open, onClose, onSuccess }: AddAccountFormProps) {
  const [name, setName] = useState('');
  const [type, setType] = useState<AccountType | ''>('');
  const [defaultCurrency, setDefaultCurrency] = useState('');
  const [balance, setBalance] = useState('');
  const [institution, setInstitution] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const [typeError, setTypeError] = useState<string | null>(null);
  const [currencyError, setCurrencyError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  function reset() {
    setName('');
    setType('');
    setDefaultCurrency('');
    setBalance('');
    setInstitution('');
    setSubmitting(false);
    setNameError(null);
    setTypeError(null);
    setCurrencyError(null);
    setServerError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    let valid = true;
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('Name is required');
      valid = false;
    } else {
      setNameError(null);
    }
    if (!type) {
      setTypeError('Type is required');
      valid = false;
    } else {
      setTypeError(null);
    }
    const trimmedCurrency = defaultCurrency.trim().toUpperCase();
    if (!trimmedCurrency) {
      setCurrencyError('Currency is required');
      valid = false;
    } else {
      setCurrencyError(null);
    }
    if (!valid) return;

    const parsedBalance = balance ? parseFloat(balance) : undefined;

    setServerError(null);
    setSubmitting(true);
    try {
      const req: CreateAccountRequest = {
        name: trimmedName,
        type: type as AccountType,
        currencies: [trimmedCurrency],
        defaultCurrency: trimmedCurrency,
        ...(parsedBalance !== undefined && !isNaN(parsedBalance) && { balance: parsedBalance }),
        ...(institution.trim() && { institution: institution.trim() }),
      };
      const created = await createAccount(req);
      reset();
      onSuccess(created);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('409')) {
        setServerError('An account with this name already exists');
      } else {
        setServerError('Could not save — please try again');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add Account">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Name *"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. ING Checking"
            error={!!nameError}
            helperText={nameError || ' '}
            fullWidth
          />
          <FormControl fullWidth error={!!typeError}>
            <InputLabel id="acc-type-label">Type *</InputLabel>
            <Select
              labelId="acc-type-label"
              value={type}
              label="Type *"
              onChange={(e) => setType(e.target.value as AccountType | '')}
            >
              <MenuItem value="">Select a type</MenuItem>
              {ACCOUNT_TYPES.map((t) => (
                <MenuItem key={t} value={t}>{t}</MenuItem>
              ))}
            </Select>
            {typeError && <FormHelperText>{typeError}</FormHelperText>}
          </FormControl>
          <TextField
            label="Currency (ISO 4217) *"
            value={defaultCurrency}
            onChange={(e) => setDefaultCurrency(e.target.value)}
            placeholder="e.g. EUR"
            error={!!currencyError}
            helperText={currencyError || ' '}
            fullWidth
          />
          <TextField
            label="Starting Balance"
            type="text"
            inputMode="decimal"
            value={balance}
            onChange={(e) => setBalance(e.target.value)}
            placeholder="0.00"
            helperText=" "
            fullWidth
          />
          <TextField
            label="Institution"
            value={institution}
            onChange={(e) => setInstitution(e.target.value)}
            placeholder="Optional"
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
