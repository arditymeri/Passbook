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
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Button from '@mui/material/Button';
import { EMPTY_TRANSACTION_FILTERS } from '../types';
import type { Account, Category, IncomeSource, TransactionFilters, TransactionTypeFilter } from '../types';

const TYPE_FILTERS: TransactionTypeFilter[] = ['ALL', 'BILL', 'INCOME'];
const TYPE_FILTER_LABELS: Record<TransactionTypeFilter, string> = {
  ALL: 'All',
  BILL: 'Bills only',
  INCOME: 'Income only',
};

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

          <TextField
            label="From"
            type="date"
            size="small"
            value={filters.startDate ?? ''}
            onChange={(e) => onFiltersChange({ ...filters, startDate: e.target.value || undefined })}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="To"
            type="date"
            size="small"
            value={filters.endDate ?? ''}
            onChange={(e) => onFiltersChange({ ...filters, endDate: e.target.value || undefined })}
            slotProps={{ inputLabel: { shrink: true } }}
          />

          <TextField
            label="Min amount"
            type="number"
            size="small"
            sx={{ width: 130 }}
            value={filters.minAmount ?? ''}
            onChange={(e) => onFiltersChange({
              ...filters,
              minAmount: e.target.value === '' ? undefined : Number(e.target.value),
            })}
          />
          <TextField
            label="Max amount"
            type="number"
            size="small"
            sx={{ width: 130 }}
            value={filters.maxAmount ?? ''}
            onChange={(e) => onFiltersChange({
              ...filters,
              maxAmount: e.target.value === '' ? undefined : Number(e.target.value),
            })}
          />

          <ToggleButtonGroup
            value={filters.type}
            exclusive
            size="small"
            onChange={(_, val: TransactionTypeFilter | null) => {
              if (val !== null) onFiltersChange({ ...filters, type: val });
            }}
          >
            {TYPE_FILTERS.map((f) => (
              <ToggleButton key={f} value={f}>{TYPE_FILTER_LABELS[f]}</ToggleButton>
            ))}
          </ToggleButtonGroup>

          <Button variant="outlined" size="small" onClick={() => onFiltersChange(EMPTY_TRANSACTION_FILTERS)}>
            Clear filters
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
