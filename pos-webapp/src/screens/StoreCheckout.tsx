import { useEffect, useMemo, useState } from 'react';
import { lookupLineItem, placeOrder, quoteOrder } from '../api/client';
import { USE_MOCK_DATA } from '../api/config';
import {
  MOCK_LINE_ITEMS,
  MOCK_PROMOTIONS,
  MOCK_SERVICES_AND_FEES,
} from '../api/fixtures';
import type {
  OrderLineItem,
  OrderRequest,
  OrderTotals,
  Promotion,
  Tender,
  TenderType,
} from '../api/types';
import { CartLineItem } from '../components/CartLineItem';
import { Header } from '../components/Header';
import { ItemLookup } from '../components/ItemLookup';
import { OrderSummary } from '../components/OrderSummary';
import { PromotionsBar } from '../components/PromotionsBar';
import { TenderPanel } from '../components/TenderPanel';
import { parseAmount, tenderLabel, type TenderLine } from '../lib/tenders';
import { calculateTotals } from '../lib/totals';
import { useAuth } from '../auth/useAuth';
import styles from './StoreCheckout.module.css';

const PROMO_CATALOG: Record<string, Promotion> = {
  FALL30: { code: 'FALL30', description: '30% off', percentOff: 30, amountOff: 0 },
  CARD10: { code: 'CARD10', description: '10% off', percentOff: 10, amountOff: 0 },
};

