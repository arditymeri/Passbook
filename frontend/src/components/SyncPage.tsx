import { useState } from 'react';
import type * as React from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Alert from '@mui/material/Alert';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import { applySyncImport, fetchSyncExport, previewSyncImport } from '../api/client';
import { downloadJsonFile } from '../utils/downloadFile';
import type { EntityMergeCounts, ImportSummary, SyncSnapshot } from '../types';
import { PageHeader } from './PageHeader';

interface SyncPageProps {
  onBack: () => void;
}

const BREAKDOWN_ROWS: Array<{ key: keyof ImportSummary & string; label: string }> = [
  { key: 'accounts', label: 'Accounts' },
  { key: 'categories', label: 'Categories' },
  { key: 'budgets', label: 'Budgets' },
  { key: 'recurringSeries', label: 'Recurring series' },
  { key: 'bills', label: 'Bills' },
  { key: 'incomes', label: 'Incomes' },
  { key: 'savingsGoals', label: 'Savings goals' },
];

function totalsOf(summary: ImportSummary): EntityMergeCounts {
  return BREAKDOWN_ROWS.reduce(
    (acc, { key }) => {
      const counts = summary[key] as EntityMergeCounts;
      return {
        added: acc.added + counts.added,
        updated: acc.updated + counts.updated,
        unchanged: acc.unchanged + counts.unchanged,
      };
    },
    { added: 0, updated: 0, unchanged: 0 },
  );
}

export function SyncPage({ onBack }: SyncPageProps) {
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const [pendingSnapshot, setPendingSnapshot] = useState<SyncSnapshot | null>(null);
  const [preview, setPreview] = useState<ImportSummary | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [applying, setApplying] = useState(false);
  const [applied, setApplied] = useState<ImportSummary | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  async function handleExport() {
    setExporting(true);
    setExportError(null);
    try {
      const snapshot = await fetchSyncExport();
      const isoDate = new Date().toISOString().slice(0, 10);
      downloadJsonFile(`passbook-sync-${isoDate}.json`, snapshot);
    } catch {
      setExportError('Could not export — please try again');
    } finally {
      setExporting(false);
    }
  }

  function resetImport() {
    setPendingSnapshot(null);
    setPreview(null);
    setApplied(null);
    setImportError(null);
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    resetImport();
    const reader = new FileReader();
    reader.onload = async () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      let snapshot: SyncSnapshot;
      try {
        snapshot = JSON.parse(text) as SyncSnapshot;
      } catch {
        setImportError('That file is not valid JSON');
        return;
      }
      setPreviewing(true);
      try {
        const summary = await previewSyncImport(snapshot);
        setPendingSnapshot(snapshot);
        setPreview(summary);
      } catch {
        setImportError('Could not read this snapshot — it may be malformed or from an incompatible version');
      } finally {
        setPreviewing(false);
      }
    };
    reader.onerror = () => setImportError('Could not read the file — please try again');
    reader.readAsText(file);
  }

  async function handleConfirmImport() {
    if (!pendingSnapshot) return;
    setApplying(true);
    setImportError(null);
    try {
      const summary = await applySyncImport(pendingSnapshot);
      setApplied(summary);
      setPreview(null);
      setPendingSnapshot(null);
    } catch {
      setImportError('Could not apply this import — please try again');
    } finally {
      setApplying(false);
    }
  }

  const totals = preview ? totalsOf(preview) : null;

  return (
    <Box sx={{ p: 2, maxWidth: 720 }}>
      <PageHeader title="Device Sync" onBack={onBack} />

      <Stack spacing={2.5}>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>Export</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            Download every account, category, budget, bill, income, recurring series, and savings
            goal on this device into one file, to import on another device.
          </Typography>
          {exportError && <Alert severity="error" sx={{ mb: 2 }}>{exportError}</Alert>}
          <Button variant="contained" onClick={handleExport} disabled={exporting}>
            {exporting ? 'Exporting…' : 'Export'}
          </Button>
        </Paper>

        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>Import</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            Choose a snapshot file exported from another device. Nothing is applied until you
            confirm — existing local data is never deleted or overwritten with older data.
          </Typography>

          {importError && <Alert severity="error" sx={{ mb: 2 }}>{importError}</Alert>}

          {applied ? (
            <>
              <Alert severity="success" sx={{ mb: 2 }}>Import applied.</Alert>
              <ImportBreakdown summary={applied} />
              <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 2 }}>
                <Button variant="outlined" onClick={resetImport}>Done</Button>
              </Stack>
            </>
          ) : preview && totals ? (
            <>
              <Typography sx={{ mb: 1 }}>
                {totals.added} item{totals.added === 1 ? '' : 's'} will be added,{' '}
                {totals.updated} updated, {totals.unchanged} unchanged.
              </Typography>
              {preview.correctionConflictsResolved > 0 && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  {preview.correctionConflictsResolved} correction conflict
                  {preview.correctionConflictsResolved === 1 ? '' : 's'} resolved — the more
                  recently made correction was kept on each.
                </Alert>
              )}
              <ImportBreakdown summary={preview} />
              <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', mt: 2 }}>
                <Button variant="outlined" onClick={resetImport} disabled={applying}>Cancel</Button>
                <Button variant="contained" onClick={handleConfirmImport} disabled={applying}>
                  {applying ? 'Applying…' : 'Confirm Import'}
                </Button>
              </Stack>
            </>
          ) : (
            <Button variant="outlined" component="label" disabled={previewing}>
              {previewing ? 'Reading…' : 'Choose Snapshot File'}
              <input type="file" accept=".json,application/json" hidden onChange={handleFileChange} disabled={previewing} />
            </Button>
          )}
        </Paper>
      </Stack>
    </Box>
  );
}

function ImportBreakdown({ summary }: { summary: ImportSummary }) {
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Data type</TableCell>
            <TableCell align="right">Added</TableCell>
            <TableCell align="right">Updated</TableCell>
            <TableCell align="right">Unchanged</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {BREAKDOWN_ROWS.map(({ key, label }) => {
            const counts = summary[key] as EntityMergeCounts;
            return (
              <TableRow key={key}>
                <TableCell>{label}</TableCell>
                <TableCell align="right">{counts.added}</TableCell>
                <TableCell align="right">{counts.updated}</TableCell>
                <TableCell align="right">{counts.unchanged}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
