import { computeNetWorthTrend, currentNetWorth } from '../utils/netWorthTrend';
import type { Account, NetWorthTrendPoint, Transaction } from '../types';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';

interface NetWorthCardProps {
  accounts: Account[];
  allTransactions: Transaction[];
}

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });
const fmtCompact = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR', notation: 'compact' });

const CHART_WIDTH = 600;
const CHART_HEIGHT = 160;
const CHART_PADDING_X = 32;
const CHART_PADDING_Y = 28;

function TrendChart({ points }: { points: NetWorthTrendPoint[] }) {
  const values = points.map((p) => p.netWorth);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min;
  // A flat (or single-point) trend has no range to scale against — fall back to a fixed band
  // so the line still renders centered rather than dividing by zero.
  const paddedMin = range === 0 ? min - 1 : min - range * 0.1;
  const paddedMax = range === 0 ? max + 1 : max + range * 0.1;
  const paddedRange = paddedMax - paddedMin;

  const plotWidth = CHART_WIDTH - CHART_PADDING_X * 2;
  const plotHeight = CHART_HEIGHT - CHART_PADDING_Y * 2;

  function xFor(index: number): number {
    return points.length === 1
      ? CHART_PADDING_X + plotWidth / 2
      : CHART_PADDING_X + (index / (points.length - 1)) * plotWidth;
  }

  function yFor(value: number): number {
    return CHART_PADDING_Y + plotHeight - ((value - paddedMin) / paddedRange) * plotHeight;
  }

  const linePoints = points.map((p, i) => `${xFor(i)},${yFor(p.netWorth)}`).join(' ');
  const trendUp = values[values.length - 1] >= values[0];
  // Matches this app's default (unthemed) MUI success/error palette colors.
  const lineColor = trendUp ? '#2e7d32' : '#d32f2f';

  return (
    <Box sx={{ width: '100%', overflowX: 'auto' }}>
      <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} style={{ width: '100%', height: CHART_HEIGHT }} preserveAspectRatio="none">
        <polyline points={linePoints} fill="none" stroke={lineColor} strokeWidth={2} />
        {points.map((p, i) => (
          <g key={p.cutoff}>
            <circle cx={xFor(i)} cy={yFor(p.netWorth)} r={3.5} fill={lineColor} />
            <text x={xFor(i)} y={yFor(p.netWorth) - 8} textAnchor="middle" fontSize={11} fill="currentColor">
              {fmtCompact.format(p.netWorth)}
            </text>
            <text x={xFor(i)} y={CHART_HEIGHT - 6} textAnchor="middle" fontSize={11} fill="currentColor" opacity={0.7}>
              {p.label}
            </text>
          </g>
        ))}
      </svg>
    </Box>
  );
}

export function NetWorthCard({ accounts, allTransactions }: NetWorthCardProps) {
  if (accounts.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>Net Worth</Typography>
        <Typography color="text.secondary">No accounts yet — nothing to total.</Typography>
      </Paper>
    );
  }

  const total = currentNetWorth(accounts);
  const trend = computeNetWorthTrend(accounts, allTransactions, 6);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack spacing={0.5}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Net Worth</Typography>
          <Typography variant="h4" color={total >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 700 }}>
            {fmt.format(total)}
          </Typography>
        </Stack>
        <TrendChart points={trend} />
      </Stack>
    </Paper>
  );
}
