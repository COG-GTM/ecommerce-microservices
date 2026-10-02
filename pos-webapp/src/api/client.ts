import { API_BASE_URL, STORE_ID, USE_MOCK_DATA } from './config';
import { getAccessToken } from '../auth/accessToken';
import {
  MOCK_INVENTORY,
  MOCK_PRODUCTS,
  mockLineItem,
} from './fixtures';
import type {
  Inventory,
  OrderLineItem,
  OrderRequest,
  OrderResponse,
  Product,
  QuoteResponse,
} from './types';

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function errorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown };
    return typeof body.message === 'string' && body.message ? body.message : fallback;
  } catch {
    return fallback;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((init?.headers as Record<string, string>) ?? {}),
  };
  const token = await getAccessToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    const fallback = `${init?.method ?? 'GET'} ${path} failed: ${response.status}`;
    throw new ApiError(await errorMessage(response, fallback), response.status);
  }
  return (await response.json()) as T;
}

export async function getProducts(): Promise<Product[]> {
  if (USE_MOCK_DATA) return MOCK_PRODUCTS;
  return request<Product[]>('/api/product');
}

export async function getProductBySku(skuCode: string): Promise<Product | null> {
  if (USE_MOCK_DATA) {
    return MOCK_PRODUCTS.find((p) => p.skuCode === skuCode) ?? null;
  }
  const products = await request<Product[]>(
    `/api/product?skuCode=${encodeURIComponent(skuCode)}`,
  );
  return products.find((p) => p.skuCode === skuCode) ?? products[0] ?? null;
}

export async function getInventory(
  skuCode: string,
  storeId: string = STORE_ID,
): Promise<Inventory | null> {
  if (USE_MOCK_DATA) return MOCK_INVENTORY[skuCode] ?? null;
  try {
    return await request<Inventory>(
      `/api/inventory?skuCode=${encodeURIComponent(skuCode)}&storeId=${encodeURIComponent(storeId)}`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
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

/** Server-side pricing for the current bag; only used against the live gateway. */
export async function quoteOrder(order: OrderRequest): Promise<QuoteResponse> {
  return request<QuoteResponse>('/api/order/quote', {
    method: 'POST',
    body: JSON.stringify(order),
  });
}

export async function placeOrder(order: OrderRequest): Promise<OrderResponse> {
  if (USE_MOCK_DATA) {
    return {
      orderNumber: `1969-04-${Math.floor(Math.random() * 90000 + 10000)}`,
      status: 'COMPLETED',
      message: 'Order placed (mock mode)',
    };
  }
  return request<OrderResponse>('/api/order', {
    method: 'POST',
    body: JSON.stringify(order),
  });
}
