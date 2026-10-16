import { afterEach, describe, expect, it, vi } from 'vitest';
import { calculateTotals } from '../lib/totals';
import {
  MOCK_INVENTORY,
  MOCK_LINE_ITEMS,
  MOCK_ORDER_RESPONSE,
  MOCK_PRODUCTS,
  MOCK_PROMO_CATALOG,
  MOCK_PROMOTIONS,
  MOCK_SERVICES,
  MOCK_SERVICES_AND_FEES,
} from './fixtures';
import type {
  Inventory,
  OrderLineItem,
  OrderRequest,
  OrderResponse,
  OrderTotals,
  Product,
  Promotion,
  ServiceFee,
} from './types';

const config = vi.hoisted(() => ({ USE_MOCK_DATA: true }));
vi.mock('./config', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./config')>();
  return {
    ...actual,
    API_BASE_URL: 'http://gateway.test',
    AUTH_TOKEN: undefined,
    get USE_MOCK_DATA() {
      return config.USE_MOCK_DATA;
    },
  };
});

import {
  ApiError,
  buildOrderRequest,
  getInventory,
  getProductBySku,
  lookupLineItem,
  placeOrder,
  shipFromStoreFor,
  totalsMismatches,
} from './client';

// --- Runtime shape checks: every key `types.ts` declares, no extras, right JS types. ---

type Check = (value: unknown, path: string) => string[];

const str: Check = (v, p) => (typeof v === 'string' ? [] : [`${p}: expected string`]);
const num: Check = (v, p) =>
  typeof v === 'number' && Number.isFinite(v) ? [] : [`${p}: expected number`];
const bool: Check = (v, p) => (typeof v === 'boolean' ? [] : [`${p}: expected boolean`]);
const nullable =
  (inner: Check): Check =>
  (v, p) =>
    v === null ? [] : inner(v, p);
const oneOf =
  (...allowed: string[]): Check =>
  (v, p) =>
    allowed.includes(v as string) ? [] : [`${p}: expected one of ${allowed.join('|')}`];
const arrayOf =
  (inner: Check): Check =>
  (v, p) =>
    Array.isArray(v) ? v.flatMap((item, i) => inner(item, `${p}[${i}]`)) : [`${p}: expected array`];

function shape<T>(fields: Record<keyof T, Check>, optional: Array<keyof T> = []): Check {
  return (v, p) => {
    if (typeof v !== 'object' || v === null || Array.isArray(v)) return [`${p}: expected object`];
    const obj = v as Record<string, unknown>;
    const errors: string[] = [];
    for (const [key, check] of Object.entries(fields) as Array<[string, Check]>) {
      if (!(key in obj)) {
        if (!optional.includes(key as keyof T)) errors.push(`${p}.${key}: missing`);
        continue;
      }
      errors.push(...check(obj[key], `${p}.${key}`));
    }
    for (const key of Object.keys(obj)) {
      if (!(key in fields)) errors.push(`${p}.${key}: not declared in types.ts`);
    }
    return errors;
  };
}

