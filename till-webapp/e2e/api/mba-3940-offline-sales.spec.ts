import { expect, test } from '@playwright/test'
import { OfflineBackend } from '../fixtures/offline'

// MBA-3940: POST /api/till/offline-sales replays a queued offline cash sale; it is idempotent on saleId.
const tillId = () => `e2e-api-off-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
const sale = (till: string, saleId: string, extra: Record<string, unknown> = {}) => ({
  saleId,
  tillId: till,
  transactionNumber: `0417-${till}-OFF1`,
  tenderType: 'CASH',
  amountTenderedPence: 1000,
  lines: [{ barcode: '1001', skuCode: 'MOR-MILK-SS-4PT', name: 'Semi skimmed milk 4 pints', quantity: 2, unitPricePence: 155 }],
  completedAt: new Date().toISOString(),
  ...extra,
})

test('offline sale replay is idempotent: same sale twice is one order', async ({ request }) => {
  const till = tillId()
  const saleId = crypto.randomUUID()
  const first = await request.post('/api/till/offline-sales', { data: sale(till, saleId) })
  expect(first.status()).toBe(200)
  const body = await first.json()
  expect(body).toMatchObject({ saleId, totalPence: 310, amountTenderedPence: 1000, transactionNumber: `0417-${till}-OFF1` })

  const again = await request.post('/api/till/offline-sales', { data: sale(till, saleId) })
  expect(again.status()).toBe(200)
  expect((await again.json()).orderNumber).toBe(body.orderNumber)

  const orders = await new OfflineBackend(request).expectOrdersForTill(till, 1)
  expect(orders[0]).toMatchObject({ orderNumber: body.orderNumber, channel: 'TILL', tenderType: 'CASH', totalPence: 310 })
})

test('offline sale replay rejects card and unknown items', async ({ request }) => {
  const till = tillId()
  const card = await request.post('/api/till/offline-sales', { data: sale(till, crypto.randomUUID(), { tenderType: 'CARD' }) })
  expect(card.status()).toBe(400)
  expect((await card.json()).error).toBe('UNSUPPORTED_TENDER')
  const unknown = await request.post('/api/till/offline-sales', {
    data: sale(till, crypto.randomUUID(), { lines: [{ barcode: '5000000000000', quantity: 1 }] }),
  })
  expect(unknown.status()).toBe(422)
  expect(await new OfflineBackend(request).ordersForTill(till)).toHaveLength(0)
})
