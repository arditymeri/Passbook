import { useState } from 'react';
import { createCategory } from '../api/client';
import type { Category, CategoryType, CreateCategoryRequest } from '../types';
import { Modal } from './Modal';

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
        {serverError && <div className="form-server-error">{serverError}</div>}

        <div className="form-group">
          <label htmlFor="cat-name">Name *</label>
          <input
            id="cat-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Groceries"
          />
          {nameError && <span className="form-error">{nameError}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="cat-type">Type *</label>
          <select
            id="cat-type"
            value={type}
            onChange={(e) => setType(e.target.value as CategoryType | '')}
          >
            <option value="">Select a type</option>
            {CATEGORY_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
          {typeError && <span className="form-error">{typeError}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="cat-color">Color</label>
          <input
            id="cat-color"
            type="color"
            value={color}
            onChange={(e) => { setColor(e.target.value); setColorTouched(true); }}
          />
        </div>

        <div className="form-group">
          <label htmlFor="cat-parent">Parent Category</label>
          <select
            id="cat-parent"
            value={parentCategoryId}
            onChange={(e) => setParentCategoryId(e.target.value)}
          >
            <option value="">None</option>
            {existingCategories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>

        <div className="form-actions">
          <button type="button" className="btn-secondary" onClick={handleClose}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