const variantShape = shape({
  skuCode: str, colorName: str, colorCode: str, size: str, listPrice: num, salePrice: num,
});
const productShape = shape<Product>({
  styleId: str, skuCode: str, name: str, description: str, department: str, category: str,
  colorName: str, colorCode: str, size: str, listPrice: num, salePrice: num,
  clearancePercent: num, finalSale: bool, variants: arrayOf(variantShape),
});
const inventoryShape = shape<Inventory>({
  skuCode: str, storeId: str, onHand: num, shipFromStoreEligible: bool, floor: str, fixture: str,
  nearbyStores: arrayOf(shape({ storeId: str, storeName: str, distanceMiles: num, onHand: num })),
});
const promotionShape = shape<Promotion>({ code: str, description: str, percentOff: num, amountOff: num });
const lineItemShape = shape<OrderLineItem>({
  skuCode: str, styleId: str, description: str, department: str, colorName: str, colorCode: str,
  size: str, quantity: num, listPrice: num, unitPrice: num, extendedPrice: num,
  discountReason: nullable(str), clearancePercent: num, finalSale: bool, inventory: inventoryShape,
});
const serviceFeeShape = shape<ServiceFee>({ code: str, description: str, amount: num });
const totalsShape = shape<OrderTotals>({
  merchandiseTotal: num, servicesAndFees: num, discountTotal: num, taxableSubtotal: num,
  taxRate: num, salesTax: num, total: num, savedToday: num, taxExempt: bool,
});
const tenderShape = shape({ type: oneOf('CREDIT_DEBIT', 'GIFT_CARD', 'MOBILE_WALLET'), label: str, amount: num });
const orderResponseShape = shape<OrderResponse>(
  {
    orderNumber: str, status: oneOf('COMPLETED'), message: str, totals: totalsShape,
    lineItems: arrayOf((v, p) => (typeof v === 'object' && v ? [] : [`${p}: expected object`])),
    promotions: arrayOf(shape({ code: str, amount: num })),
  },
  ['lineItems', 'promotions'],
);
const orderRequestShape = shape<OrderRequest>(
  {
    storeId: str, registerId: str, associateId: str,
    lineItems: arrayOf(
      shape(
        {
          skuCode: str, quantity: num,
          fulfillmentType: oneOf('TAKE_WITH', 'SHIP_FROM_STORE'),
          fulfillmentStoreId: nullable(str),
        },
        ['fulfillmentType', 'fulfillmentStoreId'],
      ),
    ),
    promotions: arrayOf(str), tenders: arrayOf(tenderShape), taxExempt: bool,
    services: arrayOf(serviceFeeShape),
    receiptDelivery: nullable(
      shape({ channel: oneOf('EMAIL', 'SMS', 'PRINT', 'NONE'), destination: nullable(str) }, ['destination']),
    ),
    taxExemptCertificate: nullable(str),
  },
  ['receiptDelivery', 'taxExemptCertificate'],
);

const GOLDEN_SKUS = ['268341-016-L', '471902-004-29', '512884-022-M'];

function goldenRequest(overrides: Partial<Parameters<typeof buildOrderRequest>[0]> = {}) {
  return buildOrderRequest({
    storeId: '1969',
    registerId: '04',
    associateId: 'A-4471',
    lineItems: MOCK_LINE_ITEMS,
    promotionCodes: ['FALL30'],
    tenderType: 'CREDIT_DEBIT',
    tenderLabel: 'CREDIT_DEBIT',
    amountDue: 117.65,
    services: MOCK_SERVICES,
    taxExempt: false,
    receiptDelivery: { channel: 'EMAIL', destination: 'customer@example.com' },
    ...overrides,
  });
}

