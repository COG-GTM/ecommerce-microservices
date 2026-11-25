import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { GATEWAY_URL } from '../fixtures/backend'
import { expect, test } from '../fixtures/till'

// MBA-3941: prices reach the till from the product price-feed API instead of the legacy nightly price file.
// Serial and desktop-only (no @tablet tag): these tests change shared catalogue prices and restore them.
test.describe.configure({ mode: 'serial' })

const FEED = `${GATEWAY_URL}/api/product/price-feed`
const LEGACY_FILE = fileURLToPath(new URL('../../../docs/morrisons-till/legacy/PRICEFILE.DAT', import.meta.url))

/** Detail records of the fixed-width legacy file: type(1) barcode(13) sku(20) price pence(7) effective date(8). */
function legacyPrices() {
  return readFileSync(LEGACY_FILE, 'ascii').split(/\r?\n/).filter((l) => l.startsWith('D')).map((l) => ({
    barcode: l.slice(1, 14).trim(),
    skuCode: l.slice(14, 34).trim(),
    pricePence: Number(l.slice(34, 41)),
  }))
}

test.describe('MBA-3941 price feed', () => {
  let before: { barcode: string; pricePence: number }[] = []

  test.beforeAll(async ({ request }) => {
    const barcodes = new Set(legacyPrices().map((p) => p.barcode))
    const snapshot = await (await request.get(FEED)).json()
    before = snapshot.prices
      .filter((p: { barcode: string; pricePence: number }) => barcodes.has(p.barcode) && p.pricePence > 0)
      .map(({ barcode, pricePence }: { barcode: string; pricePence: number }) => ({ barcode, pricePence }))
  })

  test.afterAll(async ({ request }) => {
    if (!before.length) return
    const restored = await request.put(FEED, { data: { prices: before } })
    expect(restored.status(), 'restore pre-test prices').toBe(200)
  })

  test('legacy price file and price feed give the till identical prices for every seeded item', async ({ request }) => {
    const fromFile = legacyPrices()
    expect(fromFile).toHaveLength(13)
    const published = await request.put(FEED, { data: { prices: fromFile.map(({ barcode, pricePence }) => ({ barcode, pricePence })) } })
    expect(published.status()).toBe(200)
    for (const item of fromFile) {
      const onTill = await (await request.get(`${GATEWAY_URL}/api/till/products/${item.barcode}`)).json()
      expect(onTill, `till price for ${item.barcode}`).toMatchObject({ skuCode: item.skuCode, pricePence: item.pricePence })
    }
  })

  test('price changed through the feed shows on the next scan without a restart', async ({ till, request, backend }) => {
    await till.open()
    await till.scan('394102')
    await till.expectLine('Instant coffee 200g', 1, '£4.25', '£4.25')
    await till.voidLine()
    await expect(till.lines).toHaveCount(0)

    const published = await request.put(FEED, { data: { prices: [{ barcode: '394102', pricePence: 399 }] } })
    expect(published.status()).toBe(200)

    await till.scan('394102')
    await expect(till.scanMessage).toHaveText('Added Instant coffee 200g')
    await till.expectLine('Instant coffee 200g', 1, '£3.99', '£3.99')
    await expect(till.total).toHaveText('£3.99')

    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(399)
  })
})
