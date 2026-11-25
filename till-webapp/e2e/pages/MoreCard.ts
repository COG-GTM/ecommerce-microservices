import { expect, type Locator, type Page } from '@playwright/test'

/** Fictional seeded test cards (till-service SeededMoreCardDirectory). */
export const MORE_CARD = { member: '9826000000000001', unknown: '9826999999999999' }

/** More Card loyalty: header member chip, points on the sale-complete screen and receipt. */
export class MoreCard {
  readonly member: Locator
  readonly pointsEarned: Locator
  readonly receiptMoreCard: Locator
  readonly receiptPoints: Locator

  constructor(readonly page: Page) {
    this.member = page.getByTestId('more-card-member')
    this.pointsEarned = page.getByTestId('points-earned')
    this.receiptMoreCard = page.getByTestId('receipt-more-card')
    this.receiptPoints = page.getByTestId('receipt-points')
  }

  async expectMember(name: string, last4: string) {
    await expect(this.member).toHaveText(`More Card · ${name} ····${last4}`)
  }

  async showReceipt() {
    await this.page.getByTestId('reprint-receipt').click()
    await expect(this.page.getByTestId('receipt')).toBeVisible()
  }
}