describe('contract fixtures match types.ts at runtime', () => {
  it('MOCK_PRODUCTS are Products (the three golden SKUs)', () => {
    expect(MOCK_PRODUCTS.map((p) => p.skuCode)).toEqual(GOLDEN_SKUS);
    expect(MOCK_PRODUCTS.flatMap((p, i) => productShape(p, `MOCK_PRODUCTS[${i}]`))).toEqual([]);
  });

  it('MOCK_INVENTORY entries are Inventory rows at store 1969', () => {
    expect(Object.keys(MOCK_INVENTORY)).toEqual(GOLDEN_SKUS);
    for (const [sku, row] of Object.entries(MOCK_INVENTORY)) {
      expect(inventoryShape(row, `MOCK_INVENTORY[${sku}]`)).toEqual([]);
      expect(row.skuCode).toBe(sku);
      expect(row.storeId).toBe('1969');
    }
    expect(MOCK_INVENTORY['512884-022-M'].shipFromStoreEligible).toBe(false);
  });

  it('MOCK_PROMOTIONS, MOCK_LINE_ITEMS and MOCK_SERVICES match their types', () => {
    expect(MOCK_PROMOTIONS.flatMap((p, i) => promotionShape(p, `MOCK_PROMOTIONS[${i}]`))).toEqual([]);
    expect(
      Object.entries(MOCK_PROMO_CATALOG).flatMap(([code, p]) => [
        ...promotionShape(p, `MOCK_PROMO_CATALOG.${code}`),
        ...(p.code === code ? [] : [`MOCK_PROMO_CATALOG.${code}.code`]),
      ]),
    ).toEqual([]);
    expect(MOCK_LINE_ITEMS.flatMap((l, i) => lineItemShape(l, `MOCK_LINE_ITEMS[${i}]`))).toEqual([]);
    expect(MOCK_SERVICES.flatMap((s, i) => serviceFeeShape(s, `MOCK_SERVICES[${i}]`))).toEqual([]);
    expect(MOCK_SERVICES.reduce((sum, s) => sum + s.amount, 0)).toBe(MOCK_SERVICES_AND_FEES);
  });

  it('MOCK_ORDER_RESPONSE is an OrderResponse carrying the golden totals', () => {
    expect(orderResponseShape(MOCK_ORDER_RESPONSE, 'MOCK_ORDER_RESPONSE')).toEqual([]);
  });

  it('the golden OrderRequest has the POS-3 shape and sends no prices', () => {
    const request = goldenRequest();
    expect(orderRequestShape(request, 'request')).toEqual([]);
    expect(request).toEqual({
      storeId: '1969',
      registerId: '04',
      associateId: 'A-4471',
      lineItems: GOLDEN_SKUS.map((skuCode) => ({
        skuCode, quantity: 1, fulfillmentType: 'TAKE_WITH', fulfillmentStoreId: '1969',
      })),
      promotions: ['FALL30'],
      tenders: [{ type: 'CREDIT_DEBIT', label: 'CREDIT_DEBIT', amount: 117.65 }],
      taxExempt: false,
      services: [{ code: 'ALT', description: 'Services & fees', amount: 12 }],
      receiptDelivery: { channel: 'EMAIL', destination: 'customer@example.com' },
      taxExemptCertificate: null,
    });
  });

  it('ship-from-store lines carry the fulfilling store; the certificate is only sent when exempt', () => {
    const request = goldenRequest({
      fulfillment: { '471902-004-29': 'SHIP_FROM_STORE' },
      shipFromStoreIds: { '471902-004-29': '1042' },
      taxExempt: true,
      taxExemptCertificate: ' CA-123 ',
    });
    expect(request.lineItems[1]).toEqual({
      skuCode: '471902-004-29', quantity: 1, fulfillmentType: 'SHIP_FROM_STORE', fulfillmentStoreId: '1042',
    });
    expect(request.taxExemptCertificate).toBe('CA-123');
    expect(goldenRequest({ taxExemptCertificate: 'CA-123' }).taxExemptCertificate).toBeNull();
  });
});

describe('shipFromStoreFor', () => {
  const [tee, jean, hoodie] = MOCK_LINE_ITEMS;

  it('picks the nearest nearby store with enough stock for the line', () => {
    expect(shipFromStoreFor(jean)).toBe('1042');
    expect(shipFromStoreFor({ ...jean, quantity: 4 })).toBe('1042');
    expect(shipFromStoreFor({ ...tee, quantity: 12 })).toBeNull();
    expect(shipFromStoreFor({ ...jean, quantity: 6 })).toBeNull();
  });

  it('buildOrderRequest refuses SHIP_FROM_STORE without a source store', () => {
    expect(() =>
      buildOrderRequest({
        storeId: '1969', registerId: '04', associateId: 'A-4471', lineItems: [hoodie],
        promotionCodes: [], tenderType: 'CREDIT_DEBIT', tenderLabel: 'CREDIT_DEBIT', amountDue: 0,
        services: [], taxExempt: false, fulfillment: { [hoodie.skuCode]: 'SHIP_FROM_STORE' },
      }),
    ).toThrow(/No ship-from-store source store for 512884-022-M/);
  });

  it('returns null for SKUs that are not ship-from-store eligible', () => {
    expect(hoodie.inventory.shipFromStoreEligible).toBe(false);
    expect(shipFromStoreFor(hoodie)).toBeNull();
  });
});

