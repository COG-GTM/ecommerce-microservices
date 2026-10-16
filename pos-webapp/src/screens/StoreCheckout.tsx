import { useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import {
  ApiError,
  buildOrderRequest,
  lookupLineItem,
  placeOrder,
  shipFromStoreFor,
  totalsMismatches,
} from '../api/client';
import { REGISTER_ID, STORE_ID, USE_MOCK_DATA } from '../api/config';
import {
  MOCK_LINE_ITEMS,
  MOCK_PROMO_CATALOG,
  MOCK_PROMOTIONS,
  MOCK_SERVICES,
  MOCK_SERVICES_AND_FEES,
} from '../api/fixtures';
import type {
  FulfillmentType,
  OrderLineItem,
  OrderTotals,
  Promotion,
  ReceiptChannel,
  TenderType,
} from '../api/types';
import { CartLineItem } from '../components/CartLineItem';
import { Header } from '../components/Header';
import { ItemLookup } from '../components/ItemLookup';
import { OrderSummary } from '../components/OrderSummary';
import { PromotionsBar } from '../components/PromotionsBar';
import { TenderPanel } from '../components/TenderPanel';
import { calculateTotals, formatCurrency } from '../lib/totals';
import styles from './StoreCheckout.module.css';

const PROMO_CATALOG = MOCK_PROMO_CATALOG;

const ASSOCIATE_ID = 'A-4471';

const TOTAL_LABELS: Record<string, string> = {
  merchandiseTotal: 'Merchandise',
  discountTotal: 'Discounts',
  servicesAndFees: 'Services & fees',
  taxableSubtotal: 'Taxable subtotal',
  salesTax: 'Sales tax',
  total: 'Total',
};

type NoticeKind = 'error' | 'warning' | 'success';

interface Notice {
  kind: NoticeKind;
  title: string;
  detail?: string;
}

function describeError(error: unknown, action: string): Notice {
  if (error instanceof ApiError) {
    const slug = error.problemType;
    const title = error.problem?.title ?? `${action} failed`;
    return {
      kind: 'error',
      title: `${error.status} ${title}${slug ? ` (${slug})` : ''}`,
      detail: error.message,
    };
  }
  return {
    kind: 'error',
    title: `${action} failed`,
    detail: error instanceof Error ? error.message : String(error),
  };
}

const NOTICE_COLORS: Record<NoticeKind, string> = {
  error: 'var(--sale)',
  warning: '#8a5a00',
  success: 'var(--positive)',
};

const panelStyle: CSSProperties = {
  background: 'var(--surface)',
  border: '1px solid var(--rule)',
};
const panelHeadingStyle: CSSProperties = {
  margin: 0,
  padding: '14px 18px',
  borderBottom: '1px solid var(--rule)',
  fontSize: 12,
  fontWeight: 700,
  letterSpacing: '0.14em',
  textTransform: 'uppercase',
};
const panelBodyStyle: CSSProperties = {
  padding: '14px 18px',
  display: 'flex',
  flexDirection: 'column',
  gap: 10,
  fontSize: 13,
};
const fieldStyle: CSSProperties = {
  border: '1px solid var(--rule)',
  padding: '8px 10px',
  font: 'inherit',
  width: '100%',
};
const rowStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  gap: 12,
};

function Panel({ title, label, children }: { title: string; label: string; children: ReactNode }) {
  return (
    <section style={panelStyle} aria-label={label}>
      <h2 style={panelHeadingStyle}>{title}</h2>
      <div style={panelBodyStyle}>{children}</div>
    </section>
  );
}

function NoticeBanner({ notice }: { notice: Notice }) {
  return (
    <div
      role={notice.kind === 'error' ? 'alert' : 'status'}
      data-kind={notice.kind}
      style={{
        ...panelStyle,
        borderLeft: `4px solid ${NOTICE_COLORS[notice.kind]}`,
        padding: '12px 14px',
        fontSize: 13,
      }}
    >
      <strong style={{ color: NOTICE_COLORS[notice.kind] }}>{notice.title}</strong>
      {notice.detail && <div style={{ marginTop: 4, color: 'var(--ink-soft)' }}>{notice.detail}</div>}
    </div>
  );
}

