import { useState } from 'react';
import { ingestStatement, previewStatement } from '../api/client';
import type { Account, Category, StatementPreview, StatementRowPreview } from '../types';
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
  categories: Category[];
  accounts: Account[];
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

/**
 * Imports a bank statement.
 *
 * Feature 022 moved every judgement in this dialog to the server: parsing, whether a row is already
 * recorded, and the suggested category. The client renders what it is told and derives nothing — a
 * browser can only compare against transactions it happens to have loaded, which is a useful
 * convenience but not the idempotency guarantee the app now makes.
 *
 * The file is uploaded twice, once to preview and once to commit. That is deliberate: it keeps
 * identity derivation entirely server-side, so a row can never be recorded under an identity no
 * re-parse of the statement would reproduce.
 */
export function ImportTransactionsDialog({ open, onClose, onImported, categories, accounts }: ImportTransactionsDialogProps) {
  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<StatementPreview | null>(null);
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [importedCount, setImportedCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setAccountId('');
    setFile(null);
    setPreview(null);
    setExcluded(new Set());
    setImportedCount(null);
    setError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const chosen = e.target.files?.[0];
    if (!chosen) return;
    if (!accountId) {
      setError('Choose the account this statement belongs to first.');
      return;
    }
    setFile(chosen);
    setError(null);
    try {
      const result = await previewStatement(chosen, accountId);
      setPreview(result);
      // Rows already recorded start excluded — importing them is a no-op anyway, but leaving them
      // ticked would misrepresent what confirming is about to do.
      setExcluded(new Set(result.rows
        .filter((row) => row.status !== 'RECORDED')
        .map((row) => row.rowIndex)));
    } catch (e) {
      setPreview(null);
      setError(e instanceof Error ? e.message : 'Could not read that file.');
    }
  }

  function toggleExcluded(rowIndex: number) {
    setExcluded((prev) => {
      const next = new Set(prev);
      if (next.has(rowIndex)) next.delete(rowIndex);
      else next.add(rowIndex);
      return next;
    });
  }

  async function handleConfirm() {
    if (!file || !accountId) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await ingestStatement(file, accountId, [...excluded]);
      setImportedCount(result.recordedCount);
      onImported();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not finish the import.');
    } finally {
      setSubmitting(false);
    }
  }

  const includedCount = preview
    ? preview.rows.filter((row) => row.status !== 'REJECTED' && !excluded.has(row.rowIndex)).length
    : 0;

  function statusChip(row: StatementRowPreview) {
    if (row.status === 'REJECTED') {
      return <Chip size="small" color="error" label={row.rejectionReason ?? 'Unusable row'} />;
    }
    if (row.status === 'ALREADY_RECORDED') {
      return <Chip size="small" color="default" label="Already recorded" />;
    }
    return null;
  }

  function categoryNameFor(categoryId?: string) {
    return categories.find((c) => c.id === categoryId)?.name;
  }

  return (
    <Modal open={open} onClose={handleClose} title="Import Transactions">
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}

        {importedCount !== null ? (
          <>
            <Alert severity="success">
              {importedCount} transaction{importedCount === 1 ? '' : 's'} imported.
            </Alert>
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
                onChange={(e) => { setAccountId(e.target.value); setPreview(null); setFile(null); }}
              >
                <MenuItem value="">Select an account</MenuItem>
                {accounts.map((acc) => (
                  <MenuItem key={acc.id} value={acc.id}>{acc.name}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <Button variant="outlined" component="label" disabled={!accountId}>
              Choose a statement file
              <input type="file" accept=".csv,text/csv" hidden onChange={handleFileChange} />
            </Button>

            {preview && (
              <>
                <Typography variant="body2" color="text.secondary">
                  {preview.newCount} new
                  {preview.alreadyRecordedCount > 0 && `, ${preview.alreadyRecordedCount} already recorded`}
                  {preview.rejectedCount > 0 && `, ${preview.rejectedCount} unusable`}
                  {' — nothing has been saved yet.'}
                </Typography>

                <List dense sx={{ maxHeight: 320, overflow: 'auto' }}>
                  {preview.rows.map((row) => (
                    <ListItem key={row.rowIndex} disableGutters sx={{ gap: 1, alignItems: 'flex-start' }}>
                      <Checkbox
                        size="small"
                        checked={!excluded.has(row.rowIndex)}
                        disabled={row.status === 'REJECTED'}
                        onChange={() => toggleExcluded(row.rowIndex)}
                      />
                      <Stack sx={{ flexGrow: 1 }}>
                        <Typography variant="body2">
                          {row.date} — {row.description || '(no description)'}
                          {row.amount && ` — ${row.direction === 'BILL' ? '−' : '+'}${fmt.format(Number(row.amount))}`}
                        </Typography>
                        {categoryNameFor(row.suggestedCategoryId) && (
                          <Typography variant="caption" color="text.secondary">
                            Suggested category: {categoryNameFor(row.suggestedCategoryId)}
                          </Typography>
                        )}
                      </Stack>
                      {statusChip(row)}
                    </ListItem>
                  ))}
                </List>

                <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                  <Button onClick={handleClose} disabled={submitting}>Cancel</Button>
                  <Button
                    variant="contained"
                    onClick={handleConfirm}
                    disabled={submitting || includedCount === 0}
                  >
                    Import {includedCount} transaction{includedCount === 1 ? '' : 's'}
                  </Button>
                </Stack>
              </>
            )}
          </>
        )}
      </Stack>
    </Modal>
  );
}
