import { useState } from 'react';
import { useSavingsGoals } from '../hooks/useSavingsGoals';
import { useAccounts } from '../hooks/useAccounts';
import { deleteSavingsGoal } from '../api/client';
import { SavingsGoalForm } from './SavingsGoalForm';
import { PageHeader } from './PageHeader';
import type { SavingsGoalStatus } from '../types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });
const dateFmt = new Intl.DateTimeFormat('default', { year: 'numeric', month: 'short', day: 'numeric' });

interface SavingsGoalsPageProps {
  onBack: () => void;
}

function paceChip(goal: SavingsGoalStatus) {
  if (goal.achieved) return <Chip label="ACHIEVED" color="success" size="small" />;
  if (!goal.paceStatus) return null;
  if (goal.paceStatus === 'ON_PACE') return <Chip label="ON PACE" color="success" size="small" />;
  if (goal.paceStatus === 'OVERDUE') return <Chip label="OVERDUE" color="error" size="small" />;
  return <Chip label="BEHIND PACE" color="warning" size="small" />;
}

export function SavingsGoalsPage({ onBack }: SavingsGoalsPageProps) {
  const { goals, loading, error, refresh } = useSavingsGoals();
  const { accounts } = useAccounts();
  const [formOpen, setFormOpen] = useState(false);
  const [editingGoal, setEditingGoal] = useState<SavingsGoalStatus | null>(null);
  const [deletingGoal, setDeletingGoal] = useState<SavingsGoalStatus | null>(null);
  const [deleting, setDeleting] = useState(false);

  function openCreate() {
    setEditingGoal(null);
    setFormOpen(true);
  }

  function openEdit(goal: SavingsGoalStatus) {
    setEditingGoal(goal);
    setFormOpen(true);
  }

  async function handleConfirmDelete() {
    if (!deletingGoal) return;
    setDeleting(true);
    try {
      await deleteSavingsGoal(deletingGoal.id);
      setDeletingGoal(null);
      refresh();
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Box sx={{ p: 2 }}>
      <PageHeader title="Savings Goals" onBack={onBack}>
        <Button variant="contained" onClick={openCreate}>+ New Goal</Button>
      </PageHeader>

      {loading && (
        <Stack spacing={1.5}>
          {[0, 1].map((i) => <Skeleton key={i} variant="rectangular" height={96} />)}
        </Stack>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {!loading && !error && goals.length === 0 && (
        <Paper sx={{ p: 2 }}>
          <Typography color="text.secondary">No savings goals yet — create one to get started.</Typography>
        </Paper>
      )}

      <Stack spacing={2}>
        {goals.map((goal) => (
          <Paper key={goal.id} sx={{ p: 2 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{goal.name}</Typography>
              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                {paceChip(goal)}
                <IconButton size="small" aria-label="Edit goal" onClick={() => openEdit(goal)}>
                  <EditIcon fontSize="small" />
                </IconButton>
                <IconButton size="small" aria-label="Delete goal" onClick={() => setDeletingGoal(goal)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={goal.percentComplete}
              color={goal.achieved ? 'success' : 'primary'}
              sx={{ borderRadius: 1, height: 8 }}
            />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.75 }}>
              <Typography variant="caption" color="text.secondary">
                Saved: {fmt.format(goal.savedAmount)} of {fmt.format(goal.targetAmount)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {goal.percentComplete.toFixed(0)}%
              </Typography>
              <Typography variant="caption" color={goal.achieved ? 'success.main' : 'text.secondary'}>
                Remaining: {fmt.format(goal.remainingAmount)}
              </Typography>
            </Box>
            {goal.targetDate && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                Target date: {dateFmt.format(new Date(goal.targetDate))}
              </Typography>
            )}
          </Paper>
        ))}
      </Stack>

      <SavingsGoalForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSuccess={refresh}
        accounts={accounts}
        goal={editingGoal}
      />

      <Dialog open={deletingGoal !== null} onClose={() => setDeletingGoal(null)} fullWidth maxWidth="xs">
        <DialogTitle sx={{ fontWeight: 700 }}>Delete this goal?</DialogTitle>
        <DialogContent>
          {deletingGoal && (
            <Typography>
              <strong>{deletingGoal.name}</strong> will be removed. Its linked account and
              transaction history are not affected.
            </Typography>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button variant="outlined" onClick={() => setDeletingGoal(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleConfirmDelete} disabled={deleting}>
            {deleting ? 'Deleting…' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
