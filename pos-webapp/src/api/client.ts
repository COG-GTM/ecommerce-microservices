import { API_BASE_URL, AUTH_TOKEN, STORE_ID, USE_MOCK_DATA } from './config';
import {
  MOCK_INVENTORY,
  MOCK_LINE_ITEMS,
  MOCK_ORDER_RESPONSE,
  MOCK_PRODUCTS,
  MOCK_PROMO_CATALOG,
  mockLineItem,
} from './fixtures';
import type {
  ApiProblem,
  FulfillmentType,
  Inventory,
  OrderLineItem,
  OrderRequest,
  OrderResponse,
  OrderTotals,
  Product,
  ReceiptDelivery,
  ServiceFee,
  Tender,
} from './types';
import { calculateTotals } from '../lib/totals';

/** A non-2xx gateway/service response, with the parsed `problem+json` body when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ApiProblem | null;

  constructor(status: number, problem: ApiProblem | null, fallbackMessage: string) {
    super(problem?.detail ?? problem?.title ?? fallbackMessage);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /**
   * Stable PRE-1 slug (`out-of-stock`, `invalid-promotion`, ...). Services prefix the
   * `type` URI differently (`/problems/`, `urn:problem-type:gap-pos:` or bare), so
   * only the last path/URN segment is compared.
   */
  get problemType(): string | null {
    const type = this.problem?.type;
    if (!type) return null;
    const segments = type.split(/[/:]/).filter(Boolean);
    return segments[segments.length - 1] ?? null;
  }
}

