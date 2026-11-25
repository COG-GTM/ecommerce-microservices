import { expect, test } from '@playwright/test'

// MBA-3941: product price-feed API through the gateway (stack runs with pricing.source=price-feed).
// Only read-only or rejected writes here, so these never race the desktop spec that changes prices.
const FEED = '/api/product/price-feed'

test('feed is the active price source and lists every seeded item', async ({ request }) => {
  const snapshot = await (await request.get(FEED)).json()
  expect(snapshot.source).toBe('PRICE_FEED')
  expect(snapshot.prices.map((p: { barcode: string }) => p.barcode)).toEqual(
    expect.arrayContaining(['1001', '1002', '1003', '1004', '1005', '1006', '1007', '1008', '4001', '4002', '4003']))
  expect(snapshot.prices.find((p: { barcode: string }) => p.barcode === '1007')).toMatchObject({ pricePence: 475 })
})

test('a batch with an unknown barcode or bad price is rejected whole', async ({ request }) => {
  const unknown = await request.put(FEED, { data: { prices: [{ barcode: '4001', pricePence: 1 }, { barcode: '0000', pricePence: 1 }] } })
  expect(unknown.status()).toBe(404)
  expect(await unknown.json()).toMatchObject({ error: 'UNKNOWN_BARCODE' })
  expect((await request.put(FEED, { data: { prices: [{ barcode: '4001', pricePence: 0 }] } })).status()).toBe(400)
  expect((await request.put(FEED, { data: { prices: [] } })).status()).toBe(400)
  expect(await (await request.get('/api/till/products/4001')).json()).toMatchObject({ pricePence: 250 })
})

test('the legacy price-file import is retired behind the flag', async ({ request }) => {
  const legacy = await request.post(`${FEED}/legacy-import`)
  expect(legacy.status()).toBe(409)
  expect(await legacy.json()).toMatchObject({ error: 'LEGACY_PRICE_FILE_RETIRED' })
})