export function StoreCheckout() {
  const [lineItems, setLineItems] = useState<OrderLineItem[]>(
    USE_MOCK_DATA ? MOCK_LINE_ITEMS : [],
  );
  const [promotions, setPromotions] = useState<Promotion[]>(USE_MOCK_DATA ? MOCK_PROMOTIONS : []);
  const [taxExempt, setTaxExempt] = useState(false);
  const [tender, setTender] = useState<TenderType>('CREDIT_DEBIT');
  const [splitTender, setSplitTender] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [fulfillment, setFulfillment] = useState<Record<string, FulfillmentType>>({});
  const [receiptChannel, setReceiptChannel] = useState<ReceiptChannel>('PRINT');
  const [receiptDestination, setReceiptDestination] = useState('');
  const [taxExemptCertificate, setTaxExemptCertificate] = useState('');
  const [notices, setNotices] = useState<Notice[]>([]);
  const [serverTotals, setServerTotals] = useState<OrderTotals | null>(null);
  const [charging, setCharging] = useState(false);
  /** Request snapshot of the last successful live charge; Charge re-enables once anything changes. */
  const [chargedKey, setChargedKey] = useState<string | null>(null);
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

  const shipFromStoreIds = useMemo(
    () =>
      Object.fromEntries(
        lineItems.flatMap((item) => {
          const store = shipFromStoreFor(item);
          return store ? [[item.skuCode, store]] : [];
        }),
      ) as Record<string, string>,
    [lineItems],
  );

  // A SHIP_FROM_STORE choice that is no longer fulfillable (quantity grew past nearby stock)
  // falls back to TAKE_WITH, and the select shows that.
  const effectiveFulfillment = useMemo(
    () =>
      Object.fromEntries(
        Object.entries(fulfillment).map(([sku, type]) => [
          sku,
          type === 'SHIP_FROM_STORE' && !shipFromStoreIds[sku] ? 'TAKE_WITH' : type,
        ]),
      ) as Record<string, FulfillmentType>,
    [fulfillment, shipFromStoreIds],
  );

  const orderRequest = useMemo(
    () =>
      buildOrderRequest({
        storeId: STORE_ID,
        registerId: REGISTER_ID,
        associateId: ASSOCIATE_ID,
        lineItems,
        promotionCodes: promotions.map((promo) => promo.code),
        tenderType: tender,
        tenderLabel: tender,
        amountDue: totals.total,
        services: MOCK_SERVICES,
        taxExempt,
        taxExemptCertificate,
        fulfillment: effectiveFulfillment,
        shipFromStoreIds,
        receiptDelivery:
          receiptChannel === 'EMAIL' || receiptChannel === 'SMS'
            ? { channel: receiptChannel, destination: receiptDestination.trim() }
            : { channel: receiptChannel },
      }),
    [
      lineItems,
      promotions,
      tender,
      totals.total,
      taxExempt,
      taxExemptCertificate,
      effectiveFulfillment,
      shipFromStoreIds,
      receiptChannel,
      receiptDestination,
    ],
  );
  const requestKey = JSON.stringify(orderRequest);

  const itemCount = lineItems.reduce((sum, item) => sum + item.quantity, 0);

  async function handleLookup(skuCode: string): Promise<boolean> {
    let item: OrderLineItem | null;
    try {
      item = await lookupLineItem(skuCode);
    } catch (error) {
      setNotices([describeError(error, `Lookup of ${skuCode}`)]);
      return true;
    }
    if (!item) return false;
    const found = item;
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

  function handleApplyPromotion(code: string) {
    // Live mode: order-service owns the promo catalog, so unknown codes go to the
    // server (no preview discount) and come back as 422 invalid-promotion if bad.
    const promo =
      PROMO_CATALOG[code] ??
      (USE_MOCK_DATA
        ? undefined
        : { code, description: 'validated at charge', percentOff: 0, amountOff: 0 });
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
    const request = orderRequest;
    const key = requestKey;

    if (USE_MOCK_DATA) {
      const response = await placeOrder(request);
      setStatus(`${response.status} · Order ${response.orderNumber}`);
      return;
    }

    chargeInFlight.current = true;
    setCharging(true);
    setNotices([]);
    setServerTotals(null);
    setStatus('Charging…');
    try {
      const response = await placeOrder(request);
      setServerTotals(response.totals);
      setChargedKey(key);
      setStatus(
        `${response.status} · Order ${response.orderNumber} · Charged ${formatCurrency(response.totals.total)}`,
      );
      const mismatches = totalsMismatches(totals, response.totals);
      setNotices(
        mismatches.length === 0
          ? [
              {
                kind: 'success',
                title: `Order ${response.orderNumber} placed`,
                detail: `Server total ${formatCurrency(response.totals.total)} matches the register preview.`,
              },
            ]
          : [
              {
                kind: 'warning',
                title: 'Server totals differ from the register preview',
                detail: mismatches
                  .map(
                    (m) =>
                      `${TOTAL_LABELS[m.field] ?? m.field}: preview ${formatCurrency(m.preview)}, server ${formatCurrency(m.server)}`,
                  )
                  .join(' · '),
              },
            ],
      );
    } catch (error) {
      setStatus(null);
      setNotices([describeError(error, 'Charge')]);
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
        associateId={ASSOCIATE_ID}
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
          {!USE_MOCK_DATA && (
            <Panel title="Fulfillment & receipt" label="Fulfillment and receipt">
              {lineItems.map((item) => {
                const shipStore = shipFromStoreFor(item);
                return (
                <label key={item.skuCode} style={rowStyle}>
                  <span>{item.skuCode}</span>
                  <select
                    aria-label={`Fulfillment for ${item.skuCode}`}
                    value={effectiveFulfillment[item.skuCode] ?? 'TAKE_WITH'}
                    onChange={(event) =>
                      setFulfillment((current) => ({
                        ...current,
                        [item.skuCode]: event.target.value as FulfillmentType,
                      }))
                    }
                  >
                    <option value="TAKE_WITH">Take with</option>
                    <option value="SHIP_FROM_STORE" disabled={!shipStore}>
                      {shipStore ? `Ship from store ${shipStore}` : 'Ship from store (not eligible)'}
                    </option>
                  </select>
                </label>
                );
              })}
              <label style={rowStyle}>
                <span>Receipt</span>
                <select
                  aria-label="Receipt delivery"
                  value={receiptChannel}
                  onChange={(event) => setReceiptChannel(event.target.value as ReceiptChannel)}
                >
                  <option value="PRINT">Print</option>
                  <option value="EMAIL">Email</option>
                  <option value="SMS">SMS</option>
                  <option value="NONE">No receipt</option>
                </select>
              </label>
              {(receiptChannel === 'EMAIL' || receiptChannel === 'SMS') && (
                <input
                  style={fieldStyle}
                  aria-label="Receipt destination"
                  placeholder={receiptChannel === 'EMAIL' ? 'customer@example.com' : '415-555-0100'}
                  value={receiptDestination}
                  onChange={(event) => setReceiptDestination(event.target.value)}
                />
              )}
              {taxExempt && (
                <input
                  style={fieldStyle}
                  aria-label="Tax-exempt certificate"
                  placeholder="Tax-exempt certificate #"
                  value={taxExemptCertificate}
                  onChange={(event) => setTaxExemptCertificate(event.target.value)}
                />
              )}
            </Panel>
          )}
          <TenderPanel
            amountDue={totals.total}
            selected={tender}
            splitTender={splitTender}
            status={status}
            disabled={lineItems.length === 0 || charging || chargedKey === requestKey}
            onSelect={setTender}
            onSplitTenderChange={setSplitTender}
            onCharge={handleCharge}
          />
          {notices.map((notice, index) => (
            <NoticeBanner key={`${notice.kind}-${index}`} notice={notice} />
          ))}
          {!USE_MOCK_DATA && serverTotals && (
            <Panel title="Charged · server totals" label="Server totals">
              {(Object.keys(TOTAL_LABELS) as Array<keyof OrderTotals>).map((field) => (
                <div key={field} style={rowStyle}>
                  <span>{TOTAL_LABELS[field]}</span>
                  <strong>{formatCurrency(Number(serverTotals[field]))}</strong>
                </div>
              ))}
            </Panel>
          )}
        </aside>
      </div>
    </div>
  );
}
