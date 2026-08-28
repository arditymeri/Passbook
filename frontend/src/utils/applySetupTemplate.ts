import { createAccountIfMissing, createCategoryIfMissing } from '../api/client';
import type { ApplyTemplateResult, SetupTemplate } from '../types';

const DEFAULT_CURRENCY = 'EUR';

export function categoryItemKey(name: string): string {
  return `category:${name}`;
}

export function accountItemKey(name: string): string {
  return `account:${name}`;
}

export function allKeysFor(template: SetupTemplate): Set<string> {
  return new Set([
    ...template.categoryItems.map((item) => categoryItemKey(item.name)),
    ...template.accountItems.map((item) => accountItemKey(item.name)),
  ]);
}

export async function applySetupTemplate(template: SetupTemplate, selectedKeys: Set<string>): Promise<ApplyTemplateResult> {
  const created: string[] = [];
  const skipped: string[] = [];

  for (const item of template.categoryItems) {
    if (!selectedKeys.has(categoryItemKey(item.name))) continue;
    const outcome = await createCategoryIfMissing({ name: item.name, type: item.type });
    (outcome === 'created' ? created : skipped).push(item.name);
  }

  for (const item of template.accountItems) {
    if (!selectedKeys.has(accountItemKey(item.name))) continue;
    const outcome = await createAccountIfMissing({
      name: item.name,
      type: item.type,
      currencies: [DEFAULT_CURRENCY],
      defaultCurrency: DEFAULT_CURRENCY,
    });
    (outcome === 'created' ? created : skipped).push(item.name);
  }

  return { created, skipped };
}
