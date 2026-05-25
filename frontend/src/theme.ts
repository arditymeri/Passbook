import { createTheme } from '@mui/material/styles';

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
