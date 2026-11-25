import { expect, type Locator } from '@playwright/test'
import type { TillPage } from './TillPage'

/** Savings lines that the promotion engine (MBA-3934) adds under qualifying basket lines. */
export class Promotions {
  readonly savingLines: Locator

  constructor(readonly till: TillPage) {
    this.savingLines = till.page.getByTestId('saving-line')
  }

  /** The saving line(s) rendered directly after the named basket line. */
  under(name: string) {
    return this.till.line(name).locator('xpath=following-sibling::tr[1][@data-testid="saving-line"]')
  }

  async voidSelectedLine() {
    await this.till.page.getByTestId('void-line').click()
  }

  async changeSelectedQty(by: number) {
    const button = this.till.page.getByTestId(by < 0 ? 'qty-minus' : 'qty-plus')
    for (let i = 0; i < Math.abs(by); i++) await button.click()
  }

  async expectSaleComplete() {
    await expect(this.till.page.getByTestId('sale-complete')).toBeVisible()
  }

  async expectSavingUnder(name: string, label: string, amount: string) {
    const saving = this.under(name)
    await expect(saving.getByTestId('saving-label')).toHaveText(label)
    await expect(saving.getByTestId('saving-amount')).toHaveText(amount)
  }
}
