const TOKEN_STORAGE_KEY = 'passbook.authToken';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

// A minimal cross-module signal for "the current session just died" (expired, rejected, or
// explicitly logged out) — api/client.ts dispatches this on any 401, and auth/AuthGate.tsx
// listens for it so it can drop back to the login screen from anywhere in the app, not just on
// its own initial mount. One small EventTarget rather than a new state-management dependency.
const sessionEvents = new EventTarget();
const SESSION_DIED_EVENT = 'session-died';

export function sessionDied(): void {
  sessionEvents.dispatchEvent(new Event(SESSION_DIED_EVENT));
}

export function onSessionDied(listener: () => void): () => void {
  sessionEvents.addEventListener(SESSION_DIED_EVENT, listener);
  return () => sessionEvents.removeEventListener(SESSION_DIED_EVENT, listener);
}
