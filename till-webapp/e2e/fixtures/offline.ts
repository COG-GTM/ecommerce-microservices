import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { expect, type APIRequestContext } from '@playwright/test'
import { OfflineTill } from '../pages/OfflineTill'
import { GATEWAY_URL, type OrderView } from './backend'
import { test as base } from './till'

export interface TillOrderView extends OrderView {
  transactionId: string
}

/** Read side for offline sales: every order order-service stored for a till. */
export class OfflineBackend {
  constructor(private readonly request: APIRequestContext) {}

  async ordersForTill(tillId: string): Promise<TillOrderView[]> {
    const response = await this.request.get(`${GATEWAY_URL}/api/order`, { params: { tillId } })
    expect(response.status(), `orders for till ${tillId}`).toBe(200)
    return response.json()
  }

  /** Waits until order-service holds exactly `count` orders for the till, then checks it stays at that count. */
  async expectOrdersForTill(tillId: string, count: number) {
    await expect.poll(async () => (await this.ordersForTill(tillId)).length, { timeout: 30_000 }).toBe(count)
    await new Promise((resolve) => setTimeout(resolve, 2_000))
    const orders = await this.ordersForTill(tillId)
    expect(orders, 'no duplicate orders after replay').toHaveLength(count)
    return orders
  }
}

/** Outage tests stop the local compose gateway, so they only run against the local stack. */
export const LOCAL_STACK = !process.env.TILL_URL && !process.env.TILL_GATEWAY_URL

const COMPOSE_FILE = path.resolve(import.meta.dirname, '../../../deploy/till/docker-compose.till.yml')

/** Controls the real docker compose stack (`make till-up`) for outage tests. */
export class Stack {
  private compose(...args: string[]) {
    execFileSync('docker', ['compose', '-f', COMPOSE_FILE, ...args], { stdio: 'pipe', timeout: 240_000 })
  }

  stopGateway() {
    this.compose('stop', 'api-gateway')
  }

  startGateway() {
    this.compose('start', 'api-gateway')
    this.compose('up', '-d', '--wait', '--wait-timeout', '180', 'api-gateway')
  }
}

export const test = base.extend<{ offlineTill: OfflineTill; offlineBackend: OfflineBackend; stack: Stack }>({
  offlineTill: async ({ till }, use) => {
    await use(new OfflineTill(till))
  },
  offlineBackend: async ({ request }, use) => {
    await use(new OfflineBackend(request))
  },
  // eslint-disable-next-line no-empty-pattern
  stack: async ({}, use) => {
    const stack = new Stack()
    await use(stack)
    stack.startGateway()
  },
})

export { expect } from '@playwright/test'
