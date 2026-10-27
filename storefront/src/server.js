// Backend-for-frontend: serves the static storefront and proxies catalog/checkout calls
// through the real api-gateway with a service-account bearer token.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cartTotals, toOrderRequest, validateQuantity, toCents } from './cart.js';

export const ORDER_PLACED_MESSAGE = 'Order Placed Successfully!';
export const MAX_BODY_BYTES = 64 * 1024;

const ROOT = fileURLToPath(new URL('..', import.meta.url));
const STATIC_FILES = {
  '/': join(ROOT, 'public', 'index.html'),
  '/app.js': join(ROOT, 'public', 'app.js'),
  '/styles.css': join(ROOT, 'public', 'styles.css'),
  '/cart.js': join(ROOT, 'src', 'cart.js'),
};
const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
};

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

export async function readJsonBody(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      throw new HttpError(413, 'Request body too large');
    }
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'Request body must be valid JSON');
  }
}

export function parseCheckoutItems(body) {
  if (!body || !Array.isArray(body.items) || body.items.length === 0) {
    throw new HttpError(400, 'Checkout requires a non-empty items array');
  }
  try {
    return body.items.map((item) => ({
      skuCode: String(item.skuCode),
      name: String(item.name ?? item.skuCode),
      priceCents: toCents(item.price),
      quantity: validateQuantity(item.quantity),
    }));
  } catch (error) {
    throw new HttpError(400, error.message);
  }
}

export function createApp({ gatewayUrl, getToken, fetchImpl = fetch, logger = console }) {
  async function callGateway(path, init = {}) {
    const token = await getToken();
    return fetchImpl(`${gatewayUrl}${path}`, {
      ...init,
      headers: { ...init.headers, Authorization: `Bearer ${token}` },
    });
  }

  async function listProducts(res) {
    const response = await callGateway('/api/product');
    if (!response.ok) {
      throw new HttpError(502, `Product service returned HTTP ${response.status}`);
    }
    sendJson(res, 200, await response.json());
  }

  async function checkout(req, res) {
    const items = parseCheckoutItems(await readJsonBody(req));
    const response = await callGateway('/api/order', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(toOrderRequest(items)),
    });
    const message = await response.text();
    const totals = cartTotals(items);
    if (response.ok && message === ORDER_PLACED_MESSAGE) {
      sendJson(res, 201, { status: 'CONFIRMED', message, ...totals, items });
    } else {
      sendJson(res, 409, { status: 'REJECTED', message, ...totals });
    }
  }

  async function serveStatic(pathname, res) {
    const file = STATIC_FILES[pathname];
    if (!file) {
      throw new HttpError(404, 'Not found');
    }
    res.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(file)] });
    res.end(await readFile(file));
  }

  return async function handle(req, res) {
    const { pathname } = new URL(req.url, 'http://storefront');
    try {
      if (req.method === 'GET' && pathname === '/healthz') {
        sendJson(res, 200, { status: 'UP' });
      } else if (req.method === 'GET' && pathname === '/api/products') {
        await listProducts(res);
      } else if (req.method === 'POST' && pathname === '/api/checkout') {
        await checkout(req, res);
      } else if (req.method === 'GET') {
        await serveStatic(pathname, res);
      } else {
        throw new HttpError(405, 'Method not allowed');
      }
    } catch (error) {
      const status = error instanceof HttpError ? error.status : 502;
      if (status >= 500) {
        logger.error(`storefront ${req.method} ${pathname} failed: ${error.message}`);
      }
      sendJson(res, status, { status: 'ERROR', message: error.message });
    }
  };
}

export function createStorefrontServer(options) {
  return createServer(createApp(options));
}
