import { expect, type Locator, type Page } from '@playwright/test'

/** Manual weight prompt shown for loose items without a label (MBA-3935). */
export class WeightDialog {
  readonly root: Locator
  readonly item: Locator
  readonly price: Locator
  readonly preview: Locator

  constructor(readonly page: Page) {
    this.root = page.getByTestId('weight-dialog')
    this.item = page.getByTestId('weight-item')
    this.price = page.getByTestId('weight-price')
    this.preview = page.getByTestId('weight-preview')
  }

  async keyWeight(kg: string) {
    await expect(this.root).toBeVisible()
    for (const key of kg) await this.page.getByTestId(`weight-key-${key}`).click()
  }

  async confirm() {
    await this.page.getByTestId('weight-confirm').click()
    await expect(this.root).toBeHidden()
  }

  async cancel() {
    await this.page.getByTestId('weight-cancel').click()
    await expect(this.root).toBeHidden()
  }
}
