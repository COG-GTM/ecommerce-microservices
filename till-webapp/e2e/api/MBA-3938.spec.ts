import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3938: every tender attempt carries an idempotency key; retrying after a timeout never pays or orders twice.
const till = () => `api-3938-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
const key = () => `attempt-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`

async function basketWithMilk(request: APIRequestContext) {
  const { basketId } = await (await request.get(`/api/till/session?tillId=${till()}`)).json()
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001' } })
  return basketId as string
}

const tender = (request: APIRequestContext, basketId: string, data: Record<string, unknown>) =>
  request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', ...data } })

async function ordersForTransaction(request: APIRequestContext, orderNumber: string) {
  const order = await (await request.get(`/api/order/${orderNumber}`)).json()
  return (await (await request.get(`/api/order/transaction/${order.transactionId}`)).json()) as { orderNumber: string }[]
}

test('decline then approve: basket stays open, one order', async ({ request }) => {
  const basketId = await basketWithMilk(request)
  const declined = await (await tender(request, basketId, { terminalOutcome: 'DECLINED', idempotencyKey: key() })).json()
  expect(declined).toMatchObject({ status: 'DECLINED', declineReason: 'Card declined by issuer', nextBasketId: basketId, basket: { status: 'OPEN', totalPence: 155 } })

  const approved = await (await tender(request, basketId, { terminalOutcome: 'APPROVED', idempotencyKey: key() })).json()
  expect(approved.status).toBe('APPROVED')
  expect(await ordersForTransaction(request, approved.orderNumber)).toHaveLength(1)
})

test('timeout then retry with the same key: one charge, exactly one order', async ({ request }) => {
  const basketId = await basketWithMilk(request)
  const attempt = key()
  const timedOut = await (await tender(request, basketId, { terminalOutcome: 'TIMEOUT', idempotencyKey: attempt })).json()
  expect(timedOut).toMatchObject({ status: 'TIMEOUT', orderNumber: null, idempotencyKey: attempt, nextBasketId: basketId, basket: { status: 'OPEN' } })

  const otherCard = await tender(request, basketId, { terminalOutcome: 'APPROVED', idempotencyKey: key() })
  expect(otherCard.status()).toBe(409)
  expect(await otherCard.json()).toMatchObject({ error: 'PAYMENT_UNRESOLVED' })
  const change = await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1002' } })
  expect(change.status()).toBe(409)

  const retried = await (await tender(request, basketId, { terminalOutcome: 'APPROVED', idempotencyKey: attempt })).json()
  expect(retried).toMatchObject({ status: 'APPROVED', idempotencyKey: attempt })
  const replayed = await (await tender(request, basketId, { terminalOutcome: 'APPROVED', idempotencyKey: attempt })).json()
  expect(replayed).toMatchObject({ status: 'APPROVED', orderNumber: retried.orderNumber, authCode: retried.authCode })

  const orders = await ordersForTransaction(request, retried.orderNumber)
  expect(orders).toEqual([expect.objectContaining({ orderNumber: retried.orderNumber })])
})
