import { expect, test } from '../fixtures/till'
import { VatSummaryPanel } from '../pages/VatSummaryPanel'

// MBA-3932: per-item UK VAT. Milk (1001) is zero-rated; chocolate (4001) and washing up liquid (4002) are standard-rated.
test.describe('UK VAT by item', () => {
  test('mixed basket shows the VAT breakdown per rate on the basket and the receipt @tablet', async ({ till, backend, page }) => {
    const vat = new VatSummaryPanel(page)
    await till.open()
    await till.quickKey('1001')
    await till.scan('4001')
    await till.scan('4002')

    await till.expectLine('Milk chocolate bar 100g', 1, '£1.20', '£1.20')
    await expect(vat.lineVatCode('Milk chocolate bar 100g')).toHaveText('S')
    await expect(vat.lineVatCode('Semi skimmed milk 4 pints')).toHaveText('Z')
    await expect(till.total).toHaveText('£4.10')
    await vat.expectBand('basket', 'Z', 'Z 0%', '£1.55', '£0.00', '£1.55')
    // 20% band: VAT on the band gross £2.55 is 42.5p, rounded once to 43p.
    await vat.expectBand('basket', 'S', 'S 20%', '£2.12', '£0.43', '£2.55')

    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(410)

    await vat.showReceipt()
    await expect(vat.receiptTotal).toHaveText('£4.10')
    await vat.expectBand('receipt', 'Z', 'Z 0%', '£1.55', '£0.00', '£1.55')
    await vat.expectBand('receipt', 'S', 'S 20%', '£2.12', '£0.43', '£2.55')
  })

  test('zero-rated only basket has a single 0% band', async ({ till, page }) => {
    const vat = new VatSummaryPanel(page)
    await till.open()
    await till.quickKey('1004')
    await till.quickKey('1004')
    await expect(vat.band('basket', 'S')).toHaveCount(0)
    await vat.expectBand('basket', 'Z', 'Z 0%', '£1.90', '£0.00', '£1.90')
  })
})
