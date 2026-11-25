import { expect, test } from '../fixtures/till'
import { GATEWAY_URL } from '../fixtures/backend'
import type { APIRequestContext } from '@playwright/test'

// MBA-3938: card declines and PIN pad timeouts keep the basket, and a retry never creates a second order.
async function ordersForSale(request: APIRequestContext, orderNumber: string) {
  const order = await (await request.get(`${GATEWAY_URL}/api/order/${orderNumber}`)).json()
  const response = await request.get(`${GATEWAY_URL}/api/order/transaction/${order.transactionId}`)
  expect(response.status()).toBe(200)
  return (await response.json()) as { orderNumber: string }[]
}

test.describe('MBA-3938 card terminal declines and retries', () => {
  test('decline then approve keeps the basket and places exactly one order @tablet', async ({ till, page, request }) => {
    await till.open()
    await till.quickKey('1001')
    await till.quickKey('1004')
    await till.pay()
    await till.payByCard('declined')
    await expect(page.getByTestId('decline-message')).toHaveText('Card declined. Ask for another payment method.')
    await expect(page.getByTestId('amount-due')).toHaveText('£2.50')
    await expect(till.lines).toHaveCount(2)

    await page.getByTestId('simulate-approved').click()
    const orderNumber = await till.orderNumber()
    expect(await ordersForSale(request, orderNumber)).toEqual([expect.objectContaining({ orderNumber })])
  })

  test('timeout then retry charges once and produces exactly one order', async ({ till, page, request, backend }) => {
    await till.open()
    await till.quickKey('1006')
    await till.pay()
    await till.payByCard('timeout')
    await expect(page.getByTestId('terminal-waiting')).toBeVisible()
    await expect(page.getByTestId('decline-message')).toHaveText(
      'No response from the PIN pad. Retry the payment; the customer will not be charged twice.',
    )
    await expect(page.getByTestId('amount-due')).toHaveText('£3.50')
    await expect(till.lines).toHaveCount(1)
    await expect(page.getByTestId('back-to-basket')).toBeDisabled()
    await expect(page.getByTestId('tender-cash')).toBeDisabled()
    await expect(page.getByTestId('simulate-approved')).toHaveCount(0)

    await page.getByTestId('retry-payment').click()
    const orderNumber = await till.orderNumber()
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by card · £3.50')
    expect(await ordersForSale(request, orderNumber)).toEqual([expect.objectContaining({ orderNumber })])
    expect(await backend.order(orderNumber)).toMatchObject({ tillId: till.tillId, totalPence: 350 })
  })
})
