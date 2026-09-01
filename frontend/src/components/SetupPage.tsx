import { useState } from 'react';
import type * as React from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { setupAdminAccount } from '../api/client';
import { setToken } from '../auth/authToken';

interface SetupPageProps {
  onAuthenticated: () => void;
}

export function SetupPage({ onAuthenticated }: SetupPageProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!username || !password) return;
    setSubmitting(true);
    setError(null);
    try {
      const session = await setupAdminAccount({ username, password });
      setToken(session.token);
      onAuthenticated();
    } catch {
      setError('Could not create the admin account — please try again');
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', p: 2 }}>
      <Paper sx={{ p: 4, width: '100%', maxWidth: 400 }}>
        <Stack spacing={2} component="form" onSubmit={handleSubmit}>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>Welcome to Passbook</Typography>
          <Typography color="text.secondary">
            This is a fresh instance. Choose an admin username and password to protect it —
            this is the only account this instance will ever have.
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            fullWidth
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
          />
          <Button type="submit" variant="contained" disabled={submitting || !username || !password}>
            {submitting ? 'Setting up…' : 'Create Admin Account'}
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
