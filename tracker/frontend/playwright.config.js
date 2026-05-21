import { defineConfig, devices } from '@playwright/test'

// Headless E2E against the running tracker test env (full stack: nginx + backend
// + seeded Postgres). Run via tracker/testenv/e2e.sh, which drives the official
// Playwright Docker image (no host browser/deps needed). Override the target
// with E2E_BASE_URL.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    // 127.0.0.1 not localhost — Chromium resolves localhost to ::1, but the
    // test-env port is published on IPv4 only.
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:18080/tracker/',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ignoreHTTPSErrors: true,
  },
  // --no-sandbox: in the Docker image Chromium's sandbox isolates the renderer's
  // net namespace, so it can't reach the host-published test-env port even under
  // --network host. Disabling the sandbox lets it share the container's network.
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], launchOptions: { args: ['--no-sandbox', '--disable-setuid-sandbox'] } },
    },
  ],
})