describe('PRE-1 golden-cart parity', () => {
  const golden = (taxExempt: boolean) =>
    calculateTotals({
      lineItems: MOCK_LINE_ITEMS,
      promotions: MOCK_PROMOTIONS,
      servicesAndFees: MOCK_SERVICES_AND_FEES,
      taxExempt,
    });

  it('calculateTotals(golden cart) = 159.85 / 63.54 / 108.31 / 9.34 / 117.65', () => {
    const totals = golden(false);
    expect(totals.merchandiseTotal).toBe(159.85);
    expect(totals.discountTotal).toBe(63.54);
    expect(totals.taxableSubtotal).toBe(108.31);
    expect(totals.salesTax).toBe(9.34);
    expect(totals.total).toBe(117.65);
    expect(totals.servicesAndFees).toBe(12);
    expect(totals.taxRate).toBe(0.08625);
  });

  it('tax exempt golden cart totals 108.31', () => {
    expect(golden(true).total).toBe(108.31);
  });

  it('MOCK_ORDER_RESPONSE.totals equals the client preview exactly', () => {
    expect(MOCK_ORDER_RESPONSE.totals).toEqual(golden(false));
    expect(totalsMismatches(golden(false), MOCK_ORDER_RESPONSE.totals)).toEqual([]);
  });

  it('totalsMismatches reports every field that differs by a cent or more', () => {
    const server = { ...golden(false), salesTax: 9.35, total: 117.66 };
    expect(totalsMismatches(golden(false), server)).toEqual([
      { field: 'salesTax', preview: 9.34, server: 9.35 },
      { field: 'total', preview: 117.65, server: 117.66 },
    ]);
  });
});

// --- Live-mode client against the frozen backend contracts (fetch stubbed). ---

