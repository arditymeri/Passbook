import { useEffect, useState } from 'react';
import { useAccounts } from '../hooks/useAccounts';
import { AccountList } from './AccountList';
import { AddAccountForm } from './AddAccountForm';
import { SetupTemplateDialog } from './SetupTemplateDialog';
import type { Account, AccountType } from '../types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

interface AccountsPageProps {
  onBack: () => void;
}

export function AccountsPage({ onBack }: AccountsPageProps) {
  const { accounts, loading, error, refresh } = useAccounts();
  const [localAccounts, setLocalAccounts] = useState<Account[] | null>(null);
  const [addFormOpen, setAddFormOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [activeTypeFilter, setActiveTypeFilter] = useState<AccountType | 'ALL'>('ALL');

  const displayAccounts = localAccounts ?? accounts;

  useEffect(() => {
    if (!loading && displayAccounts.length === 0) {
      setAddFormOpen(true);
    }
  }, [loading, displayAccounts.length]);

  function handleSuccess(created: Account) {
    setLocalAccounts([...(localAccounts ?? accounts), created]);
    setAddFormOpen(false);
  }

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
        <IconButton onClick={onBack} aria-label="Back">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>Accounts</Typography>
        <Button variant="outlined" onClick={() => setTemplateDialogOpen(true)}>
          Use a starter template
        </Button>
      </Stack>

      <AccountList
        accounts={displayAccounts}
        loading={loading && localAccounts === null}
        error={error}
        activeTypeFilter={activeTypeFilter}
        onTypeFilterChange={setActiveTypeFilter}
        onAddClick={() => setAddFormOpen(true)}
      />

      <AddAccountForm
        open={addFormOpen}
        onClose={() => setAddFormOpen(false)}
        onSuccess={handleSuccess}
      />

      <SetupTemplateDialog
        open={templateDialogOpen}
        onClose={() => setTemplateDialogOpen(false)}
        onApplied={() => { setLocalAccounts(null); refresh(); }}
      />
    </Box>
  );
}
