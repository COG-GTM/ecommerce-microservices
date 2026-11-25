import { expect, type Locator, type Page } from '@playwright/test'

/** Page object for the till. Future tickets add methods here (or a sibling page object) rather than raw selectors in specs. */
export class TillPage {
  readonly lines: Locator
  readonly total: Locator
  readonly subtotal: Locator
  readonly savings: Locator
  readonly scanMessage: Locator
  readonly backendStatus: Locator

  constructor(readonly page: Page, readonly tillId: string) {
    this.lines = page.getByTestId('basket-line')
    this.total = page.getByTestId('total')
    this.subtotal = page.getByTestId('subtotal')
    this.savings = page.getByTestId('savings')
    this.scanMessage = page.getByTestId('scan-message')
    this.backendStatus = page.getByTestId('backend-status')
  }

  async open(query = '') {
    await this.page.goto(`/?till=${this.tillId}${query ? '&' + query : ''}`)
    await expect(this.backendStatus).toHaveText(/Backend connected/)
    await expect(this.page.getByTestId('quickkey-1001')).toBeVisible()
  }

  line(name: string) {
    return this.lines.filter({ has: this.page.getByTestId('line-name').getByText(name, { exact: true }) })
  }

  async scan(barcode: string) {
    await this.page.getByTestId('barcode-input').fill(barcode)
    await this.page.getByTestId('enter').click()
  }

  async keyBarcode(barcode: string) {
    for (const digit of barcode) await this.page.getByTestId(`key-${digit}`).click()
    await this.page.getByTestId('enter').click()
  }

  async quickKey(barcode: string) {
    await this.page.getByTestId(`quickkey-${barcode}`).click()
  }

  async expectLine(name: string, qty: number, each: string, total: string) {
    const line = this.line(name)
    await expect(line.getByTestId('line-qty')).toHaveText(String(qty))
    await expect(line.getByTestId('line-each')).toHaveText(each)
    await expect(line.getByTestId('line-total')).toHaveText(total)
  }

  async selectLine(name: string) {
    await this.line(name).click()
  }

  async pay() {
    await this.page.getByTestId('pay').click()
    await expect(this.page.getByTestId('amount-due')).toBeVisible()
  }

  async payByCard(outcome: 'approved' | 'declined' | 'timeout', method: 'card' | 'contactless' = 'card') {
    await this.page.getByTestId(`tender-${method}`).click()
    await expect(this.page.getByTestId('pinpad-wait')).toBeVisible()
    await this.page.getByTestId(`simulate-${outcome}`).click()
  }

  /** MBA-3932: VAT summary rows (one per rate band) on the basket or the reprinted receipt. */
  vatBand(where: 'basket' | 'receipt', code: 'Z' | 'S') {
    return this.page.getByTestId(`${where}-vat-summary`).locator(`[data-testid="vat-band"][data-vat-code="${code}"]`)
  }

  async expectVatBand(where: 'basket' | 'receipt', code: 'Z' | 'S', rate: string, net: string, vat: string, gross: string) {
    const band = this.vatBand(where, code)
    await expect(band.getByTestId('vat-rate')).toHaveText(rate)
    await expect(band.getByTestId('vat-net')).toHaveText(net)
    await expect(band.getByTestId('vat-amount')).toHaveText(vat)
    await expect(band.getByTestId('vat-gross')).toHaveText(gross)
  }

  async showReceipt() {
    await this.page.getByTestId('reprint-receipt').click()
    await expect(this.page.getByTestId('receipt')).toBeVisible()
  }

  async orderNumber() {
    await expect(this.page.getByTestId('sale-complete')).toBeVisible()
    return (await this.page.getByTestId('order-number').textContent())!.trim()
  }
}
