import { describe, expect, it } from 'vitest';
import type { OrderLineItem, OrderTotals, Promotion } from '../api/types';
import { TAX_RATE } from '../api/config';
import { calculateTotals, roundCurrency, type TotalsInput } from './totals';

function line(overrides: Partial<OrderLineItem>): OrderLineItem {
  const listPrice = overrides.listPrice ?? 0;
  const unitPrice = overrides.unitPrice ?? listPrice;
  const quantity = overrides.quantity ?? 1;
  return {
    skuCode: 'TEST-SKU',
    styleId: 'TEST',
    description: 'Test item',
    department: "WOMEN'S",
    colorName: 'Black',
    colorCode: '#000000',
    size: 'M',
    quantity,
    listPrice,
    unitPrice,
    extendedPrice: unitPrice * quantity,
    discountReason: null,
    clearancePercent: 0,
    finalSale: false,
    inventory: {
      skuCode: 'TEST-SKU',
      storeId: '1969',
      onHand: 1,
      nearbyStores: [],
      shipFromStoreEligible: false,
      floor: 'Floor 1',
      fixture: 'Fixture A-01',
    },
    ...overrides,
  };
}

function promo(percentOff: number, amountOff = 0): Promotion {
  return { code: 'TEST', description: 'Test promotion', percentOff, amountOff };
}

type ExpectedTotals = Pick<
  OrderTotals,
  'merchandiseTotal' | 'servicesAndFees' | 'discountTotal' | 'taxableSubtotal' | 'salesTax' | 'total'
>;

interface TotalsCase {
  name: string;
  input: Partial<TotalsInput>;
  expected: ExpectedTotals;
}

function run({ input, expected }: TotalsCase) {
  const full: TotalsInput = {
    lineItems: [],
    promotions: [],
    servicesAndFees: 0,
    taxExempt: false,
    ...input,
  };
  expect(calculateTotals(full)).toEqual({
    ...expected,
    taxRate: full.taxRate ?? TAX_RATE,
    savedToday: expected.discountTotal,
    taxExempt: full.taxExempt,
  });
}

