import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';

interface MonthNavProps {
  year: number;
  month: number;
  onPrevious: () => void;
  onNext: () => void;
}

export function MonthNav({ year, month, onPrevious, onNext }: MonthNavProps) {
  const label = new Date(year, month - 1).toLocaleString('default', {
    month: 'long',
    year: 'numeric',
  });

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'center' }}>
        <IconButton onClick={onPrevious} aria-label="Previous month" size="small"
          sx={{ minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}>
          <ChevronLeftIcon />
        </IconButton>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          {label}
        </Typography>
        <IconButton onClick={onNext} aria-label="Next month" size="small"
          sx={{ minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}>
          <ChevronRightIcon />
        </IconButton>
      </Stack>
    </Paper>
  );
}
