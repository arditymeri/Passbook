import { defineConfig } from '@playwright/test';

/**
 * Layout checks for feature 025.
 *
 * Deliberately narrow. This is not an end-to-end suite and should not grow into one: it exists to
 * assert the one thing a machine judges better than a person and a person cannot repeat reliably —
 * that no page scrolls sideways at any supported width, on every screen, including screens added
 * after this was written. Everything else about a layout needs eyes, and lives in
 * specs/025-mobile-layout/quickstart.md as a written procedure.
 *
 * It runs against the BUILT frontend with every API call stubbed in the test itself, so there is no
 * backend, no database, and nothing to start beyond a static file server. That is what makes it
 * affordable in a CI job that already runs `npm run build`.
 */
export default defineConfig({
  testDir: './tests',

  // The widths this project supports, from specs/025-mobile-layout/contracts/layout-contract.md.
  // Each spec sets its own viewport per-test, so no project-level device is configured here.
  use: {
    baseURL: 'http://127.0.0.1:4173',
    // On failure, the trace is the only way to see what a layout looked like after the fact.
    trace: 'retain-on-failure',
    // Honour a browser the environment already provides. CI leaves this unset and uses the one
    // `npx playwright install chromium` fetches; a sandbox or image that pins its own Chromium sets
    // it and skips the download. Unset is the normal case and changes nothing.
    launchOptions: process.env.CHROMIUM_PATH
      ? { executablePath: process.env.CHROMIUM_PATH }
      : {},
  },

  // `vite preview` serves ./dist, so `npm run build` must have run first. The test:layout script
  // chains them so this cannot be forgotten.
  //
  // `--host 127.0.0.1` is not decoration. Vite preview binds to `localhost` by default, which on a
  // GitHub runner resolves to ::1 first — so the server was up and Playwright, polling
  // http://127.0.0.1:4173, waited the full 60 seconds and failed with "Timed out waiting from
  // config.webServer" and not one word about why. Binding and polling the same literal address
  // removes the ambiguity. This passed locally before it failed in CI, which is exactly the shape
  // of bug an IPv4/IPv6 default mismatch has.
  //
  // stdout/stderr are piped so the next failure of this step says something. A silent timeout is
  // the worst possible CI message.
  webServer: {
    command: 'npx vite preview --port 4173 --strictPort --host 127.0.0.1',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },

  // A layout failure is deterministic — a retry would only hide a flake that is really a bug.
  retries: 0,
  reporter: process.env.CI ? 'list' : 'line',
});
