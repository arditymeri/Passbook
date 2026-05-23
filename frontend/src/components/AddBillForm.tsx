import { useState } from 'react';
import { createBill } from '../api/client';
import type { Category, CreateBillRequest } from '../types';
import { Modal } from './Modal';

interface AddBillFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  categories: Category[];
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function AddBillForm({ open, onClose, onSuccess, categories }: AddBillFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(today());
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setAmount('');
    setDate(today());
    setDescription('');
    setCategoryId('');
    setError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = parseFloat(amount);
    if (!amount || isNaN(parsed) || parsed <= 0) {
      setError('Amount must be greater than zero');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const req: CreateBillRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        categoryId: categoryId || undefined,
      };
      await createBill(req);
      reset();
      onSuccess();
      onClose();
    } catch {
      setError('Could not save — please try again');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Add Expense">
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label htmlFor="bill-amount">Amount *</label>
          <input
            id="bill-amount"
            type="number"
            step="0.01"
            min="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
          />
          {error && <span className="form-error">{error}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="bill-date">Date *</label>
          <input
            id="bill-date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="bill-desc">Description</label>
          <input
            id="bill-desc"
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional"
          />
        </div>

        <div className="form-group">
          <label htmlFor="bill-category">Category</label>
          <select
            id="bill-category"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">No category</option>
            {categories.map((cat) => (
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
