import { defineConfig, devices } from '@playwright/test';

/**
 * E2E smoke tests (#343): a handful of critical-path journeys over the real UI + a live backend.
 *
 * Playwright starts the Vite dev server itself (`webServer` below). It does NOT start the backend —
 * that's a JVM/Postgres stack: run `make start` locally (Postgres via docker-compose + the backend),
 * or, in CI, the `e2e` job brings up Postgres + the backend before invoking Playwright. `globalSetup`
 * waits for the backend's /health probe and fails fast with a clear message if it isn't reachable.
 *
 * The web app defaults its API base URL to http://localhost:8080 when VITE_API_BASE_URL is unset
 * (see src/api/client.ts), which is exactly where the backend listens locally + in CI — so no env
 * wiring is needed here.
 */
const WEB_BASE_URL = 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  // Smoke tests are a single happy-path journey; keep them serial + fail-fast in CI.
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: WEB_BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // Dev server is enough for a smoke test (and faster than build+preview). It reads no env here —
    // the client falls back to the localhost:8080 backend default.
    command: 'npm run dev',
    url: WEB_BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
