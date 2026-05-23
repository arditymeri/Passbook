import { useState } from 'react';
import { createIncome } from '../api/client';
import type { CreateIncomeRequest, IncomeSource } from '../types';
import { Modal } from './Modal';

interface AddIncomeFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const INCOME_SOURCES: IncomeSource[] = ['SALARY', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'GIFT', 'OTHER'];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function AddIncomeForm({ open, onClose, onSuccess }: AddIncomeFormProps) {
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(today());
  const [description, setDescription] = useState('');
  const [source, setSource] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setAmount('');
    setDate(today());
    setDescription('');
    setSource('');
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
      const req: CreateIncomeRequest = {
        amount: parsed,
        time: new Date(date).toISOString(),
        description: description.trim() || undefined,
        source: (source as IncomeSource) || undefined,
      };
      await createIncome(req);
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
    <Modal open={open} onClose={handleClose} title="Add Income">
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label htmlFor="income-amount">Amount *</label>
          <input
            id="income-amount"
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
          <label htmlFor="income-date">Date *</label>
          <input
            id="income-date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="income-desc">Description</label>
          <input
            id="income-desc"
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional"
          />
        </div>

        <div className="form-group">
          <label htmlFor="income-source">Source</label>
          <select
            id="income-source"
            value={source}
            onChange={(e) => setSource(e.target.value)}
          >
            <option value="">No source</option>
            {INCOME_SOURCES.map((s) => (
              <option key={s} value={s}>{s}</option>
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