const MANAGER_ROLE = 'pos-manager';

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function StoreCheckout() {
  const { identity, logout } = useAuth();
  const [lineItems, setLineItems] = useState<OrderLineItem[]>(
    USE_MOCK_DATA ? MOCK_LINE_ITEMS : [],
  );
  const [promotions, setPromotions] = useState<Promotion[]>(
    USE_MOCK_DATA ? MOCK_PROMOTIONS : [],
  );
  const [taxExempt, setTaxExempt] = useState(false);
  const [tender, setTender] = useState<TenderType>('CREDIT_DEBIT');
  const [splitTender, setSplitTender] = useState(false);
  const [splitLines, setSplitLines] = useState<TenderLine[]>([]);
  const [cashReceived, setCashReceived] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [changeDue, setChangeDue] = useState<number | null>(null);
  const [serverTotals, setServerTotals] = useState<OrderTotals | null>(null);

  const localTotals = useMemo(
    () =>
      calculateTotals({
        lineItems,
        promotions,
        servicesAndFees: USE_MOCK_DATA || lineItems.length > 0 ? MOCK_SERVICES_AND_FEES : 0,
        taxExempt,
      }),
    [lineItems, promotions, taxExempt],
  );
  const totals = USE_MOCK_DATA || lineItems.length === 0 ? localTotals : serverTotals ?? localTotals;

  const storeId = identity?.storeId ?? '';

  useEffect(() => {
    if (USE_MOCK_DATA || lineItems.length === 0) return;
    let cancelled = false;
    quoteOrder({
      storeId,
      registerId: '',
      associateId: '',
      lineItems: lineItems.map((item) => ({ skuCode: item.skuCode, quantity: item.quantity })),
      promotions: promotions.map((promo) => promo.code),
      tenders: [],
      taxExempt,
    })
      .then((quote) => {
        if (!cancelled) setServerTotals(quote.totals);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorText(err));
      });
    return () => {
      cancelled = true;
    };
  }, [lineItems, promotions, taxExempt, storeId]);

  const itemCount = lineItems.reduce((sum, item) => sum + item.quantity, 0);

  if (!identity) return null;
  const sessionIdentity = identity;
  const taxExemptAllowed = USE_MOCK_DATA || sessionIdentity.roles.includes(MANAGER_ROLE);

  function orderRequest(overrides: Partial<OrderRequest> = {}): OrderRequest {
    return {
      storeId: sessionIdentity.storeId,
      registerId: sessionIdentity.registerId,
      associateId: sessionIdentity.associateId,
      lineItems: lineItems.map((item) => ({
        skuCode: item.skuCode,
        quantity: item.quantity,
      })),
      promotions: promotions.map((promo) => promo.code),
      tenders: [],
      taxExempt,
      ...overrides,
    };
  }

  async function handleLookup(skuCode: string): Promise<boolean> {
    let item: OrderLineItem | null;
    try {
      item = await lookupLineItem(skuCode);
    } catch (err) {
      setError(errorText(err));
      return false;
    }
    if (!item) return false;
    const found = item;
    setStatus(null);
    setChangeDue(null);
    setError(null);
    setLineItems((current) => {
      const existing = current.find((line) => line.skuCode === found.skuCode);
      if (!existing) return [...current, found];
      return current.map((line) =>
        line.skuCode === found.skuCode
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

  async function handleApplyPromotion(code: string) {
    if (promotions.some((p) => p.code === code)) return;
    if (USE_MOCK_DATA) {
      const promo = PROMO_CATALOG[code];
      if (promo) setPromotions((current) => [...current, promo]);
      return;
    }
    const codes = [...promotions.map((p) => p.code), code];
    if (lineItems.length === 0) {
      setError('Scan an item before applying a promotion');
      return;
    }
    try {
      const quote = await quoteOrder(orderRequest({ promotions: codes }));
      setError(null);
      setPromotions(quote.promotions);
    } catch (err) {
      setError(errorText(err));
    }
  }

  function handleRemovePromotion(code: string) {
    setPromotions((current) => current.filter((promo) => promo.code !== code));
  }

  function handleSelectTender(type: TenderType) {
    setTender(type);
    if (type === 'CASH') setCashReceived(totals.total.toFixed(2));
  }

  function handleSplitTenderChange(split: boolean) {
    setSplitTender(split);
    if (split) setSplitLines([{ type: tender, amount: totals.total.toFixed(2) }]);
  }

  function tendersForCharge(): Tender[] {
    if (splitTender) {
      return splitLines
        .map((line) => ({
          type: line.type,
          label: tenderLabel(line.type),
          amount: parseAmount(line.amount),
        }))
        .filter((line) => line.amount > 0);
    }
    const amount = tender === 'CASH' ? parseAmount(cashReceived) : totals.total;
    return [{ type: tender, label: tenderLabel(tender), amount }];
  }

  async function handleCharge() {
    setError(null);
    try {
      const response = await placeOrder(orderRequest({ tenders: tendersForCharge() }));
      setStatus(`${response.status} · Order ${response.orderNumber}`);
      setChangeDue(response.changeDue ?? null);
      if (!USE_MOCK_DATA) {
        setLineItems([]);
        setPromotions([]);
        setTaxExempt(false);
        setSplitTender(false);
        setSplitLines([]);
        setCashReceived('');
        setServerTotals(null);
      }
    } catch (err) {
      setStatus(null);
      setChangeDue(null);
      setError(errorText(err));
    }
  }

  return (
    <div className={styles.screen}>
      <Header
        storeId={sessionIdentity.storeId}
        registerId={sessionIdentity.registerId}
        associateName={sessionIdentity.associateName}
        associateId={sessionIdentity.associateId}
        transactionId="TXN-208874"
        mode={USE_MOCK_DATA ? 'Mock data' : 'Live gateway'}
        onLogout={logout}
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
            taxExemptAllowed={taxExemptAllowed}
          />
          <TenderPanel
            amountDue={totals.total}
            selected={tender}
            splitTender={splitTender}
            splitLines={splitLines}
            cashReceived={cashReceived}
            status={status}
            error={error}
            changeDue={changeDue}
            disabled={lineItems.length === 0}
            onSelect={handleSelectTender}
            onSplitTenderChange={handleSplitTenderChange}
            onCashReceivedChange={setCashReceived}
            onSplitLinesChange={setSplitLines}
            onCharge={handleCharge}
          />
        </aside>
      </div>
    </div>
  );
}