async function readProblem(response: Response): Promise<ApiProblem | null> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) return null;
  try {
    const body = (await response.json()) as Partial<ApiProblem> | null;
    if (!body || typeof body !== 'object') return null;
    return {
      ...body,
      type: typeof body.type === 'string' ? body.type : 'about:blank',
      title: typeof body.title === 'string' ? body.title : response.statusText,
      status: typeof body.status === 'number' ? body.status : response.status,
    };
  } catch {
    return null;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((init?.headers as Record<string, string>) ?? {}),
  };
  if (AUTH_TOKEN) {
    headers.Authorization = `Bearer ${AUTH_TOKEN}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    throw new ApiError(
      response.status,
      await readProblem(response),
      `${init?.method ?? 'GET'} ${path} failed: ${response.status}`,
    );
  }
  return (await response.json()) as T;
}

/** Maps a 404 (`sku-not-found` / `store-not-found`) to `null`; any other error is rethrown. */
async function requestOrNull<T>(path: string): Promise<T | null> {
  try {
    return await request<T>(path);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export async function getProducts(): Promise<Product[]> {
  if (USE_MOCK_DATA) return MOCK_PRODUCTS;
  return request<Product[]>('/api/product');
}

export async function getProductBySku(skuCode: string): Promise<Product | null> {
  if (USE_MOCK_DATA) {
    return MOCK_PRODUCTS.find((p) => p.skuCode === skuCode) ?? null;
  }
  return requestOrNull<Product>(`/api/product?skuCode=${encodeURIComponent(skuCode)}`);
}

export async function getInventory(
  skuCode: string,
  storeId: string = STORE_ID,
): Promise<Inventory | null> {
  if (USE_MOCK_DATA) return MOCK_INVENTORY[skuCode] ?? null;
  return requestOrNull<Inventory>(
    `/api/inventory?skuCode=${encodeURIComponent(skuCode)}&storeId=${encodeURIComponent(storeId)}`,
  );
}

/** Resolves a scanned hangtag (style-color-size) into a cart line item. */
export async function lookupLineItem(
  skuCode: string,
  quantity = 1,
): Promise<OrderLineItem | null> {
  if (USE_MOCK_DATA) return mockLineItem(skuCode, quantity);

  const product = await getProductBySku(skuCode);
  if (!product) return null;
  const inventory = await getInventory(product.skuCode);
  if (!inventory) return null;

  return {
    skuCode: product.skuCode,
    styleId: product.styleId,
    description: product.description,
    department: `${product.department} · ${product.category}`,
    colorName: product.colorName,
    colorCode: product.colorCode,
    size: product.size,
    quantity,
    listPrice: product.listPrice,
    unitPrice: product.salePrice,
    extendedPrice: product.salePrice * quantity,
    discountReason:
      product.clearancePercent > 0 ? `Clearance ${product.clearancePercent}%` : null,
    clearancePercent: product.clearancePercent,
    finalSale: product.finalSale,
    inventory,
  };
}

/**
 * Live mode: 201 → `OrderResponse` (server-priced `totals`); 409 `out-of-stock`,
 * 422 `invalid-promotion` / `validation-failed` and 503 `dependency-unavailable`
 * reject with an `ApiError` carrying the `problem+json` body.
 */
/** Mock mode prices the submitted request from the fixtures, like order-service would. */
function mockOrderTotals(order: OrderRequest): OrderTotals {
  const lineItems = order.lineItems.flatMap((line) => {
    const fixture = MOCK_LINE_ITEMS.find((item) => item.skuCode === line.skuCode);
    return fixture
      ? [{ ...fixture, quantity: line.quantity, extendedPrice: fixture.unitPrice * line.quantity }]
      : [];
  });
  return calculateTotals({
    lineItems,
    promotions: order.promotions.flatMap((code) =>
      MOCK_PROMO_CATALOG[code] ? [MOCK_PROMO_CATALOG[code]] : [],
    ),
    servicesAndFees: order.services.reduce((sum, fee) => sum + fee.amount, 0),
    taxExempt: order.taxExempt,
  });
}

export async function placeOrder(order: OrderRequest): Promise<OrderResponse> {
  if (USE_MOCK_DATA) {
    return {
      ...MOCK_ORDER_RESPONSE,
      totals: mockOrderTotals(order),
      orderNumber: `1969-04-${Math.floor(Math.random() * 90000 + 10000)}`,
      message: 'Order placed (mock mode)',
    };
  }
  return request<OrderResponse>('/api/order', {
    method: 'POST',
    body: JSON.stringify(order),
  });
}

export interface BuildOrderRequestInput {
  storeId: string;
  registerId: string;
  associateId: string;
  lineItems: OrderLineItem[];
  promotionCodes: string[];
  tenderType: Tender['type'];
  tenderLabel: string;
  amountDue: number;
  services: ServiceFee[];
  taxExempt: boolean;
  taxExemptCertificate?: string;
  fulfillment?: Record<string, FulfillmentType>;
  /** Fulfilling store per SKU for SHIP_FROM_STORE lines (see `shipFromStoreFor`). */
  shipFromStoreIds?: Record<string, string>;
  receiptDelivery?: ReceiptDelivery | null;
}

/**
 * Nearest nearby store that can ship `item` (enough on hand for the line quantity), or null when
 * the SKU is not ship-from-store eligible at this store or no nearby store has the stock.
 */
export function shipFromStoreFor(item: OrderLineItem): string | null {
  if (!item.inventory.shipFromStoreEligible) return null;
  const source = [...item.inventory.nearbyStores]
    .sort((a, b) => a.distanceMiles - b.distanceMiles)
    .find((store) => store.onHand >= item.quantity);
  return source?.storeId ?? null;
}

/** Builds the POS-3 `OrderRequest`. Prices are never sent; order-service re-prices every line. */
export function buildOrderRequest(input: BuildOrderRequestInput): OrderRequest {
  const certificate = input.taxExemptCertificate?.trim();
  return {
    storeId: input.storeId,
    registerId: input.registerId,
    associateId: input.associateId,
    lineItems: input.lineItems.map((item) => {
      const fulfillmentType = input.fulfillment?.[item.skuCode] ?? 'TAKE_WITH';
      const shipFrom = input.shipFromStoreIds?.[item.skuCode];
      if (fulfillmentType === 'SHIP_FROM_STORE' && !shipFrom) {
        throw new Error(`No ship-from-store source store for ${item.skuCode}`);
      }
      return {
        skuCode: item.skuCode,
        quantity: item.quantity,
        fulfillmentType,
        fulfillmentStoreId: fulfillmentType === 'SHIP_FROM_STORE' ? shipFrom : input.storeId,
      };
    }),
    promotions: input.promotionCodes,
    tenders: [{ type: input.tenderType, label: input.tenderLabel, amount: input.amountDue }],
    taxExempt: input.taxExempt,
    services: input.services,
    receiptDelivery: input.receiptDelivery ?? null,
    taxExemptCertificate: input.taxExempt && certificate ? certificate : null,
  };
}

const COMPARED_TOTALS: Array<keyof OrderTotals> = [
  'merchandiseTotal',
  'discountTotal',
  'servicesAndFees',
  'taxableSubtotal',
  'salesTax',
  'total',
];

export interface TotalsMismatch {
  field: keyof OrderTotals;
  preview: number;
  server: number;
}

/** Fields where the client preview and the server-priced totals differ by at least a cent. */
export function totalsMismatches(preview: OrderTotals, server: OrderTotals): TotalsMismatch[] {
  return COMPARED_TOTALS.flatMap((field) => {
    const a = Number(preview[field]);
    const b = Number(server[field]);
    return Math.abs(a - b) >= 0.005 ? [{ field, preview: a, server: b }] : [];
  });
}
