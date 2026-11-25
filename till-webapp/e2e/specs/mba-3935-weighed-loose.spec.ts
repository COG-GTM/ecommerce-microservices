import { expect, test } from '../fixtures/till'
import { WeightDialog } from '../pages/WeightDialog'

// Seeded: PLU 2101 Deli honey roast ham £12.90/kg, PLU 2201 Loose tomatoes £2.20/kg (product-service, 2xxx range).
// 2002101003490 = prefix 2, item 002101, price 00349 (£3.49), check digit 0.
test.describe('MBA-3935 weighed and loose items', () => {
  test('price-embedded deli label adds one line at the label price and is charged @tablet', async ({ till, page, backend }) => {
    await till.open()
    await till.scan('2002101003490')
    await expect(till.scanMessage).toHaveText('Added Deli honey roast ham')
    await till.expectLine('Deli honey roast ham', 1, '£3.49', '£3.49')
    await expect(new WeightDialog(page).lineLabel(till.line('Deli honey roast ham'))).toBeVisible()
    await till.quickKey('1001')
    await expect(till.total).toHaveText('£5.04')
    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(504)
    expect(order.lines).toEqual(expect.arrayContaining([expect.objectContaining({ skuCode: 'MOR-DELI-HAM-KG', quantity: 1, price: 3.49 })]))
  })

  test('loose item without a label prompts for kg on the keypad and shows weight x price per kg @tablet', async ({ till, page, backend }) => {
    await till.open()
    await till.keyBarcode('2201')
    const weight = new WeightDialog(page)
    await expect(weight.item).toHaveText('Loose tomatoes')
    await expect(weight.price).toHaveText('£2.20/kg')
    await expect(till.lines).toHaveCount(0)
    await weight.keyWeight('0.452')
    await expect(weight.preview).toHaveText('0.452 kg × £2.20/kg = £0.99')
    await weight.confirm()
    await expect(till.scanMessage).toHaveText('Added Loose tomatoes 0.452 kg')
    const line = till.line('Loose tomatoes')
    await expect(weight.lineWeight(line)).toHaveText('0.452 kg × £2.20/kg')
    await till.expectLine('Loose tomatoes', 1, '£2.20/kg', '£0.99')
    await expect(weight.qtyPlus).toBeDisabled()
    await expect(till.total).toHaveText('£0.99')
    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(99)
  })

  test('cancelling the weight prompt adds nothing', async ({ till, page }) => {
    await till.open()
    await till.scan('2201')
    const weight = new WeightDialog(page)
    await weight.keyWeight('1')
    await weight.cancel()
    await expect(till.lines).toHaveCount(0)
    await expect(till.total).toHaveText('£0.00')
  })

  test('label with a bad check digit is rejected with a clear message', async ({ till }) => {
    await till.open()
    await till.scan('2002101003491')
    await expect(till.scanMessage).toHaveText('Barcode 2002101003491 has an invalid check digit. Rescan the label or key the item.')
    await expect(till.scanMessage).toHaveClass(/error/)
    await expect(till.lines).toHaveCount(0)
    await expect(till.total).toHaveText('£0.00')
  })
})
