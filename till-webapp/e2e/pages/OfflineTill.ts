import { expect, type Locator, type Page } from '@playwright/test'
import type { TillPage } from './TillPage'

/** MBA-3940 offline-mode controls and assertions; sits beside TillPage so specs never use raw selectors. */
export class OfflineTill {
  readonly page: Page
  readonly queued: Locator
  readonly saleComplete: Locator

  constructor(readonly till: TillPage) {
    this.page = till.page
    this.queued = this.page.getByTestId('offline-queue')
    this.saleComplete = this.page.getByTestId('sale-complete')
  }

  /** Cuts the till's network, as if the store lost its link to the backend. */
  async cutNetwork() {
    await this.page.context().setOffline(true)
  }

  async restoreNetwork() {
    await this.page.context().setOffline(false)
  }

  async expectOffline(timeout = 20_000) {
    await expect(this.till.backendStatus).toHaveText(/Offline/, { timeout })
  }

  async expectOnline(timeout = 30_000) {
    await expect(this.till.backendStatus).toHaveText(/Backend connected/, { timeout })
  }

  async expectQueued(count: number, timeout = 30_000) {
    if (count === 0) await expect(this.queued).toBeHidden({ timeout })
    else await expect(this.queued).toHaveText(`${count} queued`, { timeout })
  }

  async expectCardUnavailable() {
    await expect(this.page.getByTestId('card-offline-note')).toBeVisible()
    await expect(this.page.getByTestId('tender-card')).toBeDisabled()
    await expect(this.page.getByTestId('tender-contactless')).toBeDisabled()
    await expect(this.page.getByTestId('tender-cash')).toBeEnabled()
  }

  async expectCardAvailable() {
    await expect(this.page.getByTestId('card-offline-note')).toBeHidden()
    await expect(this.page.getByTestId('tender-card')).toBeEnabled()
  }

  /** Takes cash (a note, or exact when omitted) and expects the sale to be saved offline. */
  async payCashOffline(note?: 5 | 10 | 20 | 50) {
    await this.page.getByTestId('tender-cash').click()
    await this.page.getByTestId(note ? `cash-${note}` : 'cash-exact').click()
    await this.page.getByTestId('take-cash').click()
    await expect(this.saleComplete).toBeVisible()
    await expect(this.page.getByTestId('queued-offline')).toBeVisible()
    await expect(this.page.getByTestId('order-number')).toHaveCount(0)
  }

  async expectChange(amount: string) {
    await expect(this.page.getByTestId('give-change')).toContainText(amount)
  }

  async startNewSale() {
    await this.page.getByTestId('start-new-sale').click()
    await expect(this.page.getByTestId('pay')).toBeVisible()
  }
}
