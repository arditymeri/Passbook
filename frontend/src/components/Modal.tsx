import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import { useIsPhone } from '../hooks/useIsPhone';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

/**
 * The shared form dialog. Fourteen of the app's seventeen dialogs are this component, so the one
 * change below is nearly the whole of US3.
 *
 * FULL SCREEN ON A PHONE, BOXED EVERYWHERE ELSE. A form in a `maxWidth="sm"` box wastes margins on
 * a screen that has none to spare, and MUI's `fullScreen` also makes the content area scroll
 * within the dialog rather than the dialog growing past the viewport — so a long form's save
 * button stays reachable, including in landscape where the screen is short rather than narrow
 * (SC-007). Above 600px the dialog is exactly what it was.
 *
 * FORMS, NOT CONFIRMATIONS. `RemoveConfirmDialog` and the savings-goal delete confirmation
 * deliberately do NOT use this component and are deliberately not full-screen: expanding a
 * two-button question to fill the screen hides the thing being confirmed, which is the one piece
 * of context that makes the answer meaningful (research R4, quickstart scenario 7). If a future
 * confirmation is switched to this component to save a file, that reasoning is what is being
 * discarded.
 *
 * The close button was already here and matters more at full screen than it did in a box: a
 * boxed dialog can be dismissed by clicking beside it, and a full-screen one has no beside
 * (FR-009).
 */
export function Modal({ open, onClose, title, children }: ModalProps) {
  const isPhone = useIsPhone();

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth fullScreen={isPhone}>
      <DialogTitle sx={{ pr: 6 }}>
        {title}
        <IconButton
          aria-label="close"
          onClick={onClose}
          // 44px is the smallest target a finger hits reliably (FR-010, SC-005), and this is the
          // only way out of a full-screen dialog.
          sx={{ position: 'absolute', right: 8, top: 8, minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}
        >
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        {children}
      </DialogContent>
    </Dialog>
  );
}