describe('calculateTotals', () => {
  describe('promotions skip final-sale lines', () => {
    it.each<TotalsCase>([
      {
        // eligible = 80 (final-sale 20 excluded); promo = 25% × 80 = 20
        // discount = markdown 20 + promo 20 = 40; taxable = 120 − 40 = 80; tax = 6.90
        name: 'percent promo applies only to the non-final-sale line',
        input: {
          lineItems: [
            line({ listPrice: 80, unitPrice: 80 }),
            line({ listPrice: 40, unitPrice: 20, finalSale: true }),
          ],
          promotions: [promo(25)],
        },
        expected: {
          merchandiseTotal: 120,
          servicesAndFees: 0,
          discountTotal: 40,
          taxableSubtotal: 80,
          salesTax: 6.9,
          total: 86.9,
        },
      },
      {
        // eligible = 0; promo = 0; discount = markdown 10; taxable = 40; tax = 3.45
        name: 'percent promo is a no-op when every line is final sale',
        input: {
          lineItems: [line({ listPrice: 50, unitPrice: 40, finalSale: true })],
          promotions: [promo(20)],
        },
        expected: {
          merchandiseTotal: 50,
          servicesAndFees: 0,
          discountTotal: 10,
          taxableSubtotal: 40,
          salesTax: 3.45,
          total: 43.45,
        },
      },
    ])('$name', (c) => run(c));
  });

  describe('amountOff promotions', () => {
    it.each<TotalsCase>([
      {
        // discount = 20; taxable = 80; tax = 6.90
        name: 'flat amount off a full-price line',
        input: {
          lineItems: [line({ listPrice: 100 })],
          promotions: [promo(0, 20)],
        },
        expected: {
          merchandiseTotal: 100,
          servicesAndFees: 0,
          discountTotal: 20,
          taxableSubtotal: 80,
          salesTax: 6.9,
          total: 86.9,
        },
      },
      {
        // promo = 10% × 100 + 10 = 20; taxable = 80; tax = 6.90
        name: 'percentOff and amountOff on the same promotion add together',
        input: {
          lineItems: [line({ listPrice: 100 })],
          promotions: [promo(10, 10)],
        },
        expected: {
          merchandiseTotal: 100,
          servicesAndFees: 0,
          discountTotal: 20,
          taxableSubtotal: 80,
          salesTax: 6.9,
          total: 86.9,
        },
      },
      {
        // eligible = 60 → 10% = 6, plus 5 flat = 11; discount = markdown 20 + 11 = 31
        // taxable = 120 − 31 = 89; tax = 7.67625 → 7.68
        name: 'amountOff alongside a markdown and a final-sale line',
        input: {
          lineItems: [
            line({ listPrice: 60 }),
            line({ listPrice: 60, unitPrice: 40, finalSale: true }),
          ],
          promotions: [promo(10, 5)],
        },
        expected: {
          merchandiseTotal: 120,
          servicesAndFees: 0,
          discountTotal: 31,
          taxableSubtotal: 89,
          salesTax: 7.68,
          total: 96.68,
        },
      },
    ])('$name', (c) => run(c));

    it.todo(
      'amountOff on a bag of only final-sale lines — currently still discounts, contradicting "promotions apply only to line items that are not final sale"',
    );
    it.todo(
      'amountOff larger than the bag — currently drives taxableSubtotal, salesTax and total negative (no floor at $0)',
    );
  });

  describe('stacking percentage promotions', () => {
    it.each<TotalsCase>([
      {
        // percentages stack additively on the same base: 10% × 160 + 20% × 160 = 16 + 32 = 48
        // taxable = 112; tax = 9.66
        name: 'two percent promos are summed against the same eligible base',
        input: {
          lineItems: [line({ listPrice: 160 })],
          promotions: [promo(10), promo(20)],
        },
        expected: {
          merchandiseTotal: 160,
          servicesAndFees: 0,
          discountTotal: 48,
          taxableSubtotal: 112,
          salesTax: 9.66,
          total: 121.66,
        },
      },
      {
        // eligible = 100 (final-sale 50 excluded) → 15 + 5 = 20; discount = markdown 25 + 20 = 45
        // taxable = 175 − 45 = 130; tax = 11.2125 → 11.21
        name: 'stacked percent promos still skip final-sale lines',
        input: {
          lineItems: [
            line({ listPrice: 100 }),
            line({ listPrice: 75, unitPrice: 50, finalSale: true }),
          ],
          promotions: [promo(15), promo(5)],
        },
        expected: {
          merchandiseTotal: 175,
          servicesAndFees: 0,
          discountTotal: 45,
          taxableSubtotal: 130,
          salesTax: 11.21,
          total: 141.21,
        },
      },
    ])('$name', (c) => run(c));
  });

  describe('tax exempt with services and fees', () => {
    it.each<TotalsCase>([
      {
        // taxable = 50 − 5 + 7.50 = 52.50; exempt → tax 0
        name: 'fees are added to the subtotal and no tax is charged',
        input: {
          lineItems: [line({ listPrice: 50, unitPrice: 45 })],
          servicesAndFees: 7.5,
          taxExempt: true,
        },
        expected: {
          merchandiseTotal: 50,
          servicesAndFees: 7.5,
          discountTotal: 5,
          taxableSubtotal: 52.5,
          salesTax: 0,
          total: 52.5,
        },
      },
      {
        // same bag, not exempt: tax = 52.50 × 0.08625 = 4.528125 → 4.53
        name: 'the same bag is taxed on fees when not exempt',
        input: {
          lineItems: [line({ listPrice: 50, unitPrice: 45 })],
          servicesAndFees: 7.5,
          taxExempt: false,
        },
        expected: {
          merchandiseTotal: 50,
          servicesAndFees: 7.5,
          discountTotal: 5,
          taxableSubtotal: 52.5,
          salesTax: 4.53,
          total: 57.03,
        },
      },
      {
        // promo = 10% × 100 = 10; taxable = 100 − 10 + 4.99 = 94.99; exempt
        name: 'exempt with a promotion and fees',
        input: {
          lineItems: [line({ listPrice: 100 })],
          promotions: [promo(10)],
          servicesAndFees: 4.99,
          taxExempt: true,
        },
        expected: {
          merchandiseTotal: 100,
          servicesAndFees: 4.99,
          discountTotal: 10,
          taxableSubtotal: 94.99,
          salesTax: 0,
          total: 94.99,
        },
      },
    ])('$name', (c) => run(c));
  });

  describe('empty bag', () => {
    const zero: ExpectedTotals = {
      merchandiseTotal: 0,
      servicesAndFees: 0,
      discountTotal: 0,
      taxableSubtotal: 0,
      salesTax: 0,
      total: 0,
    };

    it.each<TotalsCase>([
      { name: 'no items, no promotions, no fees', input: {}, expected: zero },
      { name: 'percent promo on an empty bag is zero', input: { promotions: [promo(20)] }, expected: zero },
      { name: 'empty bag, tax exempt', input: { taxExempt: true }, expected: zero },
      {
        // fees only: 5.00 × 0.08625 = 0.43125 → 0.43
        name: 'fees only are still taxed',
        input: { servicesAndFees: 5 },
        expected: { ...zero, servicesAndFees: 5, taxableSubtotal: 5, salesTax: 0.43, total: 5.43 },
      },
    ])('$name', (c) => run(c));
  });

  describe('rounding at 8.625% on cent boundaries', () => {
    it.each<TotalsCase>([
      {
        // 100 × 0.08625 = 8.625 → half-cent rounds up to 8.63
        name: 'half-cent tax on $100.00 rounds up',
        input: { lineItems: [line({ listPrice: 100 })] },
        expected: {
          merchandiseTotal: 100,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 100,
          salesTax: 8.63,
          total: 108.63,
        },
      },
      {
        // 20 × 0.08625 = 1.725 → 1.73
        name: 'half-cent tax on $20.00 rounds up',
        input: { lineItems: [line({ listPrice: 20 })] },
        expected: {
          merchandiseTotal: 20,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 20,
          salesTax: 1.73,
          total: 21.73,
        },
      },
      {
        // 10 × 0.08625 = 0.8625 → 0.86
        name: 'sub-half-cent remainder on $10.00 rounds down',
        input: { lineItems: [line({ listPrice: 10 })] },
        expected: {
          merchandiseTotal: 10,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 10,
          salesTax: 0.86,
          total: 10.86,
        },
      },
      {
        // 1 × 0.08625 = 0.08625 → 0.09
        name: 'tax on $1.00 rounds up to 9 cents',
        input: { lineItems: [line({ listPrice: 1 })] },
        expected: {
          merchandiseTotal: 1,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 1,
          salesTax: 0.09,
          total: 1.09,
        },
      },
      {
        // 19.99 × 0.08625 = 1.7241375 → 1.72
        name: 'tax on $19.99 rounds down',
        input: { lineItems: [line({ listPrice: 19.99 })] },
        expected: {
          merchandiseTotal: 19.99,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 19.99,
          salesTax: 1.72,
          total: 21.71,
        },
      },
      {
        // 3 × 33.33 = 99.99 (float 99.99000000000001 → 99.99); tax = 8.6241375 → 8.62
        name: 'float drift in quantity × price is rounded off',
        input: { lineItems: [line({ listPrice: 33.33, quantity: 3 })] },
        expected: {
          merchandiseTotal: 99.99,
          servicesAndFees: 0,
          discountTotal: 0,
          taxableSubtotal: 99.99,
          salesTax: 8.62,
          total: 108.61,
        },
      },
    ])('$name', (c) => run(c));

    it.each([
      [1.005, 1.01],
      [2.675, 2.68],
      [8.625, 8.63],
      [0.8625, 0.86],
      [0.1 + 0.2, 0.3],
      [0, 0],
    ])('roundCurrency(%d) → %d', (value, expected) => {
      expect(roundCurrency(value)).toBe(expected);
    });
  });

  describe('quantity > 1 with markdowns', () => {
    it.each<TotalsCase>([
      {
        // merch = 3 × 49.95 = 149.85; markdown = 3 × 19.98 = 59.94
        // taxable = 89.91; tax = 7.7547375 → 7.75
        name: 'markdown is multiplied by quantity',
        input: { lineItems: [line({ listPrice: 49.95, unitPrice: 29.97, quantity: 3 })] },
        expected: {
          merchandiseTotal: 149.85,
          servicesAndFees: 0,
          discountTotal: 59.94,
          taxableSubtotal: 89.91,
          salesTax: 7.75,
          total: 97.66,
        },
      },
      {
        // eligible = 3 × 29.97 = 89.91 → 10% = 8.991; discount = 59.94 + 8.991 = 68.931 → 68.93
        // taxable = 149.85 − 68.93 = 80.92; tax = 6.97935 → 6.98
        name: 'percent promo is taken off the marked-down price × quantity',
        input: {
          lineItems: [line({ listPrice: 49.95, unitPrice: 29.97, quantity: 3 })],
          promotions: [promo(10)],
        },
        expected: {
          merchandiseTotal: 149.85,
          servicesAndFees: 0,
          discountTotal: 68.93,
          taxableSubtotal: 80.92,
          salesTax: 6.98,
          total: 87.9,
        },
      },
      {
        // merch = 2 × 29.95 + 2 × 59.95 = 179.80; markdown = 2 × 11.98 + 2 × 17.98 = 59.92
        // eligible = 2 × 41.97 = 83.94 (tee is final sale) → 20% = 16.788
        // discount = 59.92 + 16.788 = 76.708 → 76.71; taxable = 103.09; tax = 8.8915125 → 8.89
        name: 'mixed quantities with a final-sale markdown and a promo',
        input: {
          lineItems: [
            line({ listPrice: 29.95, unitPrice: 17.97, quantity: 2, finalSale: true }),
            line({ listPrice: 59.95, unitPrice: 41.97, quantity: 2 }),
          ],
          promotions: [promo(20)],
        },
        expected: {
          merchandiseTotal: 179.8,
          servicesAndFees: 0,
          discountTotal: 76.71,
          taxableSubtotal: 103.09,
          salesTax: 8.89,
          total: 111.98,
        },
      },
    ])('$name', (c) => run(c));
  });
});
