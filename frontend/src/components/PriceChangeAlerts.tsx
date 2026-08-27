import { useEffect, useState } from 'react';
import { fetchRecurringDashboard } from '../api/client';
import type { Category, PriceChangeAlert } from '../types';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';

interface PriceChangeAlertsProps {
  categories: Category[];
  refreshKey?: number;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });
const deltaFmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR', signDisplay: 'always' });

export function PriceChangeAlerts({ categories, refreshKey = 0 }: PriceChangeAlertsProps) {
  const [alerts, setAlerts] = useState<PriceChangeAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchRecurringDashboard()
      .then((dashboard) => {
        if (cancelled) return;
        setAlerts(dashboard.recentPriceChanges);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load price change alerts');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [refreshKey]);

  function groupLabel(alert: PriceChangeAlert): string {
    if (alert.transactionType === 'INCOME') return alert.groupKey;
    return categories.find((c) => c.id === alert.groupKey)?.name ?? alert.groupKey;
  }

  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          {[0, 1].map((i) => <Skeleton key={i} variant="rectangular" height={48} />)}
        </Stack>
      </Paper>
    );
  }
  if (error) return <Alert severity="error">{error}</Alert>;
  if (alerts.length === 0) return null;

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Price Changes</Typography>
      <List disablePadding>
        {alerts.map((alert) => (
          <ListItem key={alert.transactionId} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.75 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <ListItemText
                primary={alert.description}
                secondary={`${groupLabel(alert)} · ${fmt.format(alert.priorAmount)} → ${fmt.format(alert.newAmount)}`}
              />
              <Typography sx={{ fontWeight: 600, color: alert.delta > 0 ? 'error.main' : 'success.main' }}>
                {deltaFmt.format(alert.delta)}
              </Typography>
            </Stack>
          </ListItem>
        ))}
      </List>
    </Paper>
  );
}
