import { expect, test } from '../fixtures/till'

test.describe('basket', () => {
  test.beforeEach(async ({ till }) => {
    await till.open()
  })

  test('scan by barcode adds a line with server-priced totals', async ({ till }) => {
    await till.scan('1005')
    await expect(till.scanMessage).toHaveText('Added Orange juice 1L')
    await till.expectLine('Orange juice 1L', 1, '£1.60', '£1.60')
    await expect(till.total).toHaveText('£1.60')
    await expect(till.line('Orange juice 1L')).toHaveClass(/selected/)
  })

  test('barcode keyed on the keypad', async ({ till }) => {
    await till.keyBarcode('1006')
    await till.expectLine('Mature cheddar 400g', 1, '£3.50', '£3.50')
  })

  test('unknown barcode is rejected and the basket is unchanged', async ({ till }) => {
    await till.scan('9999')
    await expect(till.scanMessage).toHaveText('No product for barcode 9999')
    await expect(till.lines).toHaveCount(0)
  })

  test('quick keys add items and repeat presses increase quantity @tablet', async ({ till }) => {
    await till.quickKey('1001')
    await till.quickKey('1004')
    await till.quickKey('1004')
    await expect(till.lines).toHaveCount(2)
    await till.expectLine('Bananas x5', 2, '£0.95', '£1.90')
    await expect(till.line('Bananas x5')).toHaveClass(/selected/)
    await expect(till.subtotal).toHaveText('£3.45')
    await expect(till.savings).toHaveText('£0.00')
    await expect(till.total).toHaveText('£3.45')
    await expect(till.page.getByTestId('pay')).toContainText('£3.45')
  })

  test('qty +1 / -1 changes the selected line', async ({ till, page }) => {
    await till.quickKey('1007')
    await till.quickKey('1008')
    await till.selectLine('Chicken breast fillets 600g')
    await page.getByTestId('qty-plus').click()
    await page.getByTestId('qty-plus').click()
    await till.expectLine('Chicken breast fillets 600g', 3, '£4.75', '£14.25')
    await page.getByTestId('qty-minus').click()
    await till.expectLine('Chicken breast fillets 600g', 2, '£4.75', '£9.50')
    await expect(till.total).toHaveText('£10.15')
  })

  test('void line removes only the selected line', async ({ till, page }) => {
    await till.quickKey('1002')
    await till.quickKey('1003')
    await till.selectLine('Wholemeal bread 800g')
    await page.getByTestId('void-line').click()
    await expect(till.lines).toHaveCount(1)
    await expect(till.line('Wholemeal bread 800g')).toHaveCount(0)
    await expect(till.total).toHaveText('£1.85')
  })

  test('void sale clears the basket and starts a new transaction', async ({ till, page }) => {
    await till.quickKey('1001')
    await till.quickKey('1006')
    const before = await page.getByTestId('transaction-number').textContent()
    await page.getByTestId('void-sale').click()
    await expect(till.lines).toHaveCount(0)
    await expect(till.total).toHaveText('£0.00')
    await expect(page.getByTestId('transaction-number')).not.toHaveText(before!)
    await expect(page.getByTestId('pay')).toBeDisabled()
  })

  test('API chips are hidden by default and shown with ?api=1', async ({ till, page }) => {
    await expect(page.getByTestId('api-chip')).toHaveCount(0)
    await till.open('api=1')
    await expect(page.getByText('GET /api/till/session')).toBeVisible()
    await expect(page.getByText('GET /api/till/products?quickkey=true')).toBeVisible()
  })
})
