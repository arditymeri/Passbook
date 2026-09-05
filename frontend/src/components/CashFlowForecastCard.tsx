import { useEffect, useState } from 'react';
import { fetchCashFlowForecast } from '../api/client';
import type { AccountForecast, CashFlowForecastResponse, CashFlowWindowWeeks, ForecastEntry } from '../types';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';

const WINDOW_OPTIONS: CashFlowWindowWeeks[] = [2, 4, 8, 12];

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });
const dateFmt = new Intl.DateTimeFormat('default', { month: 'short', day: 'numeric' });

function ForecastTimelineList({ timeline }: { timeline: ForecastEntry[] }) {
  if (timeline.length === 0) {
    return <Typography color="text.secondary" sx={{ pl: 1, pb: 1 }}>No confirmed recurring items in this window.</Typography>;
  }
  return (
    <List disablePadding>
      {timeline.map((entry, i) => (
        <ListItem key={`${entry.seriesId}-${entry.date}-${i}`} disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.5, pl: 1 }}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <ListItemText
              primary={entry.description}
              secondary={dateFmt.format(new Date(entry.date))}
            />
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Typography sx={{ fontWeight: 600, color: entry.transactionType === 'BILL' ? 'error.main' : 'success.main' }}>
                {entry.transactionType === 'BILL' ? '-' : '+'}{fmt.format(entry.amount)}
              </Typography>
              <Typography variant="caption" color={entry.projectedBalance >= 0 ? 'text.secondary' : 'error.main'}>
                balance {fmt.format(entry.projectedBalance)}
              </Typography>
            </Stack>
          </Stack>
        </ListItem>
      ))}
    </List>
  );
}

function AccountForecastRow({ forecast, expanded, onToggle }: {
  forecast: AccountForecast;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <Stack>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography sx={{ flex: 1, minWidth: { xs: 0, sm: 140 }, fontWeight: 600 }}>{forecast.accountName}</Typography>
        <Typography color={forecast.currentBalance >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 700 }}>
          {fmt.format(forecast.currentBalance)}
        </Typography>
        {forecast.atRisk && <Chip label="At risk" color="error" size="small" />}
        <IconButton
          size="small"
          onClick={onToggle}
          aria-label={expanded ? 'Hide timeline' : 'Show timeline'}
          sx={{ transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
        >
          <ExpandMoreIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Collapse in={expanded}>
        <ForecastTimelineList timeline={forecast.timeline} />
      </Collapse>
    </Stack>
  );
}

export function CashFlowForecastCard() {
  const [data, setData] = useState<CashFlowForecastResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [windowWeeks, setWindowWeeks] = useState<CashFlowWindowWeeks>(4);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchCashFlowForecast(windowWeeks)
      .then((res) => { if (!cancelled) setData(res); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [windowWeeks]);

  function toggleExpanded(accountId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(accountId)) next.delete(accountId); else next.add(accountId);
      return next;
    });
  }

  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <Skeleton variant="rectangular" height={24} />
          <Skeleton variant="rectangular" height={24} />
        </Stack>
      </Paper>
    );
  }

  if (!data || data.accounts.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>Cash Flow Forecast</Typography>
        <Typography color="text.secondary">No accounts yet — nothing to forecast.</Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Cash Flow Forecast</Typography>
          <ToggleButtonGroup
            value={windowWeeks}
            exclusive
            size="small"
            onChange={(_, val: CashFlowWindowWeeks | null) => {
              if (val !== null) setWindowWeeks(val);
            }}
          >
            {WINDOW_OPTIONS.map((weeks) => (
              <ToggleButton key={weeks} value={weeks}>{weeks}w</ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Stack>
        <Stack spacing={1} divider={<Stack sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
          {data.accounts.map((forecast) => (
            <AccountForecastRow
              key={forecast.accountId}
              forecast={forecast}
              expanded={expandedIds.has(forecast.accountId)}
              onToggle={() => toggleExpanded(forecast.accountId)}
            />
          ))}
        </Stack>
      </Stack>
    </Paper>
  );
}
