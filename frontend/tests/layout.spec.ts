import { test, expect, Page } from '@playwright/test';
import { signIn, stubApi } from './fixtures';

/**
 * Does any screen scroll sideways?
 *
 * That is the whole of this file, plus "can you reach everything". Both are checkable by a machine
 * and tedious and unreliable for a person to repeat across three widths and every screen.
 * Everything else about a layout — whether text is legible, whether it reads sensibly, whether the
 * desktop still looks right — needs eyes, and is written out as a procedure in
 * specs/025-mobile-layout/quickstart.md rather than pretended at here.
 *
 * THREE THINGS THAT SHAPE THIS FILE, all established by writing it and all surprising:
 *
 * 1. THERE IS NO ROUTER. Screens are React state in App.tsx (`view === 'accounts'`), not URLs, so
 *    this cannot goto() a screen. It has to drive the navigation the way a person would. That turns
 *    out to be better than a router would have been: reaching a screen at all is itself the check
 *    for SC-002, so navigation and overflow are proven in one pass.
 *
 * 2. THE APP IS BEHIND A LOGIN. The frontend only asks whether a token exists in localStorage, so
 *    seeding one and stubbing the API is enough. No backend, no database, no Testcontainers.
 *
 * 3. A BLANK PAGE NEVER SCROLLS SIDEWAYS. The first version of this file passed at 320px against
 *    an app that had crashed on a malformed stub and rendered nothing — a green check measuring an
 *    empty <div id="root">. That is the failure mode this whole file exists to avoid, so every
 *    measurement is now preceded by expectAppRendered(): the page must prove it has content before
 *    its width means anything.
 */

const WIDTHS = [
  { name: '320 (floor)', width: 320, height: 568 },
  { name: '375 (reference phone)', width: 375, height: 667 },
  { name: '1280 (desktop)', width: 1280, height: 800 },
  // A phone turned sideways: short rather than narrow. SC-007 and quickstart scenario 10 call this
  // "by eye", and the legible half of it still is — but the horizontal half costs nothing here, and
  // 667px is above the 600px boundary, so this is the one case that exercises the desktop
  // arrangement in a viewport that cannot show it comfortably.
  { name: '667x375 (landscape phone)', width: 667, height: 375 },
];

/** The five full-page views. Recurring, Import and Change Password are dialogs, checked separately. */
const SCREENS = ['Accounts', 'Budgeting', 'Categories', 'Goals', 'Sync'];

/** The dialogs reachable from the header. Together with SCREENS these are all nine destinations. */
const DIALOGS = ['Recurring', 'Import', 'Change Password'];

/**
 * The page must have rendered before its width tells you anything.
 *
 * Without this, any crash — a bad stub, a broken component, a failed chunk — turns into a green
 * run, because an empty document fits every viewport. This is the guard, not a courtesy.
 */
async function expectAppRendered(page: Page) {
  await expect(page.getByRole('banner')).toBeVisible();
  await expect(
    page.getByRole('button').first(),
    'the app rendered no controls at all — it has probably crashed, and an empty page fits every viewport',
  ).toBeVisible();
}

/**
 * The invariant, and the reason this file exists.
 *
 * A page wider than its viewport is FR-012 and SC-001 violated. The 1px tolerance is for
 * sub-pixel rounding in the layout engine, not slack in the rule. On failure it names what is
 * sticking out, because "1276 > 320" on its own tells you nothing about which component to fix.
 */
async function expectNoHorizontalScroll(page: Page, where: string) {
  const overflow = await page.evaluate(() => {
    const past: string[] = [];
    document.querySelectorAll('*').forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.right > window.innerWidth + 1) {
        past.push(`<${el.tagName.toLowerCase()}> ${Math.round(r.width)}px "${(el.textContent || '').trim().slice(0, 40)}"`);
      }
    });
    return {
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
      widest: past.slice(0, 6),
    };
  });

  expect(
    overflow.scrollWidth,
    `${where} scrolls horizontally: content is ${overflow.scrollWidth}px wide in a ${overflow.innerWidth}px viewport.\n`
      + `Past the right edge:\n  ${overflow.widest.join('\n  ')}`,
  ).toBeLessThanOrEqual(overflow.innerWidth + 1);
}

