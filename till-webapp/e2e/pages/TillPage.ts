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
    await this.settled()
  }

  async keyBarcode(barcode: string) {
    for (const digit of barcode) await this.page.getByTestId(`key-${digit}`).click()
    await this.page.getByTestId('enter').click()
    await this.settled()
  }

  async quickKey(barcode: string) {
    await this.page.getByTestId(`quickkey-${barcode}`).click()
    await this.settled()
  }

  /**
   * Waits for the till to finish the last scan. The till keeps its buttons disabled while a request is in flight
   * and selects the scanned line when the response lands, so acting before then (e.g. selecting another line)
   * races that response.
   */
  async settled() {
    await expect(this.page.getByTestId('enter')).toBeEnabled()
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

  async orderNumber() {
    await expect(this.page.getByTestId('sale-complete')).toBeVisible()
    return (await this.page.getByTestId('order-number').textContent())!.trim()
  }
}
