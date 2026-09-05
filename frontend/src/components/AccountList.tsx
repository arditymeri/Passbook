import type { Account, AccountType } from '../types';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import AddIcon from '@mui/icons-material/Add';

interface AccountListProps {
  accounts: Account[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: AccountType | 'ALL';
  onTypeFilterChange: (filter: AccountType | 'ALL') => void;
  onAddClick: () => void;
}

const TYPE_FILTERS: Array<AccountType | 'ALL'> = ['ALL', 'CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH', 'INVESTMENT'];

export function AccountList({
  accounts,
  loading,
  error,
  activeTypeFilter,
  onTypeFilterChange,
  onAddClick,
}: AccountListProps) {
  const filtered = activeTypeFilter === 'ALL'
    ? accounts
    : accounts.filter((a) => a.type === activeTypeFilter);

  return (
    <Stack spacing={2}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        {/*
          Six type filters in one row are 452px wide — they fit a laptop and run off a phone. The
          layout check found this; it is not one of the components feature 025 set out to fix,
          which is rather the point of having a check that looks at every screen rather than the
          ones somebody remembered. Wrapping is additive: above 452px of available width nothing
          about this renders differently.
        */}
        <ToggleButtonGroup
          value={activeTypeFilter}
          exclusive
          onChange={(_, val) => { if (val !== null) onTypeFilterChange(val); }}
          size="small"
          sx={{ flexWrap: 'wrap' }}
        >
          {TYPE_FILTERS.map((f) => (
            <ToggleButton key={f} value={f}>{f}</ToggleButton>
          ))}
        </ToggleButtonGroup>
        <Button variant="contained" startIcon={<AddIcon />} onClick={onAddClick}>
          Add Account
        </Button>
      </Stack>

      {loading && (
        <Stack spacing={1}>
          {[0, 1, 2].map((i) => <Skeleton key={i} variant="rectangular" height={56} />)}
        </Stack>
      )}

      {!loading && error && <Alert severity="error">{error}</Alert>}

      {!loading && !error && filtered.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 4 }}>
          <Typography color="text.secondary" sx={{ mb: 2 }}>No accounts yet.</Typography>
          <Button variant="contained" onClick={onAddClick}>+ Add your first account</Button>
        </Box>
      )}

      {!loading && !error && filtered.length > 0 && (
        <List disablePadding>
          {filtered.map((acc) => {
            const fmt = new Intl.NumberFormat('de-AT', { style: 'currency', currency: acc.defaultCurrency });
            return (
              <ListItem
                key={acc.id}
                divider
                secondaryAction={
                  <Typography sx={{ color: acc.balance < 0 ? 'error.main' : 'text.primary', fontWeight: 600 }}>
                    {fmt.format(acc.balance)}
                  </Typography>
                }
              >
                <ListItemText
                  primary={acc.name}
                  secondary={`${acc.type} · ${acc.institution ?? '—'}`}
                />
              </ListItem>
            );
          })}
        </List>
      )}
    </Stack>
  );
}
