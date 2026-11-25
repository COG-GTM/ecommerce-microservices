import { expect, type Locator, type Page } from '@playwright/test'

/** MBA-3932: per-line VAT code badges and the VAT summary (one row per rate band) on the basket and the receipt. */
export class VatSummaryPanel {
  readonly receiptTotal: Locator

  constructor(readonly page: Page) {
    this.receiptTotal = page.getByTestId('receipt-total')
  }

  lineVatCode(name: string) {
    return this.page
      .getByTestId('basket-line')
      .filter({ has: this.page.getByTestId('line-name').getByText(name, { exact: true }) })
      .getByTestId('line-vat-code')
  }

  band(where: 'basket' | 'receipt', code: 'Z' | 'S') {
    return this.page.getByTestId(`${where}-vat-summary`).locator(`[data-testid="vat-band"][data-vat-code="${code}"]`)
  }

  async expectBand(where: 'basket' | 'receipt', code: 'Z' | 'S', rate: string, net: string, vat: string, gross: string) {
    const band = this.band(where, code)
    await expect(band.getByTestId('vat-rate')).toHaveText(rate)
    await expect(band.getByTestId('vat-net')).toHaveText(net)
    await expect(band.getByTestId('vat-amount')).toHaveText(vat)
    await expect(band.getByTestId('vat-gross')).toHaveText(gross)
  }

  async showReceipt() {
    await this.page.getByTestId('reprint-receipt').click()
    await expect(this.page.getByTestId('receipt')).toBeVisible()
  }
}
