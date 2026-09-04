import type { Account, Allocation, AuthStatus, Bill, BudgetStatusEntry, BudgetStatusResponse, CashFlowForecastResponse, CashFlowWindowWeeks, Category, ChangePasswordRequest, CorrectBillRequest, CorrectIncomeRequest, CreateAccountRequest, CreateAllocationRequest, CreateBillRequest, CreateCategoryRequest, CreateIncomeRequest, CreateSavingsGoalRequest, ImportSummary, Income, LoginRequest, MonthlySummary, NecessityTag, PostingRunResult, RecurringCostSummaryItem, RecurringDashboard, RecurringSeries, RecurringSeriesStatusValue, RepeatAllocationsRequest, RepeatAllocationsResponse, SavingsGoalStatus, Session, SetupRequest, StatementIngestionResult, StatementPreview, SyncSnapshot, SystemVersion, TransactionHistoryEntry, TransferAllocationRequest, TransferAllocationResponse, UpdateSavingsGoalRequest } from '../types';
import { clearToken, getToken, sessionDied } from '../auth/authToken';

function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// Every existing data endpoint now requires a valid session (feature 020) — these helpers attach
// it, and a 401 here always means the session itself died (expired, rejected, or logged out
// elsewhere), never a business-logic rejection, so it's safe to always clear the token and
// notify the rest of the app. The three /auth/* endpoints below are called separately, without
// this handling: /auth/status, /auth/setup, and /auth/login are public (no token to attach, and
// a 401 from login just means "wrong credentials", not "session died").
function handlePossibleSessionDeath(res: Response): void {
  if (res.status === 401) {
    clearToken();
    sessionDied();
  }
}

async function request<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: authHeaders() });
  handlePossibleSessionDeath(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function post(url: string, body: unknown): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  handlePossibleSessionDeath(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
}

async function postAndReturn<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  handlePossibleSessionDeath(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function putAndReturn<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  handlePossibleSessionDeath(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
}

async function del(url: string): Promise<void> {
  const res = await fetch(url, { method: 'DELETE', headers: authHeaders() });
  handlePossibleSessionDeath(res);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
}

export async function fetchAuthStatus(): Promise<AuthStatus> {
  const res = await fetch('/api/v1/auth/status');
  if (!res.ok) throw new Error(`HTTP ${res.status}: /auth/status`);
  return res.json();
}

export async function setupAdminAccount(req: SetupRequest): Promise<Session> {
  const res = await fetch('/api/v1/auth/setup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: /auth/setup`);
  return res.json();
}

export async function login(req: LoginRequest): Promise<Session> {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: /auth/login`);
  return res.json();
}

// Deliberately not routed through post()/handlePossibleSessionDeath: a 401 here can mean either
// "your session already died" or (for changePasswordRequest specifically) "wrong current
// password" — the backend can't distinguish these in the status code, so the caller decides how
// to present it instead of this layer guessing.
export async function logoutRequest(): Promise<void> {
  await fetch('/api/v1/auth/logout', { method: 'POST', headers: authHeaders() });
}

export async function changePasswordRequest(req: ChangePasswordRequest): Promise<void> {
  const res = await fetch('/api/v1/auth/change-password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: /auth/change-password`);
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

/**
 * Ends a confirmed series. Deliberately separate from dismissing a proposal: dismissing says the
 * detection was wrong, stopping says the thing was real and has now finished. Already-posted
 * transactions are left alone.
 */
export async function stopRecurringSeries(id: string): Promise<{ id: string; status: RecurringSeriesStatusValue }> {
  return postAndReturn<{ id: string; status: RecurringSeriesStatusValue }>(
    `/api/v1/recurring-series/${id}/stop`, {});
}

/** Runs the same work the daily schedule does, immediately. Safe to call any number of times. */
export async function postDueOccurrences(): Promise<PostingRunResult> {
  return postAndReturn<PostingRunResult>('/api/v1/recurring-series/post-due', {});
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

export async function fetchSyncExport(): Promise<SyncSnapshot> {
  return request<SyncSnapshot>('/api/v1/sync/export');
}

export async function previewSyncImport(snapshot: SyncSnapshot): Promise<ImportSummary> {
  return postAndReturn<ImportSummary>('/api/v1/sync/import/preview', snapshot);
}

export async function applySyncImport(snapshot: SyncSnapshot): Promise<ImportSummary> {
  return postAndReturn<ImportSummary>('/api/v1/sync/import/apply', snapshot);
}

export function fetchSystemVersion(): Promise<SystemVersion> {
  return request<SystemVersion>('/api/v1/system/version');
}

/**
 * Statement import (feature 022). Both calls send the file: the commit re-uploads it rather than
 * posting back the previewed rows, so the server derives every identity itself and a client can
 * never introduce a row carrying an identity no re-parse would reproduce.
 *
 * Content-Type is deliberately not set — the browser must add the multipart boundary itself.
 */
function statementFormData(file: File, accountId: string, excludedRowIndexes?: number[]): FormData {
  const form = new FormData();
  form.append('file', file);
  form.append('accountId', accountId);
  if (excludedRowIndexes && excludedRowIndexes.length > 0) {
    // The endpoint takes this as a JSON part rather than repeated form fields.
    form.append('excludedRowIndexes',
      new Blob([JSON.stringify(excludedRowIndexes)], { type: 'application/json' }));
  }
  return form;
}

async function postStatement<T>(url: string, form: FormData): Promise<T> {
  const res = await fetch(url, { method: 'POST', headers: authHeaders(), body: form });
  handlePossibleSessionDeath(res);
  if (!res.ok) {
    // A file that is not a readable statement comes back as 400 with an explanation; surface it
    // rather than a bare status code, since it is the operator's own file that is wrong.
    throw new Error((await res.text()) || `HTTP ${res.status}: ${url}`);
  }
  return res.json();
}

export function previewStatement(file: File, accountId: string): Promise<StatementPreview> {
  return postStatement<StatementPreview>('/api/v1/statements/preview',
    statementFormData(file, accountId));
}

export function ingestStatement(file: File, accountId: string,
                                excludedRowIndexes: number[]): Promise<StatementIngestionResult> {
  return postStatement<StatementIngestionResult>('/api/v1/statements/ingest',
    statementFormData(file, accountId, excludedRowIndexes));
}
