import { useMemo, useRef, useState } from 'react';
import { lookupLineItem, placeOrder } from '../api/client';
import { REGISTER_ID, STORE_ID, USE_MOCK_DATA } from '../api/config';
import {
  MOCK_LINE_ITEMS,
  MOCK_PROMOTIONS,
  MOCK_SERVICES_AND_FEES,
} from '../api/fixtures';
import type { OrderLineItem, Promotion, TenderType } from '../api/types';
import { CartLineItem } from '../components/CartLineItem';
import { Header } from '../components/Header';
import { ItemLookup } from '../components/ItemLookup';
import { OrderSummary } from '../components/OrderSummary';
import { PromotionsBar } from '../components/PromotionsBar';
import { TenderPanel } from '../components/TenderPanel';
import { calculateTotals } from '../lib/totals';
import styles from './StoreCheckout.module.css';

const PROMO_CATALOG: Record<string, Promotion> = {
  FALL30: { code: 'FALL30', description: '30% off', percentOff: 30, amountOff: 0 },
  CARD10: { code: 'CARD10', description: '10% off', percentOff: 10, amountOff: 0 },
};

function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function StoreCheckout() {
  const [lineItems, setLineItems] = useState<OrderLineItem[]>(MOCK_LINE_ITEMS);
  const [promotions, setPromotions] = useState<Promotion[]>(MOCK_PROMOTIONS);
  const [taxExempt, setTaxExempt] = useState(false);
  const [tender, setTender] = useState<TenderType>('CREDIT_DEBIT');
  const [splitTender, setSplitTender] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const [charging, setCharging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chargeInFlight = useRef(false);

  const totals = useMemo(
    () =>
      calculateTotals({
        lineItems,
        promotions,
        servicesAndFees: MOCK_SERVICES_AND_FEES,
        taxExempt,
      }),
    [lineItems, promotions, taxExempt],
  );

  const itemCount = lineItems.reduce((sum, item) => sum + item.quantity, 0);

  async function handleLookup(skuCode: string): Promise<boolean> {
    const item = await lookupLineItem(skuCode);
    if (!item) return false;
    setLineItems((current) => {
      const existing = current.find((line) => line.skuCode === item.skuCode);
      if (!existing) return [...current, item];
      return current.map((line) =>
        line.skuCode === item.skuCode
          ? {
              ...line,
              quantity: line.quantity + 1,
              extendedPrice: line.unitPrice * (line.quantity + 1),
            }
          : line,
      );
    });
    return true;
  }

  function handleRemove(skuCode: string) {
    setLineItems((current) => current.filter((line) => line.skuCode !== skuCode));
  }

  function handleApplyPromotion(code: string) {
    const promo = PROMO_CATALOG[code];
    if (!promo) return;
    setPromotions((current) =>
      current.some((p) => p.code === promo.code) ? current : [...current, promo],
    );
  }

  function handleRemovePromotion(code: string) {
    setPromotions((current) => current.filter((promo) => promo.code !== code));
  }

  async function handleCharge() {
    if (chargeInFlight.current) return;
    chargeInFlight.current = true;
    setCharging(true);
    setError(null);
    setStatus(null);
    try {
      const response = await placeOrder({
        storeId: STORE_ID,
        registerId: REGISTER_ID,
        associateId: 'A-4471',
        lineItems: lineItems.map((item) => ({
          skuCode: item.skuCode,
          quantity: item.quantity,
        })),
        promotions: promotions.map((promo) => promo.code),
        tenders: [{ type: tender, label: tender, amount: totals.total }],
        taxExempt,
        idempotencyKey,
      });
      setStatus(`${response.status} · Order ${response.orderNumber}`);
      setLineItems([]);
      setPromotions([]);
      setIdempotencyKey(newIdempotencyKey());
    } catch (err) {
      setError(
        `Charge failed: ${err instanceof Error ? err.message : String(err)}. Bag kept — try again.`,
      );
    } finally {
      chargeInFlight.current = false;
      setCharging(false);
    }
  }

  return (
    <div className={styles.screen}>
      <Header
        storeId={STORE_ID}
        registerId={REGISTER_ID}
        associateName="M. Reyes"
        associateId="A-4471"
        transactionId="TXN-208874"
        mode={USE_MOCK_DATA ? 'Mock data' : 'Live gateway'}
      />

      <div className={styles.layout}>
        <main className={styles.main}>
          <ItemLookup onLookup={handleLookup} />

          <section className={styles.cart} aria-label="Bag">
            <div className={styles.cartHeader}>
              <h2 className={styles.cartTitle}>Bag</h2>
              <span className={styles.cartCount}>{itemCount} items</span>
            </div>
            {lineItems.length === 0 ? (
              <p className={styles.empty}>Scan a hangtag to start the transaction.</p>
            ) : (
              lineItems.map((item) => (
                <CartLineItem key={item.skuCode} item={item} onRemove={handleRemove} />
              ))
            )}
          </section>

          <PromotionsBar
            promotions={promotions}
            onApply={handleApplyPromotion}
            onRemove={handleRemovePromotion}
          />
        </main>

        <aside className={styles.aside}>
          <OrderSummary
            totals={totals}
            itemCount={itemCount}
            onTaxExemptChange={setTaxExempt}
          />
          <TenderPanel
            amountDue={totals.total}
            selected={tender}
            splitTender={splitTender}
            status={status}
            pending={charging}
            error={error}
            disabled={lineItems.length === 0 || charging}
            onSelect={setTender}
            onSplitTenderChange={setSplitTender}
            onCharge={handleCharge}
          />
        </aside>
      </div>
    </div>
  );
}
