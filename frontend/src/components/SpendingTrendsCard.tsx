import { useState } from 'react';
import { computeSpendingTrends } from '../utils/spendingTrends';
import type { CategoryNameMap, CategorySpendingTrend, SpendingMover, SpendingTrendRangeMonths, Transaction } from '../types';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Divider from '@mui/material/Divider';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';

const RANGE_OPTIONS: SpendingTrendRangeMonths[] = [3, 6, 12];

interface SpendingTrendsCardProps {
  allTransactions: Transaction[];
  categoryNames: CategoryNameMap;
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const pctFmt = new Intl.NumberFormat('de-AT', { maximumFractionDigits: 0 });

function TrendRow({ trend }: { trend: CategorySpendingTrend }) {
  return (
    <ListItem disablePadding sx={{ flexDirection: 'column', alignItems: 'stretch', py: 0.75 }}>
      <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>{trend.categoryName}</Typography>
      <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap' }}>
        {trend.points.map((p) => (
          <Stack key={p.cutoff} sx={{ alignItems: 'center', minWidth: 44 }}>
            <Typography variant="caption" color="text.secondary">{p.label}</Typography>
            <Typography variant="body2" sx={{ fontWeight: p.amount > 0 ? 600 : 400 }} color={p.amount > 0 ? 'text.primary' : 'text.disabled'}>
              {fmt.format(p.amount)}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </ListItem>
  );
}

function MoverRow({ mover }: { mover: SpendingMover }) {
  const increased = mover.change > 0;
  const color = increased ? 'error.main' : 'success.main';
  return (
    <ListItem disablePadding sx={{ py: 0.5 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
        <Typography variant="body2">{mover.categoryName}</Typography>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          {increased ? <ArrowUpwardIcon sx={{ fontSize: 16, color }} /> : <ArrowDownwardIcon sx={{ fontSize: 16, color }} />}
          <Typography variant="body2" sx={{ fontWeight: 600, color }}>
            {fmt.format(Math.abs(mover.change))}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {mover.percentChange === null ? 'new' : `${pctFmt.format(Math.abs(mover.percentChange))}%`}
          </Typography>
        </Stack>
      </Stack>
    </ListItem>
  );
}

export function SpendingTrendsCard({ allTransactions, categoryNames }: SpendingTrendsCardProps) {
  const [rangeMonths, setRangeMonths] = useState<SpendingTrendRangeMonths>(6);
  // movers is mathematically independent of rangeMonths (it always compares the fixed current
  // and previous calendar months — research.md §3), so one call correctly serves both halves
  // regardless of which window is selected.
  const { trends, movers } = computeSpendingTrends(allTransactions, categoryNames, rangeMonths);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Spending Trends</Typography>
        <ToggleButtonGroup
          value={rangeMonths}
          exclusive
          size="small"
          onChange={(_, val: SpendingTrendRangeMonths | null) => {
            if (val !== null) setRangeMonths(val);
          }}
        >
          {RANGE_OPTIONS.map((months) => (
            <ToggleButton key={months} value={months}>{months}mo</ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Stack>
      {trends.length === 0 ? (
        <Typography color="text.secondary">No spending data yet.</Typography>
      ) : (
        <List disablePadding>
          {trends.map((trend) => (
            <TrendRow key={trend.categoryId} trend={trend} />
          ))}
        </List>
      )}

      {movers.length > 0 && (
        <>
          <Divider sx={{ my: 1.5 }} />
          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>Biggest Movers</Typography>
          <List disablePadding>
            {movers.map((mover) => (
              <MoverRow key={mover.categoryId} mover={mover} />
            ))}
          </List>
        </>
      )}
    </Paper>
  );
}
