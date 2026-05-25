import type { Category, CategoryType } from '../types';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemAvatar from '@mui/material/ListItemAvatar';
import ListItemText from '@mui/material/ListItemText';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import AddIcon from '@mui/icons-material/Add';

interface CategoryListProps {
  categories: Category[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: CategoryType | 'ALL';
  onTypeFilterChange: (filter: CategoryType | 'ALL') => void;
  onAddClick: () => void;
}

const TYPE_FILTERS: Array<CategoryType | 'ALL'> = ['ALL', 'EXPENSE', 'INCOME', 'BOTH'];

export function CategoryList({
  categories,
  loading,
  error,
  activeTypeFilter,
  onTypeFilterChange,
  onAddClick,
}: CategoryListProps) {
  const filtered = activeTypeFilter === 'ALL'
    ? categories
    : categories.filter((c) => c.type === activeTypeFilter);

  return (
    <Stack spacing={2}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <ToggleButtonGroup
          value={activeTypeFilter}
          exclusive
          onChange={(_, val) => { if (val !== null) onTypeFilterChange(val); }}
          size="small"
        >
          {TYPE_FILTERS.map((f) => (
            <ToggleButton key={f} value={f}>{f}</ToggleButton>
          ))}
        </ToggleButtonGroup>
        <Button variant="contained" startIcon={<AddIcon />} onClick={onAddClick}>
          Add Category
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
          <Typography color="text.secondary" sx={{ mb: 2 }}>No categories yet.</Typography>
          <Button variant="contained" onClick={onAddClick}>+ Add your first category</Button>
        </Box>
      )}

      {!loading && !error && filtered.length > 0 && (
        <List disablePadding>
          {filtered.map((cat) => (
            <ListItem key={cat.id} divider>
              <ListItemAvatar>
                <Avatar sx={{ bgcolor: cat.color ?? 'grey.400', width: 36, height: 36, fontSize: 14 }}>
                  {cat.name[0].toUpperCase()}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={cat.name}
                secondary={cat.type}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Stack>
  );
}
