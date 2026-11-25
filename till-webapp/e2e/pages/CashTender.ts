import { expect, type Locator, type Page } from '@playwright/test'

/** Cash tender panel and part-paid state (MBA-3937). */
export class CashTender {
  readonly received: Locator
  readonly changeDue: Locator
  readonly remaining: Locator
  readonly takeCash: Locator
  readonly takePartCash: Locator
  readonly amountDue: Locator
  readonly paidSoFar: Locator
  readonly backToBasket: Locator
  readonly voidSale: Locator
  readonly giveChange: Locator
  readonly saleComplete: Locator
  readonly receiptTenders: Locator

  constructor(readonly page: Page) {
    this.received = page.getByTestId('cash-received')
    this.changeDue = page.getByTestId('change-due')
    this.remaining = page.getByTestId('remaining-after-cash')
    this.takeCash = page.getByTestId('take-cash')
    this.takePartCash = page.getByTestId('take-part-cash')
    this.amountDue = page.getByTestId('amount-due')
    this.paidSoFar = page.getByTestId('paid-so-far')
    this.backToBasket = page.getByTestId('back-to-basket')
    this.voidSale = page.getByTestId('void-part-paid-sale')
    this.giveChange = page.getByTestId('give-change')
    this.saleComplete = page.getByTestId('sale-complete')
    this.receiptTenders = page.getByTestId('receipt-tender')
  }

  async choose() {
    await this.page.getByTestId('tender-cash').click()
    await expect(this.takeCash).toBeVisible()
  }

  async note(pounds: 5 | 10 | 20 | 50) {
    await this.page.getByTestId(`cash-${pounds}`).click()
  }

  async exact() {
    await this.page.getByTestId('cash-exact').click()
  }

  async key(...keys: string[]) {
    for (const key of keys) await this.page.getByTestId(`cash-key-${key}`).click()
  }

  async showReceipt() {
    await this.page.getByTestId('reprint-receipt').click()
  }
}
