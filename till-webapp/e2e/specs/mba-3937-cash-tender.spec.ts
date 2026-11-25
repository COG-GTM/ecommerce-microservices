import { expect, test } from '../fixtures/till'
import type { OrderView } from '../fixtures/backend'

// MBA-3937: cash tender with server-side change, and part cash plus card (split tender).
type TenderLine = { tenderType: string; amountPence: number; tenderedPence: number; changeDuePence: number }
type OrderWithTenders = OrderView & { tenders: TenderLine[] }

test.describe('MBA-3937 cash tender', () => {
  test('exact cash records one cash tender line', async ({ till, backend, page }) => {
    await till.open()
    await till.quickKey('1006')
    await till.pay()
    await page.getByTestId('tender-cash').click()
    await page.getByTestId('cash-exact').click()
    await expect(page.getByTestId('cash-received')).toHaveText('£3.50')
    await expect(page.getByTestId('change-due')).toHaveText('£0.00')
    await page.getByTestId('take-cash').click()

    const orderNumber = await till.orderNumber()
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by cash · £3.50')
    await expect(page.getByTestId('give-change')).toContainText('£0.00')
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 350 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 350, tenderedPence: 350, changeDuePence: 0 }),
    ])
  })

  test('£20 note on keyed cash gives server-calculated change', async ({ till, backend, page }) => {
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1007')
    await till.pay()
    await expect(page.getByTestId('amount-due')).toHaveText('£8.25')
    await page.getByTestId('tender-cash').click()
    for (const key of ['5', '00']) await page.getByTestId(`cash-key-${key}`).click()
    await expect(page.getByTestId('cash-received')).toHaveText('£5.00')
    await expect(page.getByTestId('change-due')).toHaveText('—')
    await expect(page.getByTestId('remaining-after-cash')).toHaveText('£3.25')
    await page.getByTestId('cash-key-C').click()
    await page.getByTestId('cash-20').click()
    await expect(page.getByTestId('change-due')).toHaveText('£11.75')
    await expect(page.getByTestId('take-part-cash')).toHaveCount(0)
    await page.getByTestId('take-cash').click()

    const orderNumber = await till.orderNumber()
    await expect(page.getByTestId('give-change')).toContainText('£11.75')
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 825 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 825, tenderedPence: 2000, changeDuePence: 1175 }),
    ])
  })

  test('£5 cash then card pays the balance as a split tender @tablet', async ({ till, backend, page }) => {
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1007')
    await till.pay()
    await page.getByTestId('tender-cash').click()
    await page.getByTestId('cash-5').click()
    await expect(page.getByTestId('take-cash')).toBeDisabled()
    await expect(page.getByTestId('remaining-after-cash')).toHaveText('£3.25')
    await page.getByTestId('take-part-cash').click()

    await expect(page.getByTestId('amount-due')).toHaveText('£3.25')
    await expect(page.getByTestId('paid-so-far')).toHaveText('Paid £5.00 of £8.25')
    await expect(page.getByTestId('back-to-basket')).toBeDisabled()
    await till.payByCard('approved')

    const orderNumber = await till.orderNumber()
    await expect(page.getByTestId('sale-complete')).toContainText('Paid by cash and card · £8.25')
    await page.getByTestId('reprint-receipt').click()
    await expect(page.getByTestId('receipt-tender')).toHaveText([/Cash\s*£5\.00/i, /Card\s*£3\.25/i])
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'SPLIT', totalPence: 825 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 500, tenderedPence: 500 }),
      expect.objectContaining({ tenderType: 'CARD', amountPence: 325 }),
    ])
    await backend.notification(orderNumber)
  })
})
