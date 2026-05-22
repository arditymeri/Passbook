import type { Bill, BudgetStatusEntry, Category, Income, MonthlySummary } from '../types';

async function request<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`);
  return res.json();
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

export async function fetchBills(): Promise<Bill[]> {
  const data = await request<{ bills: Bill[] }>('/api/v1/bills');
  return data.bills ?? [];
}

export async function fetchIncomes(): Promise<Income[]> {
  const data = await request<{ incomes: Income[] }>('/api/v1/incomes');
  return data.incomes ?? [];
}

export async function fetchCategories(): Promise<Category[]> {
  const data = await request<Category[]>('/api/v1/categories');
  return Array.isArray(data) ? data : [];
}
