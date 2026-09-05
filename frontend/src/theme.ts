import { createTheme } from '@mui/material/styles';

/**
 * No `breakpoints` key here, deliberately: MUI's defaults are what the layout is built on, and
 * restating them would create a second place to change them.
 *
 * If you are here looking for the small-screen boundary, it is `sm` (600px) and the app asks about
 * it in exactly one way — `useIsPhone()` in `src/hooks/useIsPhone.ts`. The rules that boundary has
 * to obey are written down in `specs/025-mobile-layout/contracts/layout-contract.md`. Picking a
 * number here instead is how the two layouts drift apart and leave a width with neither.
 */

export const theme = createTheme({
  palette: {
    primary: { main: '#1a1a2e' },
    secondary: { main: '#3f8efc' },
    background: { default: '#f5f7fa' },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: 'Roboto, system-ui, -apple-system, sans-serif',
  },
});
