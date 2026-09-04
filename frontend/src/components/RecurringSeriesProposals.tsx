import { useEffect, useState } from 'react';
import { detectRecurringSeries, confirmRecurringSeries, dismissRecurringSeries, stopRecurringSeries } from '../api/client';
import type { Category, RecurringSeries } from '../types';
import { Modal } from './Modal';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import Chip from '@mui/material/Chip';

interface RecurringSeriesProposalsProps {
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
  categories: Category[];
}

export function RecurringSeriesProposals({ open, onClose, onChanged, categories }: RecurringSeriesProposalsProps) {
  const [series, setSeries] = useState<RecurringSeries[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    detectRecurringSeries()
      .then((result) => { if (!cancelled) { setSeries(result); setError(null); } })
      .catch(() => { if (!cancelled) setError('Could not check for recurring series — please try again'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open]);

  function groupLabel(s: RecurringSeries): string {
    if (s.transactionType === 'INCOME') return s.groupKey;
    return categories.find((c) => c.id === s.groupKey)?.name ?? s.groupKey;
  }

  async function handleConfirm(id: string) {
    setBusyId(id);
    try {
      const updated = await confirmRecurringSeries(id);
      setSeries((prev) => prev.map((s) => (s.id === id ? updated : s)));
      onChanged();
    } catch {
      setError('Could not confirm — please try again');
    } finally {
      setBusyId(null);
    }
  }

  async function handleDismiss(id: string) {
    setBusyId(id);
    try {
      const updated = await dismissRecurringSeries(id);
      setSeries((prev) => prev.map((s) => (s.id === id ? updated : s)));
      onChanged();
    } catch {
      setError('Could not dismiss — please try again');
    } finally {
      setBusyId(null);
    }
  }

  /**
   * Stopping is not dismissing. Dismissing says the detection was wrong in the first place;
   * stopping says the thing was real and has now ended — a cancelled subscription, a tenancy moved
   * out of. Collapsing the two would lose that difference, and with it the series' history.
   */
  async function handleStop(id: string) {
    setBusyId(id);
    try {
      const updated = await stopRecurringSeries(id);
      setSeries((prev) => prev.map((s) => (s.id === id ? { ...s, status: updated.status } : s)));
      onChanged();
    } catch {
      setError('Could not stop this series — please try again');
    } finally {
      setBusyId(null);
    }
  }

  const proposed = series.filter((s) => s.status === 'PROPOSED');
  const confirmed = series.filter((s) => s.status === 'CONFIRMED');
  const stopped = series.filter((s) => s.status === 'STOPPED');

  return (
    <Modal open={open} onClose={onClose} title="Recurring Series">
      <Stack spacing={2}>
        {error && <Alert severity="error">{error}</Alert>}

        {loading && (
          <Stack spacing={1}>
            {[0, 1].map((i) => <Skeleton key={i} variant="rectangular" height={48} />)}
          </Stack>
        )}

        {!loading && (
          <>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Pending proposals</Typography>
            {proposed.length === 0 && <Typography color="text.secondary">Nothing new detected.</Typography>}
            <List disablePadding>
              {proposed.map((s) => (
                <ListItem
                  key={s.id}
                  divider
                  secondaryAction={
                    <Stack direction="row" spacing={1}>
                      <Button size="small" variant="contained" disabled={busyId === s.id} onClick={() => handleConfirm(s.id)}>
                        Confirm
                      </Button>
                      <Button size="small" variant="outlined" disabled={busyId === s.id} onClick={() => handleDismiss(s.id)}>
                        Dismiss
                      </Button>
                    </Stack>
                  }
                >
                  <ListItemText primary={s.description} secondary={`${groupLabel(s)} · ${s.frequency}`} />
                </ListItem>
              ))}
            </List>

            <Typography variant="subtitle2" sx={{ fontWeight: 700, mt: 2 }}>Confirmed</Typography>
            <Typography variant="caption" color="text.secondary">
              A confirmed series posts its own transactions as they come due. Stop one when it ends.
            </Typography>
            {confirmed.length === 0 && <Typography color="text.secondary">None yet.</Typography>}
            <List disablePadding>
              {confirmed.map((s) => (
                <ListItem
                  key={s.id}
                  divider
                  secondaryAction={
                    <Button size="small" variant="outlined" disabled={busyId === s.id} onClick={() => handleStop(s.id)}>
                      Stop
                    </Button>
                  }
                >
                  <ListItemText primary={s.description} secondary={`${groupLabel(s)} · ${s.frequency}`} />
                </ListItem>
              ))}
            </List>

            {stopped.length > 0 && (
              <>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mt: 2 }}>Stopped</Typography>
                <List disablePadding>
                  {stopped.map((s) => (
                    <ListItem key={s.id} divider secondaryAction={<Chip label="Stopped" size="small" variant="outlined" />}>
                      <ListItemText
                        primary={s.description}
                        secondary={`${groupLabel(s)} · ${s.frequency} · no longer posting`}
                        sx={{ opacity: 0.7 }}
                      />
                    </ListItem>
                  ))}
                </List>
              </>
            )}
          </>
        )}
      </Stack>
    </Modal>
  );
}
