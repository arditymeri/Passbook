import { useState } from 'react';
import { createCategory } from '../api/client';
import type { Category, CategoryType, CreateCategoryRequest } from '../types';
import { Modal } from './Modal';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';

interface AddCategoryFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (created: Category) => void;
  existingCategories: Category[];
}

const DEFAULT_COLOR = '#ffffff';
const CATEGORY_TYPES: CategoryType[] = ['EXPENSE', 'INCOME', 'BOTH'];

export function AddCategoryForm({ open, onClose, onSuccess, existingCategories }: AddCategoryFormProps) {
  const [name, setName] = useState('');
  const [type, setType] = useState<CategoryType | ''>('');
  const [color, setColor] = useState(DEFAULT_COLOR);
  const [colorTouched, setColorTouched] = useState(false);
  const [parentCategoryId, setParentCategoryId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const [typeError, setTypeError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  function reset() {
    setName('');
    setType('');
    setColor(DEFAULT_COLOR);
    setColorTouched(false);
    setParentCategoryId('');
    setSubmitting(false);
    setNameError(null);
    setTypeError(null);
    setServerError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    let valid = true;
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('Name is required');
      valid = false;
    } else {
      setNameError(null);
    }
    if (!type) {
      setTypeError('Type is required');
      valid = false;
    } else {
      setTypeError(null);
    }
    if (!valid) return;

    setServerError(null);
    setSubmitting(true);
    try {
      const req: CreateCategoryRequest = {
        name: trimmedName,
        type: type as CategoryType,
        ...(colorTouched && { color }),
        ...(parentCategoryId && { parentCategoryId }),
      };
      const created = await createCategory(req);
      reset();
      onSuccess(created);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('409')) {
        setServerError('A category with this name already exists');
      } else {
        setServerError('Could not save — please try again');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add Category">
      <form onSubmit={handleSubmit} noValidate>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {serverError && <Alert severity="error">{serverError}</Alert>}
          <TextField
            label="Name *"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Groceries"
            error={!!nameError}
            helperText={nameError || ' '}
            fullWidth
          />
          <FormControl fullWidth error={!!typeError}>
            <InputLabel id="cat-type-label">Type *</InputLabel>
            <Select
              labelId="cat-type-label"
              value={type}
              label="Type *"
              onChange={(e) => setType(e.target.value as CategoryType | '')}
            >
              <MenuItem value="">Select a type</MenuItem>
              {CATEGORY_TYPES.map((t) => (
                <MenuItem key={t} value={t}>{t}</MenuItem>
              ))}
            </Select>
            {typeError && <FormHelperText>{typeError}</FormHelperText>}
          </FormControl>
          <TextField
            label="Color"
            type="color"
            value={color}
            onChange={(e) => { setColor(e.target.value); setColorTouched(true); }}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel id="cat-parent-label">Parent Category</InputLabel>
            <Select
              labelId="cat-parent-label"
              value={parentCategoryId}
              label="Parent Category"
              onChange={(e) => setParentCategoryId(e.target.value)}
            >
              <MenuItem value="">None</MenuItem>
              {existingCategories.map((cat) => (
                <MenuItem key={cat.id} value={cat.id}>{cat.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button variant="outlined" onClick={handleClose}>Cancel</Button>
            <Button variant="contained" type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save'}
            </Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
