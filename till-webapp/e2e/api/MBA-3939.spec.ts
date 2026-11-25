import { expect, test, type APIRequestContext } from '@playwright/test'

// MBA-3939: refunds against a receipt, through the gateway against real till-service, order-service and Kafka.
const till = () => `refund-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

/** Sells 3 x milk (1001, 155p) and 1 x bananas (1004, 95p) by card; returns the order number and receipt number. */
async function completedSale(request: APIRequestContext) {
  const tillId = till()
  const { basketId } = await (await request.get(`/api/till/session?tillId=${tillId}`)).json()
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001', quantity: 3 } })
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1004' } })
  const tender = await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })
  expect(tender.status()).toBe(200)
  const result = await tender.json()
  return { tillId, orderNumber: result.orderNumber as string, receiptNumber: result.receipt.receiptNumber as string }
}

const refund = (request: APIRequestContext, reference: string, quantity: number, tillId = till()) =>
  request.post('/api/till/refund', { data: { tillId, reference, lines: [{ skuCode: 'MOR-MILK-SS-4PT', quantity }] } })

async function refundNotification(request: APIRequestContext, refundNumber: string) {
  let body: Record<string, unknown> = {}
  await expect.poll(async () => {
    const response = await request.get(`/api/notification/${refundNumber}`)
    if (response.status() === 200) body = await response.json()
    return response.status()
  }, { timeout: 20_000 }).toBe(200)
  return body
}

test('look up a completed sale by receipt number or order number', async ({ request }) => {
  const { orderNumber, receiptNumber } = await completedSale(request)
  const byReceipt = await (await request.get(`/api/till/refund/${receiptNumber}`)).json()
  expect(byReceipt).toMatchObject({ orderNumber, totalPence: 560, refundedPence: 0, tenderType: 'CARD' })
  expect(byReceipt.lines).toEqual(expect.arrayContaining([
    expect.objectContaining({ skuCode: 'MOR-MILK-SS-4PT', name: 'Semi skimmed milk 4 pints', unitPricePence: 155, soldQuantity: 3, refundableQuantity: 3 }),
    expect.objectContaining({ skuCode: 'MOR-BANANA-5', soldQuantity: 1, refundableQuantity: 1 }),
  ]))
  expect((await (await request.get(`/api/till/refund/${orderNumber}`)).json()).orderNumber).toBe(orderNumber)
  const missing = await request.get('/api/till/refund/R-0000-NOPE-0001')
  expect(missing.status()).toBe(404)
  expect(await missing.json()).toMatchObject({ error: 'UNKNOWN_ORDER' })
})

test('partial refund, second refund up to the limit, then over-refund is rejected', async ({ request }) => {
  const { orderNumber, receiptNumber } = await completedSale(request)

  const first = await refund(request, receiptNumber, 2)
  expect(first.status()).toBe(201)
  const firstResult = await first.json()
  expect(firstResult).toMatchObject({ status: 'REFUNDED', originalOrderNumber: orderNumber, refundPence: 310 })
  expect(firstResult.remaining.lines.find((l: { skuCode: string }) => l.skuCode === 'MOR-MILK-SS-4PT')).toMatchObject({ refundedQuantity: 2, refundableQuantity: 1 })

  // Persisted as a negative transaction linked to the original order, and published as a REFUND event.
  const persisted = await (await request.get(`/api/order/${firstResult.refundNumber}`)).json()
  expect(persisted).toMatchObject({ orderType: 'REFUND', originalOrderNumber: orderNumber, totalPence: -310 })
  expect(persisted.lines).toEqual([expect.objectContaining({ skuCode: 'MOR-MILK-SS-4PT', quantity: -2 })])
  expect(await refundNotification(request, firstResult.refundNumber)).toMatchObject({ eventType: 'REFUND', originalOrderNumber: orderNumber, totalPence: -310 })

  const second = await refund(request, orderNumber, 1)
  expect(second.status()).toBe(201)
  expect((await second.json()).remaining).toMatchObject({ refundedPence: 465, refundablePence: 95 })

  const over = await refund(request, receiptNumber, 1)
  expect(over.status()).toBe(409)
  expect(await over.json()).toMatchObject({ error: 'REFUND_EXCEEDS_SOLD' })
  const sale = await (await request.get(`/api/till/refund/${orderNumber}`)).json()
  expect(sale.refunds).toHaveLength(2)
  expect((await (await request.get(`/api/order/${orderNumber}`)).json())).toMatchObject({ orderType: 'SALE', totalPence: 560 })
})

test('invalid refunds are rejected', async ({ request }) => {
  const { orderNumber } = await completedSale(request)
  expect((await refund(request, orderNumber, 0)).status()).toBe(400)
  const notSold = await request.post('/api/till/refund', { data: { reference: orderNumber, lines: [{ skuCode: 'MOR-BREAD-WHITE', quantity: 1 }] } })
  expect(notSold.status()).toBe(400)
  expect(await notSold.json()).toMatchObject({ error: 'INVALID_REFUND' })
  const refundNumber = (await (await refund(request, orderNumber, 1)).json()).refundNumber
  const ofRefund = await request.get(`/api/till/refund/${refundNumber}`)
  expect(ofRefund.status()).toBe(422)
  expect(await ofRefund.json()).toMatchObject({ error: 'NOT_REFUNDABLE' })
})
