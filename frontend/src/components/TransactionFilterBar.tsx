import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import type { TransactionFilters } from '../types';

interface TransactionFilterBarProps {
  filters: TransactionFilters;
  onFiltersChange: (filters: TransactionFilters) => void;
}

export function TransactionFilterBar({ filters, onFiltersChange }: TransactionFilterBarProps) {
  return (
    <Paper sx={{ p: 2 }}>
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
    </Paper>
  );
}
