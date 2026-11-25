import { expect, test } from '../fixtures/till'
import type { OrderView } from '../fixtures/backend'
import { CashTender } from '../pages/CashTender'

// MBA-3937: cash tender with server-side change, and part cash plus card (split tender).
type TenderLine = { tenderType: string; amountPence: number; tenderedPence: number; changeDuePence: number }
type OrderWithTenders = OrderView & { tenders: TenderLine[] }

test.describe('MBA-3937 cash tender', () => {
  test('exact cash records one cash tender line', async ({ till, backend, page }) => {
    const cash = new CashTender(page)
    await till.open()
    await till.quickKey('1006')
    await till.pay()
    await cash.choose()
    await cash.exact()
    await expect(cash.received).toHaveText('£3.50')
    await expect(cash.changeDue).toHaveText('£0.00')
    await cash.takeCash.click()

    const orderNumber = await till.orderNumber()
    await expect(cash.saleComplete).toContainText('Paid by cash · £3.50')
    await expect(cash.giveChange).toContainText('£0.00')
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 350 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 350, tenderedPence: 350, changeDuePence: 0 }),
    ])
  })

  test('£20 note on keyed cash gives server-calculated change', async ({ till, backend, page }) => {
    const cash = new CashTender(page)
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1007')
    await till.pay()
    await expect(cash.amountDue).toHaveText('£8.25')
    await cash.choose()
    await cash.key('5', '00')
    await expect(cash.received).toHaveText('£5.00')
    await expect(cash.changeDue).toHaveText('—')
    await expect(cash.remaining).toHaveText('£3.25')
    await cash.key('C')
    await cash.note(20)
    await expect(cash.changeDue).toHaveText('£11.75')
    await expect(cash.takePartCash).toHaveCount(0)
    await cash.takeCash.click()

    const orderNumber = await till.orderNumber()
    await expect(cash.giveChange).toContainText('£11.75')
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 825 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 825, tenderedPence: 2000, changeDuePence: 1175 }),
    ])
  })

  test('£5 cash then card pays the balance as a split tender @tablet', async ({ till, backend, page }) => {
    const cash = new CashTender(page)
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1007')
    await till.pay()
    await cash.choose()
    await cash.note(5)
    await expect(cash.takeCash).toBeDisabled()
    await expect(cash.remaining).toHaveText('£3.25')
    await cash.takePartCash.click()

    await expect(cash.amountDue).toHaveText('£3.25')
    await expect(cash.paidSoFar).toHaveText('Paid £5.00 of £8.25')
    await expect(cash.backToBasket).toBeDisabled()
    await till.payByCard('approved')

    const orderNumber = await till.orderNumber()
    await expect(cash.saleComplete).toContainText('Paid by cash and card · £8.25')
    await cash.showReceipt()
    await expect(cash.receiptTenders).toHaveText([/Cash\s*£5\.00/i, /Card\s*£3\.25/i])
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'SPLIT', totalPence: 825 })
    expect(order.tenders).toEqual([
      expect.objectContaining({ tenderType: 'CASH', amountPence: 500, tenderedPence: 500 }),
      expect.objectContaining({ tenderType: 'CARD', amountPence: 325 }),
    ])
    await backend.notification(orderNumber)
  })

  test('part cash then more cash stays a cash sale', async ({ till, backend, page }) => {
    const cash = new CashTender(page)
    await till.open()
    await till.quickKey('1006')
    await till.quickKey('1007')
    await till.pay()
    await cash.choose()
    await cash.note(5)
    await cash.takePartCash.click()
    await expect(cash.amountDue).toHaveText('£3.25')
    await cash.note(5)
    await expect(cash.changeDue).toHaveText('£1.75')
    await cash.takeCash.click()

    const orderNumber = await till.orderNumber()
    await expect(cash.saleComplete).toContainText('Paid by cash · £8.25')
    await expect(cash.giveChange).toContainText('£1.75')
    const order = (await backend.order(orderNumber)) as OrderWithTenders
    expect(order).toMatchObject({ tenderType: 'CASH', totalPence: 825 })
    expect(order.tenders).toHaveLength(2)
  })

  test('a part-paid sale can be voided from the payment screen', async ({ till, page }) => {
    const cash = new CashTender(page)
    await till.open()
    await till.quickKey('1006')
    await till.pay()
    await cash.choose()
    await cash.key('2', '00')
    await cash.takePartCash.click()
    await expect(cash.paidSoFar).toHaveText('Paid £2.00 of £3.50')
    await cash.voidSale.click()
    await expect(till.scanMessage).toHaveText('Sale voided. Return £2.00 cash')
    await expect(till.lines).toHaveCount(0)
  })
})
