import { useState } from 'react';
import { createBill, createIncome } from '../api/client';
import { detectDuplicates, parseImportFile, suggestCategory } from '../utils/transactionImport';
import type { Account, Category, ImportCandidate, Transaction } from '../types';
import { Modal } from './Modal';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';

interface ImportTransactionsDialogProps {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
  allTransactions: Transaction[];
  categories: Category[];
  accounts: Account[];
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

export function ImportTransactionsDialog({ open, onClose, onImported, allTransactions, categories, accounts }: ImportTransactionsDialogProps) {
  const [accountId, setAccountId] = useState('');
  const [candidates, setCandidates] = useState<ImportCandidate[] | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdCount, setCreatedCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setAccountId('');
    setCandidates(null);
    setSubmitting(false);
    setCreatedCount(null);
    setError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !accountId) return;
    setError(null);
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      const parsed = parseImportFile(text).map((c) => (
        c.status !== 'error' && c.direction === 'BILL'
          ? { ...c, categoryId: suggestCategory(c.description, allTransactions) }
          : c
      ));
      setCandidates(detectDuplicates(parsed, accountId, allTransactions));
    };
    reader.onerror = () => setError('Could not read the file — please try again');
    reader.readAsText(file);
  }

  function toggleIncluded(id: string) {
    setCandidates((prev) => prev?.map((c) => (c.id === id ? { ...c, included: !c.included } : c)) ?? prev);
  }

  function setCategoryFor(id: string, categoryId: string) {
    setCandidates((prev) => prev?.map((c) => (c.id === id ? { ...c, categoryId: categoryId || undefined } : c)) ?? prev);
  }

  async function handleConfirm() {
    if (!candidates || !accountId) return;
    setSubmitting(true);
    setError(null);
    let created = 0;
    try {
      for (const candidate of candidates) {
        if (!candidate.included) continue;
        if (candidate.direction === 'BILL') {
          await createBill({
            amount: candidate.amount,
            time: candidate.date,
            description: candidate.description || undefined,
            categoryId: candidate.categoryId,
            accountId,
          });
        } else {
          await createIncome({
            amount: candidate.amount,
            time: candidate.date,
            description: candidate.description || undefined,
            accountId,
          });
        }
        created++;
      }
      setCreatedCount(created);
      onImported();
    } catch {
      setError('Could not finish the import — some transactions may already have been created');
    } finally {
      setSubmitting(false);
    }
  }

  const includedCount = candidates?.filter((c) => c.included).length ?? 0;
  const errorCount = candidates?.filter((c) => c.status === 'error').length ?? 0;

  return (
    <Modal open={open} onClose={handleClose} title="Import Transactions">
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}

        {createdCount !== null ? (
          <>
            <Alert severity="success">{createdCount} transaction{createdCount === 1 ? '' : 's'} imported.</Alert>
            <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
              <Button variant="contained" onClick={handleClose}>Done</Button>
            </Stack>
          </>
        ) : (
          <>
            <FormControl fullWidth>
              <InputLabel id="import-account-label">Account</InputLabel>
              <Select
                labelId="import-account-label"
                value={accountId}
                label="Account"
                onChange={(e) => setAccountId(e.target.value)}
              >
                <MenuItem value="">Select an account</MenuItem>
                {accounts.map((acc) => (
                  <MenuItem key={acc.id} value={acc.id}>{acc.name}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <Button variant="outlined" component="label" disabled={!accountId}>
              Choose CSV File
              <input type="file" accept=".csv,text/csv" hidden onChange={handleFileChange} disabled={!accountId} />
            </Button>
            {!accountId && <Typography variant="caption" color="text.secondary">Select an account first</Typography>}

            {candidates && (
              <>
                <Typography color="text.secondary">
                  {includedCount} of {candidates.length} row{candidates.length === 1 ? '' : 's'} will be imported
                  {errorCount > 0 ? ` (${errorCount} could not be read)` : ''}.
                </Typography>
                <List disablePadding dense sx={{ maxHeight: 320, overflowY: 'auto' }}>
                  {candidates.map((c) => (
                    <ListItem key={c.id} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.5 }}>
                      {c.status === 'error' ? (
                        <Alert severity="warning" sx={{ py: 0 }}>{c.description || c.date || 'Row'}: {c.errorMessage}</Alert>
                      ) : (
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          <Checkbox size="small" checked={c.included} onChange={() => toggleIncluded(c.id)} />
                          <Stack sx={{ flex: 1, minWidth: 0 }}>
                            <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                              <Typography variant="body2" noWrap>{c.description || '(no description)'}</Typography>
                              {c.status === 'duplicate' && <Chip label="Possible duplicate" color="warning" size="small" />}
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                              {new Date(c.date).toLocaleDateString()}
                            </Typography>
                          </Stack>
                          {c.direction === 'BILL' && (
                            <FormControl size="small" sx={{ minWidth: 140 }}>
                              <Select
                                value={c.categoryId ?? ''}
                                displayEmpty
                                onChange={(e) => setCategoryFor(c.id, e.target.value)}
                              >
                                <MenuItem value="">Uncategorized</MenuItem>
                                {categories.map((cat) => (
                                  <MenuItem key={cat.id} value={cat.id}>{cat.name}</MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                          )}
                          <Typography variant="body2" sx={{ fontWeight: 600, color: c.direction === 'BILL' ? 'error.main' : 'success.main' }}>
                            {c.direction === 'BILL' ? '-' : '+'}{fmt.format(c.amount)}
                          </Typography>
                        </Stack>
                      )}
                    </ListItem>
                  ))}
                </List>
              </>
            )}

            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
              <Button variant="outlined" onClick={handleClose}>Cancel</Button>
              <Button
                variant="contained"
                onClick={handleConfirm}
                disabled={!candidates || !accountId || includedCount === 0 || submitting}
              >
                {submitting ? 'Importing…' : `Import ${includedCount}`}
              </Button>
            </Stack>
          </>
        )}
      </Stack>
    </Modal>
  );
}
