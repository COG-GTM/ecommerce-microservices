import { defineConfig, devices } from '@playwright/test'

// Runs against the already-running stack (`make till-up`); nothing here starts or mocks services.
export const TILL_URL = process.env.TILL_URL ?? 'http://localhost:5174'
export const GATEWAY_URL = process.env.TILL_GATEWAY_URL ?? 'http://localhost:8181'

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e-results',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 4,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'e2e-report', open: 'never' }]],
  use: {
    baseURL: TILL_URL,
    video: 'on',
    screenshot: 'on',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'api', testDir: './e2e/api', use: { baseURL: GATEWAY_URL } },
    { name: 'desktop', testDir: './e2e/specs', use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } },
    {
      name: 'tablet',
      testDir: './e2e/specs',
      grep: /@tablet/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1024, height: 768 }, hasTouch: true },
    },
  ],
})
