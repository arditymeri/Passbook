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

export interface TransferAllocationRequest {
  fromCategoryId: string;
  toCategoryId: string;
  year: number;
  month: number;
  amount: number;
}

export interface TransferAllocationResponse {
  id: string;
  fromCategoryId: string;
  toCategoryId: string;
  amount: number;
  fromEnvelopeBalance: number;
  toEnvelopeBalance: number;
}

export interface RepeatAllocationsRequest {
  fromYear: number;
  fromMonth: number;
  toYear: number;
  toMonth: number;
}

export interface AllocationTopUp {
  categoryId: string;
  amountAdded: number;
  newMonthlyAmount: number;
}

export interface RepeatAllocationsResponse {
  applied: AllocationTopUp[];
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

export type TransactionTypeFilter = 'ALL' | 'BILL' | 'INCOME';

export interface TransactionFilters {
  searchText: string;
  categoryId?: string;
  source?: IncomeSource;
  accountId?: string;
  startDate?: string;
  endDate?: string;
  minAmount?: number;
  maxAmount?: number;
  type: TransactionTypeFilter;
}

export const EMPTY_TRANSACTION_FILTERS: TransactionFilters = {
  searchText: '',
  categoryId: undefined,
  source: undefined,
  accountId: undefined,
  startDate: undefined,
  endDate: undefined,
  minAmount: undefined,
  maxAmount: undefined,
  type: 'ALL',
};

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

export type RecurringFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type RecurringTransactionType = 'BILL' | 'INCOME';
export type RecurringSeriesStatusValue = 'PROPOSED' | 'CONFIRMED' | 'DISMISSED';

export interface RecurringSeries {
  id: string;
  transactionType: RecurringTransactionType;
  groupKey: string;
  description: string;
  frequency: RecurringFrequency;
  status: RecurringSeriesStatusValue;
  createdAt: string;
}

export interface UpcomingRecurringItem {
  seriesId: string;
  transactionType: RecurringTransactionType;
  groupKey: string;
  description: string;
  predictedDate: string;
  predictedAmount: number;
  overdue: boolean;
}

export interface PriceChangeAlert {
  transactionId: string;
  transactionType: RecurringTransactionType;
  groupKey: string;
  description: string;
  priorAmount: number;
  newAmount: number;
  delta: number;
}

export interface RecurringDashboard {
  upcoming: UpcomingRecurringItem[];
  recentPriceChanges: PriceChangeAlert[];
}

export type PaceStatusValue = 'ON_PACE' | 'BEHIND_PACE' | 'OVERDUE';

export interface SavingsGoalStatus {
  id: string;
  name: string;
  targetAmount: number;
  targetDate?: string;
  accountId: string;
  createdAt: string;
  savedAmount: number;
  percentComplete: number;
  remainingAmount: number;
  achieved: boolean;
  paceStatus?: PaceStatusValue;
}

export interface CreateSavingsGoalRequest {
  name: string;
  targetAmount: number;
  targetDate?: string;
  accountId: string;
}

export interface UpdateSavingsGoalRequest {
  name: string;
  targetAmount: number;
  targetDate?: string;
}

export interface DashboardData {
  summary: MonthlySummary | null;
  summaryLoading: boolean;
  summaryError: string | null;

  budgetEntries: BudgetStatusEntry[];
  budgetLoading: boolean;
  budgetError: string | null;

  transactions: Transaction[];
  allTransactions: Transaction[];
  transactionsLoading: boolean;
  transactionsError: string | null;

  categoryNames: CategoryNameMap;
  categories: Category[];
  categoriesLoading: boolean;

  accounts: Account[];
  accountsLoading: boolean;
}
