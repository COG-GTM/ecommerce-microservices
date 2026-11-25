import { expect, test } from '../fixtures/till'
import { AgeCheckPrompt } from '../pages/AgeCheckPrompt'

// MBA-3936: Challenge 25 prompt and approval for age-restricted items (seeded barcodes 3001 lager, 3002 wine).
test.describe('MBA-3936 age-restricted items', () => {
  test('approve: ID seen -> line approved, order records the operator @tablet', async ({ till, backend, page }) => {
    const ageCheck = new AgeCheckPrompt(page)
    await till.open()
    await till.quickKey('1001')
    await till.scan('3001')
    await ageCheck.expectShownFor('Lager 4 x 440ml', 18)
    await ageCheck.approve()
    await expect(ageCheck.badge(till.line('Lager 4 x 440ml'))).toHaveText('ID checked · OP-1001')
    await expect(till.total).toHaveText('£6.05')

    await till.pay()
    await till.payByCard('approved')
    const orderNumber = await till.orderNumber()
    const order = await backend.order(orderNumber)
    expect(order).toMatchObject({ channel: 'TILL', tillId: till.tillId, totalPence: 605 })
    expect(order.lines).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ skuCode: 'MOR-LAGER-4X440', minimumAge: 18, ageApprovedBy: 'OP-1001' }),
        expect.objectContaining({ skuCode: 'MOR-MILK-SS-4PT', minimumAge: null, ageApprovedBy: null }),
      ]),
    )
    await page.getByTestId('reprint-receipt').click()
    await expect(page.getByTestId('receipt')).toContainText('Age verified by OP-1001')
  })

  test('refuse: line is removed and the rest of the sale can be paid', async ({ till, backend, page }) => {
    const ageCheck = new AgeCheckPrompt(page)
    await till.open()
    await till.quickKey('1004')
    await till.scan('3002')
    await ageCheck.expectShownFor('Red wine 75cl', 18)
    await ageCheck.refuse()
    await expect(till.line('Red wine 75cl')).toHaveCount(0)
    await expect(till.lines).toHaveCount(1)
    await expect(till.scanMessage).toHaveText('Refused Red wine 75cl: removed from the sale')
    await expect(till.total).toHaveText('£0.95')

    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.lines.map((l) => l.skuCode)).toEqual(['MOR-BANANA-5'])
  })

  test('pay with an unapproved line is blocked until the age check is approved', async ({ till, page }) => {
    const ageCheck = new AgeCheckPrompt(page)
    await till.open()
    await till.scan('3001')
    await ageCheck.later()
    await expect(ageCheck.badge(till.line('Lager 4 x 440ml'))).toHaveText('Challenge 25 · 18+')

    await page.getByTestId('pay').click()
    await expect(till.scanMessage).toHaveText('Approve or refuse age-restricted items before taking payment')
    await expect(page.getByTestId('amount-due')).toBeHidden()
    await ageCheck.expectShownFor('Lager 4 x 440ml', 18)
    await ageCheck.approve()

    await till.pay()
    await expect(page.getByTestId('amount-due')).toHaveText('£4.50')
  })
})
