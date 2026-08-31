import type { Account, Allocation, Bill, BudgetStatusEntry, BudgetStatusResponse, CashFlowForecastResponse, CashFlowWindowWeeks, Category, CorrectBillRequest, CorrectIncomeRequest, CreateAccountRequest, CreateAllocationRequest, CreateBillRequest, CreateCategoryRequest, CreateIncomeRequest, CreateSavingsGoalRequest, Income, MonthlySummary, NecessityTag, RecurringCostSummaryItem, RecurringDashboard, RecurringSeries, RepeatAllocationsRequest, RepeatAllocationsResponse, SavingsGoalStatus, TransactionHistoryEntry, TransferAllocationRequest, TransferAllocationResponse, UpdateSavingsGoalRequest } from '../types';

async function request<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function post(url: string, body: unknown): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
}

async function postAndReturn<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function putAndReturn<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function del(url: string): Promise<void> {
  const res = await fetch(url, { method: 'DELETE' });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
}

export async function fetchMonthlySummary(year: number, month: number): Promise<MonthlySummary> {
  const data = await request<{ summary: MonthlySummary }>(
    `/api/v1/analysis/monthly?year=${year}&month=${month}`
  );
  return data.summary;
}

export async function fetchBudgetStatus(year: number, month: number): Promise<BudgetStatusEntry[]> {
  const data = await request<{ entries: BudgetStatusEntry[] }>(
    `/api/v1/budgets/status?year=${year}&month=${month}`
  );
  return data.entries ?? [];
}

export async function fetchBudgetStatusResponse(year: number, month: number): Promise<BudgetStatusResponse> {
  return request<BudgetStatusResponse>(`/api/v1/budgets/status?year=${year}&month=${month}`);
}

export async function fetchBudgets(year: number, month: number): Promise<Allocation[]> {
  const data = await request<{ budgets: Allocation[] }>(
    `/api/v1/budgets?year=${year}&month=${month}`
  );
  return data.budgets ?? [];
}

export async function createOrUpdateBudget(req: CreateAllocationRequest): Promise<Allocation> {
  return postAndReturn<Allocation>('/api/v1/budgets', req);
}

export async function moveAllocation(req: TransferAllocationRequest): Promise<TransferAllocationResponse> {
  return postAndReturn<TransferAllocationResponse>('/api/v1/budgets/transfer', req);
}

export async function repeatAllocations(req: RepeatAllocationsRequest): Promise<RepeatAllocationsResponse> {
  return postAndReturn<RepeatAllocationsResponse>('/api/v1/budgets/repeat', req);
}

export async function fetchRecurringSeries(): Promise<RecurringSeries[]> {
  const data = await request<{ series: RecurringSeries[] }>('/api/v1/recurring-series');
  return data.series ?? [];
}

export async function detectRecurringSeries(): Promise<RecurringSeries[]> {
  const data = await postAndReturn<{ series: RecurringSeries[] }>('/api/v1/recurring-series/detect', {});
  return data.series ?? [];
}

export async function confirmRecurringSeries(id: string): Promise<RecurringSeries> {
  return postAndReturn<RecurringSeries>(`/api/v1/recurring-series/${id}/confirm`, {});
}

export async function dismissRecurringSeries(id: string): Promise<RecurringSeries> {
  return postAndReturn<RecurringSeries>(`/api/v1/recurring-series/${id}/dismiss`, {});
}

export async function fetchRecurringDashboard(): Promise<RecurringDashboard> {
  return request<RecurringDashboard>('/api/v1/recurring-series/dashboard');
}

export async function fetchRecurringCostSummary(): Promise<RecurringCostSummaryItem[]> {
  const data = await request<{ items: RecurringCostSummaryItem[] }>('/api/v1/recurring-series/cost-summary');
  return data.items ?? [];
}

export async function createSavingsGoal(req: CreateSavingsGoalRequest): Promise<SavingsGoalStatus> {
  return postAndReturn<SavingsGoalStatus>('/api/v1/savings-goals', req);
}

