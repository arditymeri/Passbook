import { useEffect, useState } from 'react';
import { Modal } from './Modal';
import { SETUP_TEMPLATES } from '../data/setupTemplates';
import { allKeysFor, applySetupTemplate } from '../utils/applySetupTemplate';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';

interface SetupTemplateDialogProps {
  open: boolean;
  onClose: () => void;
  onApplied: () => void;
}

export function SetupTemplateDialog({ open, onClose, onApplied }: SetupTemplateDialogProps) {
  const template = SETUP_TEMPLATES[0];
  const [applying, setApplying] = useState(false);
  const [applied, setApplied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setApplying(false);
      setApplied(false);
      setError(null);
    }
  }, [open]);

  function handleClose() {
    if (applied) onApplied();
    onClose();
  }

  async function handleApply() {
    setApplying(true);
    setError(null);
    try {
      await applySetupTemplate(template, allKeysFor(template));
      setApplied(true);
    } catch {
      setError('Could not apply the template — please try again');
    } finally {
      setApplying(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title={template.name}>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        {applied ? (
          <>
            <Alert severity="success">Template applied.</Alert>
            <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
              <Button variant="contained" onClick={handleClose}>Done</Button>
            </Stack>
          </>
        ) : (
          <>
            <Typography color="text.secondary">{template.description}</Typography>

            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Categories</Typography>
            <List disablePadding dense>
              {template.categoryItems.map((item) => (
                <ListItem key={item.name} disablePadding>
                  <ListItemText primary={item.name} secondary={item.type} />
                </ListItem>
              ))}
            </List>

            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>Accounts</Typography>
            <List disablePadding dense>
              {template.accountItems.map((item) => (
                <ListItem key={item.name} disablePadding>
                  <ListItemText primary={item.name} secondary={item.type} />
                </ListItem>
              ))}
            </List>

            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
              <Button variant="outlined" onClick={handleClose}>Cancel</Button>
              <Button variant="contained" onClick={handleApply} disabled={applying}>
                {applying ? 'Applying…' : 'Apply'}
              </Button>
            </Stack>
          </>
        )}
      </Stack>
    </Modal>
  );
}
