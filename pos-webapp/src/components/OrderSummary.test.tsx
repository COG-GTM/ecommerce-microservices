import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MOCK_LINE_ITEMS, MOCK_PROMOTIONS, MOCK_SERVICES_AND_FEES } from '../api/fixtures';
import { calculateTotals, roundCurrency } from '../lib/totals';
import { OrderSummary } from './OrderSummary';

function totalsFor(taxExempt: boolean) {
  return calculateTotals({
    lineItems: MOCK_LINE_ITEMS,
    promotions: MOCK_PROMOTIONS,
    servicesAndFees: MOCK_SERVICES_AND_FEES,
    taxExempt,
  });
}

describe('order total math', () => {
  it('computes merchandise − discounts + fees + tax as the total', () => {
    const totals = totalsFor(false);

    expect(totals.taxableSubtotal).toBe(
      roundCurrency(totals.merchandiseTotal - totals.discountTotal + totals.servicesAndFees),
    );
    expect(totals.salesTax).toBe(roundCurrency(totals.taxableSubtotal * totals.taxRate));
    expect(totals.total).toBe(roundCurrency(totals.taxableSubtotal + totals.salesTax));
    expect(totals.savedToday).toBe(totals.discountTotal);
  });

  it('drops sales tax when the transaction is tax exempt', () => {
    const exempt = totalsFor(true);

    expect(exempt.salesTax).toBe(0);
    expect(exempt.total).toBe(exempt.taxableSubtotal);
    expect(exempt.total).toBeLessThan(totalsFor(false).total);
  });
});

describe('OrderSummary', () => {
  it('renders the summary lines at the 8.625% tax rate', () => {
    const totals = totalsFor(false);
    render(
      <OrderSummary totals={totals} itemCount={3} onTaxExemptChange={vi.fn()} />,
    );

    expect(screen.getByText('Sales tax 8.625%')).toBeTruthy();
    expect(screen.getByText('Merchandise (3 items)')).toBeTruthy();
    expect(screen.getByText(`$${totals.total.toFixed(2)}`)).toBeTruthy();
    expect(
      screen.getByText(`You saved $${totals.savedToday.toFixed(2)} today`),
    ).toBeTruthy();
  });
});
