import { expect, test } from '../fixtures/till'
import { Promotions } from '../pages/Promotions'

// Seeded promotion products (product-service PromotionCatalogueSeeder) and offers (till-service application.properties).
const YOGHURT = { barcode: '393407', name: 'Greek style yoghurt 500g' }
const CHICKEN_SANDWICH = { barcode: '393401', name: 'Chicken and bacon sandwich' }
const CRISPS = { barcode: '393403', name: 'Ready salted crisps 30g' }
const COLA = { barcode: '393406', name: 'Cola 500ml' }

test.describe('MBA-3934 multi-buy and meal-deal promotions', () => {
  test('3 for £3.50 triggers on the third scan and un-triggers on void @tablet', async ({ till, page, backend }) => {
    const promotions = new Promotions(till)
    await till.open()
    await till.scan(YOGHURT.barcode)
    await till.scan(YOGHURT.barcode)
    await till.expectLine(YOGHURT.name, 2, '£1.50', '£3.00')
    await expect(promotions.savingLines).toHaveCount(0)
    await expect(till.total).toHaveText('£3.00')

    await till.scan(YOGHURT.barcode)
    await till.expectLine(YOGHURT.name, 3, '£1.50', '£4.50')
    await promotions.expectSavingUnder(YOGHURT.name, 'Greek yoghurt 3 for £3.50', '-£1.00')
    await expect(till.savings).toHaveText('£1.00')
    await expect(till.total).toHaveText('£3.50')

    await till.selectLine(YOGHURT.name)
    await page.getByTestId('void-line').click()
    await expect(till.lines).toHaveCount(0)
    await expect(promotions.savingLines).toHaveCount(0)
    await expect(till.savings).toHaveText('£0.00')
    await expect(till.total).toHaveText('£0.00')

    for (let i = 0; i < 3; i++) await till.scan(YOGHURT.barcode)
    await expect(till.total).toHaveText('£3.50')
    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(350)
  })

  test('saving updates live on quantity change', async ({ till, page }) => {
    const promotions = new Promotions(till)
    await till.open()
    for (let i = 0; i < 3; i++) await till.scan(YOGHURT.barcode)
    await promotions.expectSavingUnder(YOGHURT.name, 'Greek yoghurt 3 for £3.50', '-£1.00')

    await till.selectLine(YOGHURT.name)
    await page.getByTestId('qty-minus').click()
    await till.expectLine(YOGHURT.name, 2, '£1.50', '£3.00')
    await expect(promotions.savingLines).toHaveCount(0)
    await expect(till.total).toHaveText('£3.00')

    for (let i = 0; i < 4; i++) await page.getByTestId('qty-plus').click()
    await till.expectLine(YOGHURT.name, 6, '£1.50', '£9.00')
    await promotions.expectSavingUnder(YOGHURT.name, 'Greek yoghurt 3 for £3.50 x2', '-£2.00')
    await expect(till.total).toHaveText('£7.00')
  })

  test('meal deal shows one saving under the last qualifying item and goes when a part is voided', async ({ till, page }) => {
    const promotions = new Promotions(till)
    await till.open()
    await till.scan(CHICKEN_SANDWICH.barcode)
    await till.scan(CRISPS.barcode)
    await expect(promotions.savingLines).toHaveCount(0)
    await till.scan(COLA.barcode)
    await promotions.expectSavingUnder(COLA.name, 'Meal deal £3.50', '-£1.70')
    await expect(promotions.savingLines).toHaveCount(1)
    await expect(till.subtotal).toHaveText('£5.20')
    await expect(till.total).toHaveText('£3.50')

    await till.selectLine(CRISPS.name)
    await page.getByTestId('void-line').click()
    await expect(promotions.savingLines).toHaveCount(0)
    await expect(till.total).toHaveText('£4.35')
  })

  test('item in two offers gets the best price for the customer', async ({ till, page, backend }) => {
    const promotions = new Promotions(till)
    await till.open()
    await till.scan(COLA.barcode)
    await till.scan(COLA.barcode)
    await promotions.expectSavingUnder(COLA.name, 'Soft drinks 2 for £2.00', '-£0.70')

    // A cola in the meal deal saves £1.70, more than 2 colas for £2.00 (£0.70); a cola cannot count twice.
    await till.scan(CHICKEN_SANDWICH.barcode)
    await till.scan(CRISPS.barcode)
    await promotions.expectSavingUnder(CRISPS.name, 'Meal deal £3.50', '-£1.70')
    await expect(promotions.savingLines).toHaveCount(1)
    await expect(till.savings).toHaveText('£1.70')
    await expect(till.total).toHaveText('£4.85')

    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(485)
    await expect(page.getByTestId('sale-complete')).toBeVisible()
  })
})
