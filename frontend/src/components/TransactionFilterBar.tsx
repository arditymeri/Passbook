import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import ListSubheader from '@mui/material/ListSubheader';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Stack from '@mui/material/Stack';
import type { Account, Category, IncomeSource, TransactionFilters } from '../types';

interface TransactionFilterBarProps {
  filters: TransactionFilters;
  onFiltersChange: (filters: TransactionFilters) => void;
  categories: Category[];
  accounts: Account[];
}

const INCOME_SOURCES: IncomeSource[] = ['SALARY', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'GIFT', 'OTHER'];

function categorySourceValue(filters: TransactionFilters): string {
  if (filters.categoryId !== undefined) return `cat:${filters.categoryId}`;
  if (filters.source !== undefined) return `src:${filters.source}`;
  return '';
}

export function TransactionFilterBar({ filters, onFiltersChange, categories, accounts }: TransactionFilterBarProps) {
  function handleCategorySourceChange(value: string) {
    if (value === '') {
      onFiltersChange({ ...filters, categoryId: undefined, source: undefined });
    } else if (value.startsWith('cat:')) {
      onFiltersChange({ ...filters, categoryId: value.slice(4), source: undefined });
    } else if (value.startsWith('src:')) {
      onFiltersChange({ ...filters, categoryId: undefined, source: value.slice(4) as IncomeSource });
    }
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <TextField
          value={filters.searchText}
          onChange={(e) => onFiltersChange({ ...filters, searchText: e.target.value })}
          placeholder="Search transactions by description…"
          fullWidth
          size="small"
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
          <FormControl size="small" sx={{ minWidth: 220 }}>
            <InputLabel id="txn-filter-category-source-label">Category / Source</InputLabel>
            <Select
              labelId="txn-filter-category-source-label"
              label="Category / Source"
              value={categorySourceValue(filters)}
              onChange={(e) => handleCategorySourceChange(e.target.value)}
            >
              <MenuItem value="">All</MenuItem>
              <ListSubheader>Categories</ListSubheader>
              {categories.map((c) => (
                <MenuItem key={c.id} value={`cat:${c.id}`}>{c.name}</MenuItem>
              ))}
              <ListSubheader>Income Sources</ListSubheader>
              {INCOME_SOURCES.map((s) => (
                <MenuItem key={s} value={`src:${s}`}>{s}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="txn-filter-account-label">Account</InputLabel>
            <Select
              labelId="txn-filter-account-label"
              label="Account"
              value={filters.accountId ?? ''}
              onChange={(e) => onFiltersChange({ ...filters, accountId: e.target.value || undefined })}
            >
              <MenuItem value="">All</MenuItem>
              {accounts.map((a) => (
                <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </Stack>
    </Paper>
  );
}
