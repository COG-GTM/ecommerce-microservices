import { expect, test } from '../fixtures/till'
import { RefundPage } from '../pages/RefundPage'

const MILK = 'Semi skimmed milk 4 pints'
const BANANAS = 'Bananas x5'

test.describe('refunds against a receipt (MBA-3939)', () => {
  test('partial refund, second refund to the limit, then over-refund is rejected @tablet', async ({ till, backend, page }) => {
    await till.open()
    for (let i = 0; i < 3; i++) await till.quickKey('1001')
    await till.quickKey('1004')
    await till.pay()
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    await page.getByTestId('reprint-receipt').click()
    const receiptNumber = (await page.getByTestId('receipt').textContent())!.match(/R-[\w-]+/)![0]
    await page.getByTestId('start-new-sale').click()

    const refund = new RefundPage(page)
    await refund.open()
    await refund.find(receiptNumber)
    await expect(page.getByTestId('refund-sale-order')).toHaveText(orderNumber)
    await refund.expectLine(MILK, 3, 0, 3)
    await refund.expectLine(BANANAS, 1, 0, 1)

    // Partial refund: 2 of 3 milk.
    await refund.select(MILK, 2)
    await expect(refund.total).toHaveText('£3.10')
    await refund.submit()
    const firstRefund = await refund.completed('£3.10')
    expect(await backend.order(firstRefund)).toMatchObject({ totalPence: -310, originalOrderNumber: orderNumber })
    await backend.notification(firstRefund)

    // Second refund up to the remaining limit: the last milk and the bananas.
    await refund.refundAgain()
    await refund.expectLine(MILK, 3, 2, 1)
    await refund.select(MILK, 1)
    await expect(refund.plus(MILK)).toBeDisabled()
    await refund.select(BANANAS, 1)
    await refund.submit()
    await refund.completed('£2.50')

    // Nothing left to refund on the receipt.
    await refund.refundAgain()
    await refund.expectLine(MILK, 3, 3, 0)
    await expect(refund.plus(MILK)).toBeDisabled()
    await expect(refund.plus(BANANAS)).toBeDisabled()
    await expect(page.getByTestId('refund-already')).toHaveText('£5.60')
  })

  test('over-refund is rejected when another till refunded the same receipt first', async ({ till, page, request }) => {
    await till.open()
    await till.quickKey('1001')
    await till.pay()
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    await page.getByTestId('start-new-sale').click()

    const refund = new RefundPage(page)
    await refund.open()
    await refund.find(orderNumber)
    await refund.select(MILK, 1)

    const elsewhere = await request.post('/api/till/refund', {
      data: { reference: orderNumber, tillId: 'other-till', lines: [{ skuCode: 'MOR-MILK-SS-4PT', quantity: 1 }] },
    })
    expect(elsewhere.status()).toBe(201)

    await refund.submit()
    await expect(refund.error).toContainText('already refunded')
    await expect(page.getByTestId('refund-complete')).toBeHidden()
  })

  test('unknown receipt shows an error and Back to sale returns to the open basket', async ({ till, page }) => {
    await till.open()
    await till.quickKey('1005')
    const refund = new RefundPage(page)
    await refund.open()
    await page.getByTestId('refund-reference').fill('R-0000-NOPE-0001')
    await page.getByTestId('refund-lookup').click()
    await expect(refund.error).toContainText('No completed sale')
    await page.getByTestId('refund-exit').click()
    await expect(till.lines).toHaveCount(1)
  })
})