function json(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problem(status: number, type: string, title: string, detail: string): Response {
  return json(status, { type, title, status, detail, instance: '/api/order' }, 'application/problem+json');
}

describe('live client (VITE_USE_MOCK=false)', () => {
  const fetchMock = vi.fn<typeof fetch>();

  function live() {
    config.USE_MOCK_DATA = false;
    vi.stubGlobal('fetch', fetchMock);
  }

  afterEach(() => {
    config.USE_MOCK_DATA = true;
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('getProductBySku reads the single-object POS-1 response', async () => {
    live();
    fetchMock.mockResolvedValueOnce(json(200, MOCK_PRODUCTS[0]));
    await expect(getProductBySku('268341-016-L')).resolves.toEqual(MOCK_PRODUCTS[0]);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://gateway.test/api/product?skuCode=268341-016-L',
      expect.anything(),
    );
  });

  it('getProductBySku maps 404 sku-not-found to null (no products[0] fallback)', async () => {
    live();
    fetchMock.mockResolvedValueOnce(
      problem(404, '/problems/sku-not-found', 'SKU not found', 'No product with SKU 999999-000-X'),
    );
    await expect(getProductBySku('999999-000-X')).resolves.toBeNull();
  });

  it('getInventory reads the POS-2 store row and maps 404 to null', async () => {
    live();
    fetchMock.mockResolvedValueOnce(json(200, MOCK_INVENTORY['471902-004-29']));
    await expect(getInventory('471902-004-29')).resolves.toEqual(MOCK_INVENTORY['471902-004-29']);
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://gateway.test/api/inventory?skuCode=471902-004-29&storeId=1969',
      expect.anything(),
    );
    fetchMock.mockResolvedValueOnce(problem(404, 'sku-not-found', 'SKU not found', 'none'));
    await expect(getInventory('471902-004-29')).resolves.toBeNull();
  });

  it('lookupLineItem builds the same line item as mock mode from live responses', async () => {
    live();
    for (const sku of GOLDEN_SKUS) {
      const product = MOCK_PRODUCTS.find((p) => p.skuCode === sku)!;
      // POS-1 adds the legacy id/price fields only on the list call; tolerate extras.
      fetchMock.mockResolvedValueOnce(json(200, product));
      fetchMock.mockResolvedValueOnce(json(200, MOCK_INVENTORY[sku]));
      const line = await lookupLineItem(sku);
      expect(line).toEqual(MOCK_LINE_ITEMS.find((l) => l.skuCode === sku));
    }
  });

  it('non-404 lookup failures surface as ApiError', async () => {
    live();
    fetchMock.mockResolvedValueOnce(json(200, MOCK_PRODUCTS[1]));
    fetchMock.mockResolvedValueOnce(new Response('', { status: 503 }));
    await expect(lookupLineItem('471902-004-29')).rejects.toMatchObject({ status: 503 });
  });

  it('placeOrder parses the 201 OrderResponse', async () => {
    live();
    const body = { ...MOCK_ORDER_RESPONSE, orderNumber: '4d1c-uuid' };
    fetchMock.mockResolvedValueOnce(json(201, body));
    const response = await placeOrder(goldenRequest());
    expect(orderResponseShape(response, 'response')).toEqual([]);
    expect(response.totals.total).toBe(117.65);
    const [, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe('POST');
    expect(JSON.parse(init?.body as string)).toEqual(goldenRequest());
  });

  it.each([
    [409, 'urn:problem-type:gap-pos:out-of-stock', 'Out of stock', 'out-of-stock'],
    [422, 'urn:problem-type:gap-pos:invalid-promotion', 'Invalid promotion', 'invalid-promotion'],
    [422, 'urn:problem-type:gap-pos:validation-failed', 'Validation failed', 'validation-failed'],
    [503, 'urn:problem-type:gap-pos:dependency-unavailable', 'Dependency unavailable', 'dependency-unavailable'],
  ])('placeOrder turns %i problem+json (%s) into a typed ApiError', async (status, type, title, slug) => {
    live();
    fetchMock.mockResolvedValueOnce(problem(status, type, title, `${title} detail`));
    const error = await placeOrder(goldenRequest()).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(status);
    expect(apiError.problemType).toBe(slug);
    expect(apiError.problem?.title).toBe(title);
    expect(apiError.message).toBe(`${title} detail`);
  });

  it('a non-JSON error (e.g. gateway 401) still becomes an ApiError', async () => {
    live();
    fetchMock.mockResolvedValueOnce(new Response('', { status: 401, statusText: 'Unauthorized' }));
    const error = (await placeOrder(goldenRequest()).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.problem).toBeNull();
    expect(error.problemType).toBeNull();
  });
});

describe('mock mode (VITE_USE_MOCK=true) is unchanged', () => {
  it('never calls fetch and resolves fixtures', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    expect(await getProductBySku('268341-016-L')).toEqual(MOCK_PRODUCTS[0]);
    expect(await getProductBySku('nope')).toBeNull();
    expect(await lookupLineItem('512884-022-M')).toEqual(MOCK_LINE_ITEMS[2]);
    const response = await placeOrder(goldenRequest());
    expect(response.totals).toEqual(MOCK_ORDER_RESPONSE.totals);
    const teeOnly = await placeOrder(
      goldenRequest({ lineItems: [MOCK_LINE_ITEMS[0]], promotionCodes: [], services: [] }),
    );
    expect(teeOnly.totals.merchandiseTotal).toBe(29.95);
    expect(teeOnly.totals.total).toBe(19.52);
    const jeanCard10 = await placeOrder(
      goldenRequest({ lineItems: [MOCK_LINE_ITEMS[1]], promotionCodes: ['CARD10'], services: [] }),
    );
    expect(jeanCard10.totals.discountTotal).toBe(7);
    expect(jeanCard10.totals.taxableSubtotal).toBe(62.95);
    expect(response.status).toBe('COMPLETED');
    expect(response.orderNumber).toMatch(/^1969-04-\d{5}$/);
    expect(response.message).toBe('Order placed (mock mode)');
    expect(fetchSpy).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
