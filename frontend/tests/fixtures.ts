import { Page } from '@playwright/test';

/**
 * A stubbed backend, good enough to render every screen with content.
 *
 * Shared rather than inlined so a diagnostic — or a future check — measures the same app the
 * layout check does.
 *
 * TWO THINGS THIS FILE LEARNED THE HARD WAY, both of which silently produce a green layout check
 * that is looking at a blank page:
 *
 * 1. THE ENVELOPES MATTER. Nearly every endpoint wraps its payload in a single-key object
 *    (`{ bills: [...] }`, `{ categories: [...] }`, `{ summary: {...} }`). Returning the bare array
 *    or the bare object is not "close enough": api/client.ts reads the key, gets undefined, and a
 *    component downstream reads .length off it and takes the whole React tree down. The page then
 *    renders nothing at all — and nothing is very good at not scrolling sideways.
 *
 * 2. THE DATES MUST BE IN THE CURRENT MONTH. useDashboardData filters the transaction list to the
 *    selected month, which starts as today's. Hard-coded dates make the list empty the moment the
 *    month rolls over, which again hides the component this feature is mostly about.
 *
 * If this check ever goes green suspiciously easily, assert something is on the page before
 * believing it — see `expectAppRendered` in layout.spec.ts, which exists for exactly that reason.
 */
const ACCOUNT_ID = '11111111-1111-1111-1111-111111111111';
const HOUSING_ID = '22222222-2222-2222-2222-222222222222';
const GROCERIES_ID = '33333333-3333-3333-3333-333333333333';

/** A date in the month the dashboard opens on, so the transaction list is never empty. */
function thisMonth(day: number): string {
  const now = new Date();
  return new Date(Date.UTC(now.getFullYear(), now.getMonth(), day, 12)).toISOString();
}

export async function stubApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url()).pathname;

    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

    // Ordered longest-path-first where one path is a prefix of another.
    if (url.endsWith('/auth/status')) return json({ adminAccountConfigured: true });
    if (url.includes('/auth/')) {
      return json({ token: 'stub-token', expiresAt: '2099-01-01T00:00:00Z' });
    }

    if (url.includes('/accounts')) {
      return json({
        accounts: [{
          id: ACCOUNT_ID, name: 'Current Account', type: 'CHECKING',
          currencies: ['EUR'], defaultCurrency: 'EUR', balance: 2345.67,
          institution: 'Erste Bank',
        }],
      });
    }

    if (url.includes('/categories')) {
      return json({
        categories: [
          { id: HOUSING_ID, name: 'Housing', type: 'EXPENSE', color: '#3f8efc' },
          { id: GROCERIES_ID, name: 'Groceries', type: 'EXPENSE', color: '#f2994a' },
        ],
      });
    }

    if (url.includes('/analysis/monthly')) {
      const now = new Date();
      return json({
        summary: {
          year: now.getFullYear(), month: now.getMonth() + 1,
          totalIncome: 3200, totalExpenses: 1304.2, netBalance: 1895.8,
          spendingByCategory: { [HOUSING_ID]: 1250, [GROCERIES_ID]: 54.2 },
        },
      });
    }

    if (url.includes('/budgets')) {
      // Covers both /budgets and /budgets/status; each caller reads its own key.
      return json({
        entries: [{
          categoryId: HOUSING_ID, budgeted: 1300, actual: 1250, remaining: 50,
          status: 'UNDER_BUDGET', envelopeBalance: 50,
        }],
        budgets: [],
      });
    }

    if (url.includes('/bills')) {
      return json({
        bills: [
          {
            id: '44444444-4444-4444-4444-444444444444',
            // A real statement description, not a tidy one. One long string is enough to widen
            // every page, and it arrives from a bank rather than from anyone's keyboard — so tidy
            // fixtures are exactly how this failure survives testing.
            description: 'DAUERAUFTRAG MIETE WOHNUNG HAUPTSTRASSE 42 MONATLICH',
            amount: 1250.0, time: thisMonth(1),
            categoryId: HOUSING_ID, accountId: ACCOUNT_ID,
          },
          {
            id: '55555555-5555-5555-5555-555555555555',
            description: 'SUPERMARKET', amount: 54.2, time: thisMonth(2),
            categoryId: GROCERIES_ID, accountId: ACCOUNT_ID,
          },
        ],
      });
    }

    if (url.includes('/incomes')) {
      return json({
        incomes: [{
          id: '66666666-6666-6666-6666-666666666666',
          description: 'SALARY', amount: 3200.0, time: thisMonth(1),
          source: 'SALARY', accountId: ACCOUNT_ID,
        }],
      });
    }

    if (url.includes('/recurring-series/dashboard')) {
      return json({ upcoming: [], recentPriceChanges: [] });
    }
    if (url.includes('/recurring-series/cost-summary')) return json({ items: [] });
    if (url.includes('/recurring-series')) return json({ series: [] });

    if (url.includes('/savings-goals')) return json({ goals: [] });
    if (url.includes('/cash-flow-forecast')) return json({ accounts: [] });
    if (url.includes('/sync/export')) return json({ version: 1, accounts: [], bills: [], incomes: [] });
    if (url.includes('/system/version')) return json({ version: '0.1.0' });

    return json({});
  });
}

/** The app renders the login page unless a token is present; it never validates it client-side. */
export async function signIn(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('passbook.authToken', 'stub-token');
  });
}
