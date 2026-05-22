export interface Period {
  year: number;
  month: number;
}

export interface MonthlySummary {
  year: number;
  month: number;
  totalIncome: number;
  totalExpenses: number;
  netBalance: number;
  spendingByCategory: Record<string, number>;
}

export type BudgetStatusValue = 'UNDER_BUDGET' | 'OVER_BUDGET';

export interface BudgetStatusEntry {
  categoryId: string;
  budgeted: number;
  actual: number;
  remaining: number;
  status: BudgetStatusValue;
}

export interface Bill {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  categoryId: string | null;
}

export interface Income {
  id: string;
  description: string | null;
  amount: number;
  time: string;
}

export interface Category {
  id: string;
  name: string;
}

export interface Transaction {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  type: 'BILL' | 'INCOME';
}

export type CategoryNameMap = Map<string, string>;

export interface DashboardData {
  summary: MonthlySummary | null;
  summaryLoading: boolean;
  summaryError: string | null;

  budgetEntries: BudgetStatusEntry[];
  budgetLoading: boolean;
  budgetError: string | null;

  transactions: Transaction[];
  transactionsLoading: boolean;
  transactionsError: string | null;

  categoryNames: CategoryNameMap;
  categoriesLoading: boolean;
}
