import { defineConfig, devices } from '@playwright/test';

// Runs against the docker-compose.test.yml stack (scripts/system-stack.sh up).
// Every run records itself: video + trace + screenshots for every test, saved under
// e2e/test-results and the HTML report under e2e/playwright-report (uploaded by CI).
export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  outputDir: 'test-results',
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
  ],
  use: {
    baseURL: process.env.STOREFRONT_URL ?? 'http://localhost:3001',
    video: 'on',
    trace: 'on',
    screenshot: 'on',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
