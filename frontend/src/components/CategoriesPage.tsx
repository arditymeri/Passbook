import { useEffect, useState } from 'react';
import { useCategories } from '../hooks/useCategories';
import { CategoryList } from './CategoryList';
import { AddCategoryForm } from './AddCategoryForm';
import { SetupTemplateDialog } from './SetupTemplateDialog';
import type { Category, CategoryType } from '../types';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

interface CategoriesPageProps {
  onBack: () => void;
}

export function CategoriesPage({ onBack }: CategoriesPageProps) {
  const { categories, loading, error, refresh } = useCategories();
  const [localCategories, setLocalCategories] = useState<Category[] | null>(null);
  const [addFormOpen, setAddFormOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [activeTypeFilter, setActiveTypeFilter] = useState<CategoryType | 'ALL'>('ALL');

  const displayCategories = localCategories ?? categories;

  useEffect(() => {
    if (!loading && displayCategories.length === 0) {
      setAddFormOpen(true);
    }
  }, [loading, displayCategories.length]);

  function handleSuccess(created: Category) {
    setLocalCategories([...(localCategories ?? categories), created]);
    setAddFormOpen(false);
  }

  return (
    <Box sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
        <IconButton onClick={onBack} aria-label="Back">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>Categories</Typography>
        <Button variant="outlined" onClick={() => setTemplateDialogOpen(true)}>
          Use a starter template
        </Button>
      </Stack>

      <CategoryList
        categories={displayCategories}
        loading={loading && localCategories === null}
        error={error}
        activeTypeFilter={activeTypeFilter}
        onTypeFilterChange={setActiveTypeFilter}
        onAddClick={() => setAddFormOpen(true)}
      />

      <AddCategoryForm
        open={addFormOpen}
        onClose={() => setAddFormOpen(false)}
        onSuccess={handleSuccess}
        existingCategories={displayCategories}
      />

      <SetupTemplateDialog
        open={templateDialogOpen}
        onClose={() => setTemplateDialogOpen(false)}
        onApplied={() => { setLocalCategories(null); refresh(); }}
      />
    </Box>
  );
}
