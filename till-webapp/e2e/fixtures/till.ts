import { test as base } from '@playwright/test'
import { TillPage } from '../pages/TillPage'
import { Backend } from './backend'

/**
 * Shared fixtures. Each test gets its own till id, so every test has a private basket and specs can run
 * fully in parallel against one stack.
 */
export const test = base.extend<{ till: TillPage; backend: Backend }>({
  till: async ({ page }, use, testInfo) => {
    const tillId = `e2e-${testInfo.workerIndex}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
    await use(new TillPage(page, tillId))
  },
  backend: async ({ request }, use) => {
    await use(new Backend(request))
  },
})

export { expect } from '@playwright/test'
