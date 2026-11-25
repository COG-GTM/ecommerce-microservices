import { expect, test } from '../fixtures/till'
import { MORE_CARD, MoreCard } from '../pages/MoreCard'

// Basket: mature cheddar (shelf £3.50, More Card £3.00) + milk £1.55.
test.describe('MBA-3933 More Card loyalty', () => {
  test('member card: header shows member, member price in Savings, points on receipt @tablet', async ({ till, backend, page }) => {
    const moreCard = new MoreCard(page)
    await till.open()
    await till.scan(MORE_CARD.member)
    await moreCard.expectMember('Test Member One', '0001')
    await expect(till.scanMessage).toHaveText('More Card: Test Member One added')

    await till.quickKey('1006')
    await till.quickKey('1001')
    await expect(till.subtotal).toHaveText('£5.05')
    await expect(till.savings).toHaveText('£0.50')
    await expect(till.total).toHaveText('£4.55')

    await till.pay()
    await expect(page.getByTestId('amount-due')).toHaveText('£4.55')
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    await expect(moreCard.pointsEarned).toHaveText('20 More Card points earned')
    await moreCard.showReceipt()
    await expect(moreCard.receiptMoreCard).toContainText('More Card ····0001')
    await expect(moreCard.receiptPoints).toHaveText('20')
    await expect(page.getByTestId('receipt-total')).toHaveText('£4.55')
    expect((await backend.order(orderNumber)).totalPence).toBe(455)

    await page.getByTestId('start-new-sale').click()
    await expect(moreCard.member).toHaveCount(0)
  })

  test('non-member: same basket pays shelf prices and earns no points', async ({ till, backend, page }) => {
    const moreCard = new MoreCard(page)
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1001')
    await expect(moreCard.member).toHaveCount(0)
    await expect(till.savings).toHaveText('£0.00')
    await expect(till.total).toHaveText('£5.05')

    await till.pay()
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    await expect(moreCard.pointsEarned).toHaveCount(0)
    await moreCard.showReceipt()
    await expect(page.getByTestId('receipt-total')).toHaveText('£5.05')
    await expect(moreCard.receiptMoreCard).toHaveCount(0)
    expect((await backend.order(orderNumber)).totalPence).toBe(505)
  })

  test('unknown card shows an error and the sale continues as non-member', async ({ till, page }) => {
    const moreCard = new MoreCard(page)
    await till.open()
    await till.quickKey('1006')
    await till.scan(MORE_CARD.unknown)
    await expect(till.scanMessage).toHaveText('More Card not recognised. Continuing as non-member.')
    await expect(moreCard.member).toHaveCount(0)

    await till.quickKey('1001')
    await expect(till.lines).toHaveCount(2)
    await expect(till.savings).toHaveText('£0.00')
    await expect(till.total).toHaveText('£5.05')
    await till.pay()
    await till.payByCard('approved')
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by card · £5.05')
  })
})