export async function fetchSavingsGoals(): Promise<SavingsGoalStatus[]> {
  const data = await request<{ goals: SavingsGoalStatus[] }>('/api/v1/savings-goals');
  return data.goals ?? [];
}

export async function updateSavingsGoal(id: string, req: UpdateSavingsGoalRequest): Promise<SavingsGoalStatus> {
  return putAndReturn<SavingsGoalStatus>(`/api/v1/savings-goals/${id}`, req);
}

export async function deleteSavingsGoal(id: string): Promise<void> {
  await del(`/api/v1/savings-goals/${id}`);
}

export async function fetchBills(): Promise<Bill[]> {
  const data = await request<{ bills: Bill[] }>('/api/v1/bills');
  return data.bills ?? [];
}

export async function fetchIncomes(): Promise<Income[]> {
  const data = await request<{ incomes: Income[] }>('/api/v1/incomes');
  return data.incomes ?? [];
}

export async function fetchCategories(): Promise<Category[]> {
  const data = await request<{ categories: Category[] }>('/api/v1/categories');
  return data.categories ?? [];
}

export async function createCategory(req: CreateCategoryRequest): Promise<Category> {
  return postAndReturn<Category>('/api/v1/categories', req);
}

export async function createCategoryIfMissing(req: CreateCategoryRequest): Promise<'created' | 'skipped'> {
  const res = await fetch('/api/v1/categories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (res.status === 409) return 'skipped';
  if (!res.ok) throw new Error(`HTTP ${res.status}: /api/v1/categories`);
  return 'created';
}

export async function createBill(req: CreateBillRequest): Promise<void> {
  await post('/api/v1/createBill', req);
}

export async function createIncome(req: CreateIncomeRequest): Promise<void> {
  await post('/api/v1/incomes', req);
}

export async function fetchAccounts(): Promise<Account[]> {
  const data = await request<{ accounts: Account[] }>('/api/v1/accounts');
  return data.accounts ?? [];
}

export async function createAccount(req: CreateAccountRequest): Promise<Account> {
  return postAndReturn<Account>('/api/v1/accounts', req);
}

export async function createAccountIfMissing(req: CreateAccountRequest): Promise<'created' | 'skipped'> {
  const res = await fetch('/api/v1/accounts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (res.status === 409) return 'skipped';
  if (!res.ok) throw new Error(`HTTP ${res.status}: /api/v1/accounts`);
  return 'created';
}

export async function correctBill(id: string, req: CorrectBillRequest): Promise<void> {
  await putAndReturn<unknown>(`/api/v1/bills/${id}`, req);
}

export async function removeBill(id: string): Promise<void> {
  await del(`/api/v1/bills/${id}`);
}

export async function fetchBillHistory(id: string): Promise<TransactionHistoryEntry[]> {
  const data = await request<{ history: TransactionHistoryEntry[] }>(`/api/v1/bills/${id}/history`);
  return data.history ?? [];
}

export async function updateBillNecessityTag(id: string, tag: NecessityTag | null): Promise<Bill> {
  const data = await putAndReturn<{ bill: Bill }>(`/api/v1/bills/${id}/necessity-tag`, { tag });
  return data.bill;
}

export async function correctIncome(id: string, req: CorrectIncomeRequest): Promise<void> {
  await putAndReturn<unknown>(`/api/v1/incomes/${id}`, req);
}

export async function removeIncome(id: string): Promise<void> {
  await del(`/api/v1/incomes/${id}`);
}

export async function fetchIncomeHistory(id: string): Promise<TransactionHistoryEntry[]> {
  const data = await request<{ history: TransactionHistoryEntry[] }>(`/api/v1/incomes/${id}/history`);
  return data.history ?? [];
}

export async function fetchCashFlowForecast(weeks?: CashFlowWindowWeeks): Promise<CashFlowForecastResponse> {
  const query = weeks !== undefined ? `?weeks=${weeks}` : '';
  return request<CashFlowForecastResponse>(`/api/v1/cash-flow-forecast${query}`);
}
