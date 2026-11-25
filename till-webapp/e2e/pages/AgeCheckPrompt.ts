import { expect, type Locator, type Page } from '@playwright/test'

/** MBA-3936: page object for the Challenge 25 prompt and age badges on basket lines. */
export class AgeCheckPrompt {
  readonly dialog: Locator
  readonly item: Locator

  constructor(readonly page: Page) {
    this.dialog = page.getByTestId('age-check-prompt')
    this.item = page.getByTestId('age-check-item')
  }

  async expectShownFor(name: string, minimumAge: number) {
    await expect(this.dialog).toBeVisible()
    await expect(this.dialog).toContainText('Challenge 25')
    await expect(this.item).toHaveText(`${name} · must be ${minimumAge} or over`)
  }

  async approve() {
    await this.page.getByTestId('age-approve').click()
    await expect(this.dialog).toBeHidden()
  }

  async refuse() {
    await this.page.getByTestId('age-refuse').click()
    await expect(this.dialog).toBeHidden()
  }

  async later() {
    await this.page.getByTestId('age-later').click()
    await expect(this.dialog).toBeHidden()
  }

  badge(line: Locator) {
    return line.getByTestId('line-age')
  }
}