/**
 * Nothing on this screen has to be swiped sideways to be read.
 *
 * WHY THIS IS SEPARATE FROM THE ONE ABOVE, and why the feature would have shipped broken without
 * it: MUI's TableContainer sets `overflow-x: auto`. A six-column table in a 320px viewport
 * therefore scrolls *inside its own box* and the document stays exactly 320px wide — so
 * expectNoHorizontalScroll passes while the primary content of the dashboard is unreadable without
 * swiping. That is US2's entire complaint, and the page-level check cannot see it.
 *
 * Applied to the dashboard only. The sync page's table is explicitly allowed to scroll in its own
 * container (quickstart scenario 9): it is rarely visited and does not warrant the transaction
 * list's treatment. The dashboard is the screen the app is opened to look at.
 */
async function expectNothingSwipesSideways(page: Page, where: string) {
  const scrollers = await page.evaluate(() =>
    Array.from(document.querySelectorAll('*'))
      .filter((el) => {
        if (el.clientWidth === 0 || el.scrollWidth <= el.clientWidth + 1) return false;
        // Only `auto` and `scroll` produce a swipe. `hidden` clips, which is what an ellipsis
        // truncation is — deliberate, readable, and not a thing anyone has to drag. Flagging it
        // would make every correctly truncated description a failure and the check useless.
        const overflowX = getComputedStyle(el).overflowX;
        return overflowX === 'auto' || overflowX === 'scroll';
      })
      .map((el) => `<${el.tagName.toLowerCase()}> ${el.scrollWidth}px inside ${el.clientWidth}px "${(el.textContent || '').trim().slice(0, 40)}"`)
      .slice(0, 6));

  expect(
    scrollers,
    `${where} has content that must be swiped sideways to read:\n  ${scrollers.join('\n  ')}`,
  ).toEqual([]);
}

/**
 * Reaches a destination the way a person does. At phone width the destinations live behind a menu;
 * at desktop width they are in the header. Trying the header first and falling back keeps this
 * working at every width without the test needing to know which layout it is looking at — which
 * also means it cannot silently pass by only ever exercising one of them.
 */
async function navigateTo(page: Page, label: string) {
  const direct = page.getByRole('button', { name: label, exact: true });
  if (await direct.isVisible().catch(() => false)) {
    await direct.click();
    return;
  }

  const menu = page.getByRole('button', { name: /open navigation|menu/i });
  await expect(
    menu,
    `no way to reach "${label}": it is not in the header and there is no navigation menu`,
  ).toBeVisible();
  await menu.click();
  await page.getByRole('button', { name: label, exact: true })
    .or(page.getByRole('link', { name: label, exact: true }))
    .first()
    .click();
}

for (const { name, width, height } of WIDTHS) {
  test.describe(`at ${name}`, () => {
    test.use({ viewport: { width, height } });

    test.beforeEach(async ({ page }) => {
      await signIn(page);
      await stubApi(page);
    });

    test('the dashboard does not scroll sideways', async ({ page }) => {
      await page.goto('/');
      await expectAppRendered(page);
      await expectNoHorizontalScroll(page, `The dashboard at ${width}px`);

      // Phone widths only. Above the 600px boundary the transaction list is the six-column table
      // it has always been, and a table that scrolls inside its own container at a narrow desktop
      // width is existing behaviour this feature deliberately does not change (SC-006) — the
      // stacked rendering is the phone layout, not a second desktop one. Asserting this at 667px
      // would be asserting that the desktop layout must also be a phone layout.
      if (width < 600) {
        await expectNothingSwipesSideways(page, `The dashboard at ${width}px`);
      }
    });

    test('every screen is reachable and none scrolls sideways', async ({ page }) => {
      // SC-002 and SC-001 together. Reaching a screen is the reachability check; measuring it once
      // there is the overflow check. A screen that cannot be reached fails loudly in navigateTo
      // rather than being quietly skipped.
      await page.goto('/');
      await expectAppRendered(page);

      for (const screen of SCREENS) {
        await navigateTo(page, screen);
        await page.waitForTimeout(150); // let the view settle before measuring
        await expectNoHorizontalScroll(page, `"${screen}" at ${width}px`);

        const back = page.getByRole('button', { name: /back/i }).first();
        if (await back.isVisible().catch(() => false)) {
          await back.click();
        } else {
          await page.goto('/');
        }
        await expectAppRendered(page);
      }
    });

    test('every dialog is reachable and none scrolls sideways', async ({ page }) => {
      await page.goto('/');
      await expectAppRendered(page);

      for (const dialog of [...DIALOGS, '+ Add Expense', '+ Add Income']) {
        await navigateTo(page, dialog);
        await expect(page.getByRole('dialog')).toBeVisible();
        await page.waitForTimeout(150);
        await expectNoHorizontalScroll(page, `The "${dialog}" dialog at ${width}px`);

        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog')).toBeHidden();
      }
    });
  });
}
