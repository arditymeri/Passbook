import { useEffect, useState } from 'react';
import { fetchRecurringDashboard } from '../api/client';
import type { Category, UpcomingRecurringItem } from '../types';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';

interface UpcomingRecurringProps {
  categories: Category[];
  refreshKey?: number;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });
const dateFmt = new Intl.DateTimeFormat('default', { month: 'short', day: 'numeric' });

export function UpcomingRecurring({ categories, refreshKey = 0 }: UpcomingRecurringProps) {
  const [items, setItems] = useState<UpcomingRecurringItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchRecurringDashboard()
      .then((dashboard) => {
        if (cancelled) return;
        setItems(dashboard.upcoming);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load upcoming recurring items');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [refreshKey]);

  function groupLabel(item: UpcomingRecurringItem): string {
    if (item.transactionType === 'INCOME') return item.groupKey;
    return categories.find((c) => c.id === item.groupKey)?.name ?? item.groupKey;
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

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Upcoming</Typography>
      {items.length === 0 && (
        <Typography color="text.secondary">Nothing recognized as recurring yet.</Typography>
      )}
      <List disablePadding>
        {items.map((item) => (
          <ListItem key={item.seriesId} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.75 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <ListItemText
                primary={item.description}
                secondary={`${groupLabel(item)} · ${dateFmt.format(new Date(item.predictedDate))}`}
              />
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                {item.overdue && <Chip label="OVERDUE" color="error" size="small" />}
                <Typography sx={{ fontWeight: 600, color: item.overdue ? 'error.main' : 'text.primary' }}>
                  {fmt.format(item.predictedAmount)}
                </Typography>
              </Stack>
            </Stack>
          </ListItem>
        ))}
      </List>
    </Paper>
  );
}
