import { expect, test } from '../fixtures/till'

test.describe('card tender', () => {
  test('card approved -> order persisted, notification logged, receipt @tablet', async ({ till, backend, page }) => {
    await till.open()
    await till.quickKey('1001')
    await till.quickKey('1004')
    await till.quickKey('1004')
    await till.pay()
    await expect(page.getByTestId('amount-due')).toHaveText('£3.45')
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by card · £3.45')

    const order = await backend.order(orderNumber)
    expect(order).toMatchObject({ channel: 'TILL', tillId: till.tillId, tenderType: 'CARD', totalPence: 345 })
    expect(order.lines).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ skuCode: 'MOR-MILK-SS-4PT', quantity: 1 }),
        expect.objectContaining({ skuCode: 'MOR-BANANA-5', quantity: 2 }),
      ]),
    )
    const notification = await backend.notification(orderNumber)
    expect(notification.message).toBe(`Received Notification for Order - ${orderNumber}`)

    await page.getByTestId('reprint-receipt').click()
    await expect(page.getByTestId('receipt')).toContainText('2 × Bananas x5')
    await expect(page.getByTestId('receipt-total')).toHaveText('£3.45')

    await page.getByTestId('start-new-sale').click()
    await expect(till.lines).toHaveCount(0)
    await expect(page.getByTestId('barcode-input')).toBeVisible()
  })

  test('contactless approved', async ({ till, backend }) => {
    await till.open()
    await till.quickKey('1006')
    await till.pay()
    await till.payByCard('approved', 'contactless')
    const order = await backend.order(await till.orderNumber())
    expect(order).toMatchObject({ tenderType: 'CONTACTLESS', totalPence: 350 })
  })

  test('card declined keeps the basket and allows another tender', async ({ till, page }) => {
    await till.open()
    await till.quickKey('1001')
    await till.pay()
    await till.payByCard('declined')
    await expect(page.getByTestId('decline-message')).toHaveText('Card declined. Ask for another payment method.')
    await expect(till.lines).toHaveCount(1)
    await expect(till.total).toHaveText('£1.55')

    await page.getByTestId('back-to-basket').click()
    await till.expectLine('Semi skimmed milk 4 pints', 1, '£1.55', '£1.55')
    await till.pay()
    await till.payByCard('approved')
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by card · £1.55')
  })
})
