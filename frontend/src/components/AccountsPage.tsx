import { useEffect, useState } from 'react';
import { useAccounts } from '../hooks/useAccounts';
import { AccountList } from './AccountList';
import { AddAccountForm } from './AddAccountForm';
import { SetupTemplateDialog } from './SetupTemplateDialog';
import { PageHeader } from './PageHeader';
import type { Account, AccountType } from '../types';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';

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
      <PageHeader title="Accounts" onBack={onBack}>
        <Button variant="outlined" onClick={() => setTemplateDialogOpen(true)}>
          Use a starter template
        </Button>
      </PageHeader>

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
