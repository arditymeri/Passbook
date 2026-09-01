import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { fetchAuthStatus } from '../api/client';
import { getToken, onSessionDied } from './authToken';
import { SetupPage } from '../components/SetupPage';
import { LoginPage } from '../components/LoginPage';

type Screen = 'loading' | 'setup' | 'login' | 'authenticated';

interface AuthGateProps {
  children: ReactNode;
}

/**
 * The top-level gate: nothing in {@code children} (the existing dashboard, including its own
 * data-fetching effects) ever renders before this decides the operator is authenticated (FR-009).
 * A stored token is trusted optimistically once an admin account exists — no extra round-trip to
 * "prove" it — and this stays subscribed to onSessionDied for its whole lifetime, so the first
 * rejected/expired-token response from any real API call anywhere in the app (feature 020's
 * shared api/client.ts wiring) drops back to the login screen immediately (US3), not just on
 * initial mount.
 */
export function AuthGate({ children }: AuthGateProps) {
  const [screen, setScreen] = useState<Screen>('loading');

  useEffect(() => {
    let cancelled = false;
    fetchAuthStatus()
      .then((status) => {
        if (cancelled) return;
        if (!status.adminAccountConfigured) {
          setScreen('setup');
        } else if (getToken()) {
          setScreen('authenticated');
        } else {
          setScreen('login');
        }
      })
      .catch(() => {
        if (!cancelled) setScreen('login');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => onSessionDied(() => setScreen('login')), []);

  if (screen === 'loading') {
    return (
      <Box sx={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (screen === 'setup') {
    return <SetupPage onAuthenticated={() => setScreen('authenticated')} />;
  }

  if (screen === 'login') {
    return <LoginPage onAuthenticated={() => setScreen('authenticated')} />;
  }

  return <>{children}</>;
}
