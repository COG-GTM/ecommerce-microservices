import { expect, type APIRequestContext } from '@playwright/test'

export const GATEWAY_URL = process.env.TILL_GATEWAY_URL ?? 'http://localhost:8181'

export interface OrderView {
  orderNumber: string
  channel: string
  storeNumber: string
  tillId: string
  tenderType: string
  totalPence: number
  lines: { skuCode: string; price: number; quantity: number; minimumAge?: number | null; ageApprovedBy?: string | null }[]
}

/** Read-side helpers that assert on the real downstream services through the API gateway. */
export class Backend {
  constructor(private readonly request: APIRequestContext) {}

  async order(orderNumber: string): Promise<OrderView> {
    const response = await this.request.get(`${GATEWAY_URL}/api/order/${orderNumber}`)
    expect(response.status(), `order ${orderNumber} persisted in order-service`).toBe(200)
    return response.json()
  }

  /** Waits for notification-service to log the Kafka order event. */
  async notification(orderNumber: string) {
    let body: { orderNumber: string; message: string } | undefined
    await expect
      .poll(async () => {
        const response = await this.request.get(`${GATEWAY_URL}/api/notification/${orderNumber}`)
        if (response.status() === 200) body = await response.json()
        return response.status()
      }, { message: `notification for ${orderNumber}`, timeout: 20_000 })
      .toBe(200)
    return body!
  }
}
