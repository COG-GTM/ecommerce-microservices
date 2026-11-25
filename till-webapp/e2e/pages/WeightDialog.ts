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

  /** Submits the weight and leaves the dialog open, for weights the till rejects. */
  async submitRejected() {
    await this.page.getByTestId('weight-confirm').click()
    await expect(this.error).toBeVisible()
    await expect(this.root).toBeVisible()
  }

  get input() {
    return this.page.getByTestId('weight-input')
  }

  get error() {
    return this.page.getByTestId('weight-error')
  }

  /** The control holding keyboard focus inside the dialog (none when focus escaped). */
  get focusedControl() {
    return this.root.locator(':focus')
  }

  async confirm() {
    await this.page.getByTestId('weight-confirm').click()
    await expect(this.root).toBeHidden()
    await expect(this.barcodeInput, 'ready for the next scan').toBeFocused()
  }

  /** The basket line's "0.452 kg × £2.20/kg" detail. */
  lineWeight(line: Locator) {
    return line.getByTestId('line-weight')
  }

  lineLabel(line: Locator) {
    return line.getByTestId('line-label')
  }

  get barcodeInput() {
    return this.page.getByTestId('barcode-input')
  }

  /** Reprints the receipt on the sale-complete screen and returns it. */
  async reprintReceipt() {
    await this.page.getByTestId('reprint-receipt').click()
    return this.page.getByTestId('receipt')
  }

  get qtyPlus() {
    return this.page.getByTestId('qty-plus')
  }

  async cancel() {
    await this.page.getByTestId('weight-cancel').click()
    await expect(this.root).toBeHidden()
    await expect(this.barcodeInput, 'ready for the next scan').toBeFocused()
  }
}
