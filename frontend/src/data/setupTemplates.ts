import type { SetupTemplate } from '../types';

export const SETUP_TEMPLATES: SetupTemplate[] = [
  {
    id: 'personal-finance-starter',
    name: 'Personal Finance Starter',
    description: 'A well-rounded set of everyday categories and accounts to get started quickly.',
    categoryItems: [
      { name: 'Groceries', type: 'EXPENSE' },
      { name: 'Rent', type: 'EXPENSE' },
      { name: 'Utilities', type: 'EXPENSE' },
      { name: 'Entertainment', type: 'EXPENSE' },
      { name: 'Transportation', type: 'EXPENSE' },
      { name: 'Salary', type: 'INCOME' },
    ],
    accountItems: [
      { name: 'Checking', type: 'CHECKING' },
      { name: 'Savings', type: 'SAVINGS' },
      { name: 'Credit Card', type: 'CREDIT_CARD' },
    ],
  },
];
