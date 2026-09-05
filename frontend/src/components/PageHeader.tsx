import { ReactNode } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

interface PageHeaderProps {
  title: string;
  onBack: () => void;
  /** The page's action buttons, if it has any. Rendered to the right of the title. */
  children?: ReactNode;
}

/**
 * The back-arrow-and-title row every full-page view starts with.
 *
 * Extracted because all five pages had written it out identically and all five overflowed a phone
 * identically — Budgeting's three action buttons come to 437px, which fits a laptop and runs off a
 * 320px screen. Fixing that in five places would have left five places for it to come back.
 *
 * Two things make it fit, both additive — at widths where the row already fitted, nothing about it
 * renders differently:
 *
 * - `flexWrap` with a row gap, so buttons move onto a second line rather than off the screen.
 * - `minWidth: 0` on the title. A flex item will not shrink below its content by default, so a
 *   long heading pushes the row wider than the viewport instead of truncating — the same rule that
 *   makes one long merchant name widen an entire page.
 */
export function PageHeader({ title, onBack, children }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 1,
        mb: 2,
      }}
    >
      {/* 44px is the smallest target a finger hits reliably (FR-010, SC-005). */}
      <IconButton onClick={onBack} aria-label="Back" sx={{ minWidth: 44, minHeight: 44 }}>
        <ArrowBackIcon />
      </IconButton>
      <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1, minWidth: 0 }} noWrap>
        {title}
      </Typography>
      {children}
    </Box>
  );
}
