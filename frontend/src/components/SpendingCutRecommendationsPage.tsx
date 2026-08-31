import { useEffect, useState } from 'react';
import { fetchBudgetStatus, fetchRecurringCostSummary } from '../api/client';
import { computeSpendingCutRecommendations } from '../utils/spendingCutRecommendations';
import { computeSpendingTrends } from '../utils/spendingTrends';
import type { BudgetStatusEntry, CategoryNameMap, RecurringCostSummaryItem, SpendingCutRecommendations, Transaction } from '../types';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Skeleton from '@mui/material/Skeleton';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';

const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' });

interface SpendingCutRecommendationsPageProps {
  allTransactions: Transaction[];
  categoryNames: CategoryNameMap;
}

/** The most recently *completed* calendar month, handling the January → prior December rollover. */
function mostRecentlyCompletedMonth(): { year: number; month: number } {
  const now = new Date();
  const prior = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
  return { year: prior.getUTCFullYear(), month: prior.getUTCMonth() + 1 };
}

export function SpendingCutRecommendationsPage({ allTransactions, categoryNames }: SpendingCutRecommendationsPageProps) {
  const [recurringItems, setRecurringItems] = useState<RecurringCostSummaryItem[]>([]);
  const [budgetStatusEntries, setBudgetStatusEntries] = useState<BudgetStatusEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const { year, month } = mostRecentlyCompletedMonth();
    Promise.all([fetchRecurringCostSummary(), fetchBudgetStatus(year, month)])
      .then(([items, entries]) => {
        if (cancelled) return;
        setRecurringItems(items);
        setBudgetStatusEntries(entries);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

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

  const { movers } = computeSpendingTrends(allTransactions, categoryNames, 6);
  const recommendations: SpendingCutRecommendations = computeSpendingCutRecommendations(
    recurringItems,
    allTransactions,
    budgetStatusEntries,
    movers,
    categoryNames
  );
  const isEmpty =
    recommendations.recurringItems.length === 0 &&
    recommendations.taggedTransactions.length === 0 &&
    recommendations.categoryOpportunities.length === 0;

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Spending Cut Recommendations</Typography>
        {!isEmpty && (
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
            Potential savings: {fmt.format(recommendations.potentialMonthlySavings)}/mo
          </Typography>
        )}
      </Stack>

      {isEmpty ? (
        <Typography color="text.secondary">
          Confirm a recurring bill, tag a transaction, or set a budget to see cost-cutting recommendations here.
        </Typography>
      ) : (
        <>
          {recommendations.recurringItems.length > 0 && (
            <>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>Recurring Costs</Typography>
              <List disablePadding>
                {recommendations.recurringItems.map((item) => (
                  <ListItem key={item.seriesId} disablePadding sx={{ py: 0.5 }}>
                    <ListItemText
                      primary={item.description}
                      secondary={item.priceIncreased
                        ? `Price increased ${fmt.format(item.originalAmount)} → ${fmt.format(item.monthlyEquivalentAmount)} (+${fmt.format(item.increaseAmount ?? 0)})`
                        : undefined}
                    />
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      {item.priceIncreased && <Chip label="Price increased" color="warning" size="small" />}
                      <Typography sx={{ fontWeight: 600 }}>
                        {fmt.format(item.monthlyEquivalentAmount)}/mo
                      </Typography>
                    </Stack>
                  </ListItem>
                ))}
              </List>
              <Divider sx={{ my: 1 }} />
              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Total monthly recurring spend</Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {fmt.format(recommendations.totalMonthlyRecurringSpend)}
                </Typography>
              </Stack>
            </>
          )}

          {recommendations.taggedTransactions.length > 0 && (
            <>
              <Divider sx={{ my: 1.5 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>Tagged as Avoidable / Unnecessary</Typography>
              <List disablePadding>
                {recommendations.taggedTransactions.map((t) => (
                  <ListItem key={t.transactionId} disablePadding sx={{ py: 0.5 }}>
                    <ListItemText primary={t.description ?? '—'} />
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Chip
                        label={t.tag === 'UNNECESSARY' ? 'Unnecessary' : 'Avoidable'}
                        color={t.tag === 'UNNECESSARY' ? 'error' : 'warning'}
                        size="small"
                      />
                      <Typography sx={{ fontWeight: 600 }}>{fmt.format(t.amount)}</Typography>
                    </Stack>
                  </ListItem>
                ))}
              </List>
            </>
          )}

          {recommendations.categoryOpportunities.length > 0 && (
            <>
              <Divider sx={{ my: 1.5 }} />
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>Categories to Watch</Typography>
              <List disablePadding>
                {recommendations.categoryOpportunities.map((c) => (
                  <ListItem key={c.categoryId} disablePadding sx={{ py: 0.5 }}>
                    <ListItemText
                      primary={c.categoryName}
                      secondary={c.reason === 'BOTH'
                        ? 'Over budget & trending up'
                        : c.reason === 'OVER_BUDGET' ? 'Over budget last month' : 'Trending up'}
                    />
                    <Typography sx={{ fontWeight: 600 }}>+{fmt.format(c.excessAmount)}</Typography>
                  </ListItem>
                ))}
              </List>
            </>
          )}
        </>
      )}
    </Paper>
  );
}
