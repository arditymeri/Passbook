import { useState } from 'react';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { Modal } from './Modal';
import { changePasswordRequest } from '../api/client';
import { clearToken, sessionDied } from '../auth/authToken';

interface ChangePasswordDialogProps {
  open: boolean;
  onClose: () => void;
}

export function ChangePasswordDialog({ open, onClose }: ChangePasswordDialogProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setCurrentPassword('');
    setNewPassword('');
    setSubmitting(false);
    setError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit() {
    if (!currentPassword || !newPassword) return;
    setSubmitting(true);
    setError(null);
    try {
      await changePasswordRequest({ currentPassword, newPassword });
      // A successful change invalidates every session, including this one (research.md R2) — go
      // straight back to the login screen to log in with the new password.
      clearToken();
      sessionDied();
    } catch {
      setError('Current password is incorrect');
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Change Password">
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField
          label="Current Password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          fullWidth
          autoFocus
        />
        <TextField
          label="New Password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          fullWidth
        />
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          <Button variant="outlined" onClick={handleClose} disabled={submitting}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={submitting || !currentPassword || !newPassword}
          >
            {submitting ? 'Changing…' : 'Change Password'}
          </Button>
        </Stack>
      </Stack>
    </Modal>
  );
}
