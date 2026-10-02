import type { OrderLineItem } from '../api/types';
import { formatCurrency } from '../lib/totals';
import styles from './CartLineItem.module.css';

export interface CartLineItemProps {
  item: OrderLineItem;
  onRemove: (skuCode: string) => void;
}

export function CartLineItem({ item, onRemove }: CartLineItemProps) {
  const { inventory } = item;
  const nearbyCount = inventory.nearbyStores.length;
  const nearbyRadius = inventory.nearbyStores.reduce(
    (max, store) => Math.max(max, Math.ceil(store.distanceMiles)),
    0,
  );

  return (
    <article className={styles.item}>
      <div className={styles.swatchColumn}>
        <span
          className={styles.swatch}
          style={{ background: item.colorCode }}
          aria-hidden="true"
        />
        <span className={styles.qty}>Qty {item.quantity}</span>
      </div>

      <div className={styles.details}>
        <div className={styles.department}>{item.department}</div>
        <h3 className={styles.description}>{item.description}</h3>
        <div className={styles.attributes}>
          {item.colorName} · Size {item.size}
        </div>
        <div className={styles.sku}>SKU {item.skuCode}</div>

        <div className={styles.priceRow}>
          {item.listPrice !== item.unitPrice ? (
            <>
              <span className={styles.listPrice}>{formatCurrency(item.listPrice)}</span>
              <span className={styles.arrow}>→</span>
              <span className={styles.salePrice}>{formatCurrency(item.unitPrice)}</span>
              {item.clearancePercent > 0 && (
                <span className={styles.clearance}>{item.clearancePercent}% off</span>
              )}
            </>
          ) : (
            <span className={styles.salePrice}>{formatCurrency(item.unitPrice)}</span>
          )}
          {item.finalSale && <span className={styles.finalSale}>Final sale</span>}
        </div>

        <div className={styles.availability}>
          <span>On hand, this store: {inventory.onHand}</span>
          <span>
            Nearby stores: {nearbyCount} within {nearbyRadius} mi
          </span>
          <span>
            Ship from store:{' '}
            <span
              className={inventory.shipFromStoreEligible ? styles.eligible : styles.ineligible}
            >
              {inventory.shipFromStoreEligible ? 'Eligible' : 'Not eligible'}
            </span>
          </span>
          <span>
            {inventory.floor} · {inventory.fixture}
          </span>
        </div>
      </div>

      <div className={styles.amountColumn}>
        <div className={styles.extended}>{formatCurrency(item.extendedPrice)}</div>
        {item.discountReason && (
          <div className={styles.discountReason}>{item.discountReason}</div>
        )}
        <button
          className={styles.remove}
          type="button"
          aria-label={`Void line: ${item.description}, size ${item.size}`}
          onClick={() => onRemove(item.skuCode)}
        >
          Void line
        </button>
      </div>
    </article>
  );
}
