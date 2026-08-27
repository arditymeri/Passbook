import { useState } from 'react';
import { useSavingsGoals } from '../hooks/useSavingsGoals';
import { useAccounts } from '../hooks/useAccounts';
import { SavingsGoalForm } from './SavingsGoalForm';
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
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

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

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
        <IconButton onClick={onBack} aria-label="Back">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>Savings Goals</Typography>
        <Button variant="contained" onClick={() => setFormOpen(true)}>+ New Goal</Button>
      </Stack>

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
              {paceChip(goal)}
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
      />
    </Box>
  );
}
