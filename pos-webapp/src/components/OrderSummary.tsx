import type { OrderTotals } from '../api/types';
import { formatCurrency } from '../lib/totals';
import styles from './OrderSummary.module.css';

export interface OrderSummaryProps {
  totals: OrderTotals;
  itemCount: number;
  onTaxExemptChange: (taxExempt: boolean) => void;
}

export function OrderSummary({ totals, itemCount, onTaxExemptChange }: OrderSummaryProps) {
  const taxRateLabel = `${(totals.taxRate * 100).toFixed(3)}%`;

  return (
    <section className={styles.panel} aria-label="Order summary">
      <h2 className={styles.heading}>Order summary</h2>
      <div className={styles.body}>
        <div className={styles.row}>
          <span className={styles.rowLabel}>Merchandise ({itemCount} items)</span>
          <span>{formatCurrency(totals.merchandiseTotal)}</span>
        </div>
        <div className={styles.row}>
          <span className={styles.rowLabel}>Services &amp; fees</span>
          <span>{formatCurrency(totals.servicesAndFees)}</span>
        </div>
        <div className={styles.row}>
          <span className={styles.rowLabel}>Discounts</span>
          <span className={styles.discount}>−{formatCurrency(totals.discountTotal)}</span>
        </div>

        <div className={styles.divider} />

        <div className={styles.row}>
          <span className={styles.rowLabel}>Taxable subtotal</span>
          <span>{formatCurrency(totals.taxableSubtotal)}</span>
        </div>
        <div className={styles.row}>
          <span className={styles.rowLabel}>
            Sales tax {taxRateLabel}
            {totals.taxExempt ? ' (exempt)' : ''}
          </span>
          <span>{formatCurrency(totals.salesTax)}</span>
        </div>

        <div className={styles.totalRow}>
          <span className={styles.totalLabel}>Total</span>
          <span className={styles.totalValue}>{formatCurrency(totals.total)}</span>
        </div>

        <div className={styles.saved}>
          You saved {formatCurrency(totals.savedToday)} today
        </div>

        <div className={styles.toggleRow}>
          <label className={styles.toggle} htmlFor="tax-exempt">
            <input
              id="tax-exempt"
              type="checkbox"
              checked={totals.taxExempt}
              onChange={(event) => onTaxExemptChange(event.target.checked)}
            />
            Tax exempt
          </label>
          <span>Certificate on file</span>
        </div>
      </div>
    </section>
  );
}
