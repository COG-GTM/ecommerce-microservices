import { expect, type Locator, type Page } from '@playwright/test'

/** Refund mode on the till (MBA-3939). */
export class RefundPage {
  readonly error: Locator
  readonly total: Locator

  constructor(readonly page: Page) {
    this.error = page.getByTestId('refund-error')
    this.total = page.getByTestId('refund-total')
  }

  async open() {
    await this.page.getByTestId('refund-mode').click()
    await expect(this.page.getByTestId('refund-panel')).toBeVisible()
  }

  async find(reference: string) {
    await this.page.getByTestId('refund-reference').fill(reference)
    await this.page.getByTestId('refund-lookup').click()
    await expect(this.page.getByTestId('refund-sale')).toBeVisible()
  }

  line(name: string) {
    return this.page.getByTestId('refund-line').filter({ has: this.page.getByTestId('refund-line-name').getByText(name, { exact: true }) })
  }

  async expectLine(name: string, sold: number, refunded: number, left: number) {
    const line = this.line(name)
    await expect(line.getByTestId('refund-line-sold')).toHaveText(String(sold))
    await expect(line.getByTestId('refund-line-refunded')).toHaveText(String(refunded))
    await expect(line.getByTestId('refund-line-left')).toHaveText(String(left))
  }

  async select(name: string, quantity: number) {
    const line = this.line(name)
    for (let i = 0; i < quantity; i++) await line.getByTestId('refund-qty-plus').click()
    await expect(line.getByTestId('refund-qty')).toHaveText(String(quantity))
  }

  plus(name: string) {
    return this.line(name).getByTestId('refund-qty-plus')
  }

  async submit() {
    await this.page.getByTestId('refund-submit').click()
  }

  /** @returns the refund order number once the refund completes */
  async completed(amount: string) {
    await expect(this.page.getByTestId('refund-complete')).toBeVisible()
    await expect(this.page.getByTestId('refund-amount')).toHaveText('−' + amount)
    return (await this.page.getByTestId('refund-number').textContent())!.trim()
  }

  async refundAgain() {
    await this.page.getByTestId('refund-again').click()
    await expect(this.page.getByTestId('refund-sale')).toBeVisible()
  }
}
