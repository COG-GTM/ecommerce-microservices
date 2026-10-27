import { expect, test, type Page } from '@playwright/test';
import { IN_STOCK, OUT_OF_STOCK } from '../catalog';

function productCard(page: Page, name: string) {
  return page.getByTestId('product-card').filter({ has: page.getByRole('heading', { name, exact: true }) }).first();
}

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(productCard(page, IN_STOCK.name)).toBeVisible();
});

test('browse products → add to cart → checkout → order confirmation', async ({ page }) => {
  await test.step('browse the catalog served by product-service', async () => {
    await expect(productCard(page, IN_STOCK.name)).toContainText('$799.99');
    await expect(productCard(page, OUT_OF_STOCK.name)).toContainText('$999.00');
  });

  await test.step('add two units to the cart', async () => {
    await productCard(page, IN_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
    await productCard(page, IN_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
    await expect(page.getByTestId('cart-badge')).toHaveText('Cart (2)');
    await expect(page.getByTestId('cart-row')).toHaveCount(1);
    await expect(page.getByTestId('line-total')).toHaveText('$1599.98');
    await expect(page.getByTestId('cart-total')).toHaveText('$1599.98');
  });

  await test.step('checkout through api-gateway → order-service → inventory-service', async () => {
    await page.getByRole('button', { name: 'Checkout' }).click();
  });

  await test.step('order confirmation is shown and the cart is cleared', async () => {
    const confirmation = page.getByTestId('order-confirmation');
    await expect(confirmation).toBeVisible();
    await expect(confirmation).toContainText('Order Placed Successfully!');
    await expect(confirmation).toContainText(`2 × ${IN_STOCK.name}`);
    await expect(page.locator('#confirmation-total')).toHaveText('$1599.98');
    await expect(page.getByTestId('cart-badge')).toHaveText('Cart (0)');
    await expect(page.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  });
});

test('cart quantity edits and removals recalculate totals', async ({ page }) => {
  await productCard(page, IN_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
  await productCard(page, OUT_OF_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
  await expect(page.getByTestId('cart-total')).toHaveText('$1798.99');

  await page.getByLabel(`Quantity for ${IN_STOCK.name}`, { exact: true }).fill('3');
  await page.getByLabel(`Quantity for ${IN_STOCK.name}`, { exact: true }).press('Tab');
  await expect(page.getByTestId('cart-badge')).toHaveText('Cart (4)');
  await expect(page.getByTestId('cart-total')).toHaveText('$3398.97');

  await page.getByRole('button', { name: `Remove ${OUT_OF_STOCK.name}` }).click();
  await expect(page.getByTestId('cart-row')).toHaveCount(1);
  await expect(page.getByTestId('cart-total')).toHaveText('$2399.97');

  const quantity = page.getByLabel(`Quantity for ${IN_STOCK.name}`, { exact: true });
  await quantity.fill('');
  await quantity.press('Tab');
  await expect(quantity).toHaveValue('3');
  await expect(page.getByTestId('cart-total')).toHaveText('$2399.97');
});

test('a failed checkout after a successful order hides the earlier confirmation', async ({ page }) => {
  await productCard(page, IN_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
  await page.getByRole('button', { name: 'Checkout' }).click();
  await expect(page.getByTestId('order-confirmation')).toBeVisible();

  await productCard(page, OUT_OF_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
  await page.getByRole('button', { name: 'Checkout' }).click();
  await expect(page.getByTestId('checkout-error')).toBeVisible();
  await expect(page.getByTestId('order-confirmation')).toBeHidden();
});

test('checkout of an out-of-stock item is rejected and the cart is kept', async ({ page }) => {
  await productCard(page, OUT_OF_STOCK.name).getByRole('button', { name: /add .* to cart/i }).click();
  await page.getByRole('button', { name: 'Checkout' }).click();

  await expect(page.getByTestId('checkout-error')).toContainText('Checkout failed: Oops! Something went wrong');
  await expect(page.getByTestId('order-confirmation')).toBeHidden();
  await expect(page.getByTestId('cart-badge')).toHaveText('Cart (1)');
  await expect(page.getByRole('button', { name: 'Checkout' })).toBeEnabled();
});
