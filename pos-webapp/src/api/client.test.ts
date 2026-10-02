import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { MOCK_PRODUCTS } from './fixtures';
import { getProductBySku, lookupLineItem } from './client';

vi.mock('./config', () => ({
  API_BASE_URL: 'http://api.test',
  USE_MOCK_DATA: false,
  AUTH_TOKEN: undefined,
  STORE_ID: '1969',
}));

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('getProductBySku (live mode)', () => {
  const other = MOCK_PRODUCTS[0];
  const target = MOCK_PRODUCTS[1];

  it('returns the exact matching product', async () => {
    expect(other.skuCode).not.toBe(target.skuCode);
    fetchMock.mockResolvedValue(jsonResponse([other, target]));

    await expect(getProductBySku(target.skuCode)).resolves.toBe(target);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      `http://api.test/api/product?skuCode=${encodeURIComponent(target.skuCode)}`,
      expect.any(Object),
    );
  });

  it('encodes the sku', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await getProductBySku('A B/1');

    expect(fetchMock).toHaveBeenCalledWith(
      'http://api.test/api/product?skuCode=A%20B%2F1',
      expect.any(Object),
    );
  });

  it('returns null when the response contains other products', async () => {
    fetchMock.mockResolvedValue(jsonResponse([other]));

    await expect(getProductBySku('000000-000-XX')).resolves.toBeNull();
  });

  it('returns null when the response is empty', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getProductBySku('000000-000-XX')).resolves.toBeNull();
  });

  it('rejects on an HTTP error', async () => {
    fetchMock.mockResolvedValue(jsonResponse([], 500));

    await expect(getProductBySku('000000-000-XX')).rejects.toThrow(
      'GET /api/product?skuCode=000000-000-XX failed: 500',
    );
  });
});

describe('lookupLineItem (live mode)', () => {
  it('returns null for an unknown sku without requesting inventory', async () => {
    fetchMock.mockResolvedValue(jsonResponse([MOCK_PRODUCTS[0]]));

    await expect(lookupLineItem('000000-000-XX')).resolves.toBeNull();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
