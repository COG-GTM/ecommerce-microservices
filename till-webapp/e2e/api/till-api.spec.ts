import { expect, test, type APIRequestContext } from '@playwright/test'

// Backend API tests through the API gateway (dev profile): real till-service, product, inventory, order, notification.
const till = () => `api-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`

async function newBasket(request: APIRequestContext) {
  const session = await request.get(`/api/till/session?tillId=${till()}`)
  expect(session.status()).toBe(200)
  return (await session.json()) as { basketId: string; currency: string; backendConnected: boolean }
}

test('session reports GBP and all backing services up', async ({ request }) => {
  const session = await newBasket(request)
  expect(session.currency).toBe('GBP')
  expect(session.backendConnected).toBe(true)
})

test('quick keys return the 8 seeded grocery items in pence', async ({ request }) => {
  const products = await (await request.get('/api/till/products?quickkey=true')).json()
  expect(products.map((p: { barcode: string }) => p.barcode)).toEqual(['1001', '1002', '1003', '1004', '1005', '1006', '1007', '1008'])
  expect(products[0]).toMatchObject({ name: 'Semi skimmed milk 4 pints', pricePence: 155, skuCode: 'MOR-MILK-SS-4PT' })
})

test('barcode lookup and unknown barcode', async ({ request }) => {
  expect(await (await request.get('/api/till/products/1007')).json()).toMatchObject({ pricePence: 475 })
  const missing = await request.get('/api/till/products/0000')
  expect(missing.status()).toBe(404)
  expect(await missing.json()).toMatchObject({ error: 'UNKNOWN_BARCODE' })
})

test('basket totals are server-authoritative', async ({ request }) => {
  const { basketId } = await newBasket(request)
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1003', quantity: 2 } })
  const basket = await (await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1008' } })).json()
  expect(basket).toMatchObject({ itemCount: 3, subtotalPence: 435, savingsPence: 0, totalPence: 435, currency: 'GBP' })
  const lineId = basket.lines[0].lineId
  const updated = await (await request.patch(`/api/till/basket/lines/${lineId}`, { data: { basketId, quantity: 1 } })).json()
  expect(updated.totalPence).toBe(250)
  const voided = await (await request.delete(`/api/till/basket/lines/${lineId}?basketId=${basketId}`)).json()
  expect(voided.totalPence).toBe(65)
})

test('tender: empty basket 422, short cash 422, declined keeps basket, approved persists order', async ({ request }) => {
  const { basketId } = await newBasket(request)
  expect((await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD' } })).status()).toBe(422)
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1002' } })
  const short = await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CASH', amountTenderedPence: 50 } })
  expect(short.status()).toBe(422)
  const declined = await (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'DECLINED' } })).json()
  expect(declined).toMatchObject({ status: 'DECLINED', nextBasketId: basketId, basket: { status: 'OPEN', totalPence: 110 } })
  const approved = await (await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED' } })).json()
  expect(approved).toMatchObject({ status: 'APPROVED', basket: { status: 'COMPLETED' }, receipt: { totalPence: 110 } })
  const order = await (await request.get(`/api/order/${approved.orderNumber}`)).json()
  expect(order).toMatchObject({ channel: 'TILL', tenderType: 'CARD', totalPence: 110 })
  await expect.poll(async () => (await request.get(`/api/notification/${approved.orderNumber}`)).status(), { timeout: 20_000 }).toBe(200)
  expect((await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1002' } })).status()).toBe(409)
})

test('secured path: non-till routes still require a JWT', async ({ request }) => {
  expect((await request.get('/api/product')).status()).toBe(401)
  expect((await request.post('/api/order', { data: {} })).status()).toBe(401)
  expect([401, 403]).toContain((await request.post('/api/order/sale', { data: {} })).status())
})

test('tender rejects a stale displayed total', async ({ request }) => {
  const { basketId } = await newBasket(request)
  await request.post('/api/till/basket/lines', { data: { basketId, barcode: '1001' } })
  const stale = await request.post('/api/till/basket/tender', { data: { basketId, tenderType: 'CARD', terminalOutcome: 'APPROVED', expectedTotalPence: 100 } })
  expect(stale.status()).toBe(409)
  expect(await stale.json()).toMatchObject({ error: 'TOTAL_CHANGED' })
})
