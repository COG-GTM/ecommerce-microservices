import type { OrderLineItem, OrderTotals, Promotion } from '../api/types';
import { TAX_RATE } from '../api/config';

export function roundCurrency(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function formatCurrency(value: number): string {
  return `$${roundCurrency(value).toFixed(2)}`;
}

export interface TotalsInput {
  lineItems: OrderLineItem[];
  promotions: Promotion[];
  servicesAndFees: number;
  taxExempt: boolean;
  taxRate?: number;
}

/**
 * Merchandise is valued at list price; markdowns and promotions are
 * accumulated into a single discount total. Promotions apply only to
 * line items that are not final sale.
 */
export function calculateTotals({
  lineItems,
  promotions,
  servicesAndFees,
  taxExempt,
  taxRate = TAX_RATE,
}: TotalsInput): OrderTotals {
  const merchandiseTotal = roundCurrency(
    lineItems.reduce((sum, item) => sum + item.listPrice * item.quantity, 0),
  );

  const markdownTotal = lineItems.reduce(
    (sum, item) => sum + (item.listPrice - item.unitPrice) * item.quantity,
    0,
  );

  const promotionEligible = lineItems.reduce(
    (sum, item) => (item.finalSale ? sum : sum + item.unitPrice * item.quantity),
    0,
  );

  const promotionTotal = promotions.reduce(
    (sum, promo) => sum + (promotionEligible * promo.percentOff) / 100 + promo.amountOff,
    0,
  );

  const discountTotal = roundCurrency(markdownTotal + promotionTotal);
  const taxableSubtotal = roundCurrency(merchandiseTotal - discountTotal + servicesAndFees);
  const salesTax = taxExempt ? 0 : roundCurrency(taxableSubtotal * taxRate);
  const total = roundCurrency(taxableSubtotal + salesTax);

  return {
    merchandiseTotal,
    servicesAndFees: roundCurrency(servicesAndFees),
    discountTotal,
    taxableSubtotal,
    taxRate,
    salesTax,
    total,
    savedToday: discountTotal,
    taxExempt,
  };
}
