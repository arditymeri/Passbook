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
  envelopeBalance: number;
}

export interface BudgetStatusResponse {
  year: number;
  month: number;
  entries: BudgetStatusEntry[];
  unallocated: number;
}

export interface Allocation {
  id: string;
  categoryId: string;
  year: number;
  month: number;
  limitAmount: number;
}

export interface CreateAllocationRequest {
  categoryId: string;
  year: number;
  month: number;
  limitAmount: number;
}

export interface Bill {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  categoryId: string | null;
  accountId?: string | null;
  correctsTransactionId?: string | null;
  reversal?: boolean;
}

export interface Income {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  source?: IncomeSource | null;
  accountId?: string | null;
  correctsTransactionId?: string | null;
  reversal?: boolean;
}

export type CategoryType = 'EXPENSE' | 'INCOME' | 'BOTH';

export interface Category {
  id: string;
  name: string;
  type: CategoryType;
  color?: string;
  parentCategoryId?: string;
}

export interface CreateCategoryRequest {
  name: string;
  type: CategoryType;
  color?: string;
  parentCategoryId?: string;
}

export interface Transaction {
  id: string;
  description: string | null;
  amount: number;
  time: string;
  type: 'BILL' | 'INCOME';
  categoryId?: string;
  accountId?: string;
  source?: IncomeSource;
  correctsTransactionId?: string;
}

export type CategoryNameMap = Map<string, string>;

export type IncomeSource = 'SALARY' | 'FREELANCE' | 'INVESTMENT' | 'RENTAL' | 'GIFT' | 'OTHER';

export interface CreateBillRequest {
  amount: number;
  time: string;
  description?: string;
  categoryId?: string;
  accountId?: string;
}

export interface CreateIncomeRequest {
  amount: number;
  time: string;
  description?: string;
  source?: IncomeSource;
  accountId?: string;
}

export interface CorrectBillRequest {
  /** Decimal string, never a JS number — Constitution Principle IV forbids
   *  floating-point for money at any layer. */
  amount: string;
  time: string;
  description?: string;
  categoryId?: string;
  accountId?: string;
}

export interface CorrectIncomeRequest {
  /** Decimal string, never a JS number — Constitution Principle IV forbids
   *  floating-point for money at any layer. */
  amount: string;
  time: string;
  description?: string;
  source?: IncomeSource;
  accountId?: string;
}

export interface TransactionHistoryEntry {
  id: string;
  amount: number;
  time: string;
  description: string | null;
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'CASH' | 'INVESTMENT';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currencies: string[];
  defaultCurrency: string;
  balance: number;
  institution?: string;
}

export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  currencies: string[];
  defaultCurrency: string;
  balance?: number;
  institution?: string;
}

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
  categories: Category[];
  categoriesLoading: boolean;

  accounts: Account[];
  accountsLoading: boolean;
}
