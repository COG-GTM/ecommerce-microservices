import { test, describe, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { MAX_BODY_BYTES, ORDER_PLACED_MESSAGE, createStorefrontServer } from '../src/server.js';

const GATEWAY = 'http://gateway.test';
let server;
let baseUrl;
let gateway;
let errors;

function gatewayFetch(url, init = {}) {
  gateway.calls.push({ url, init });
  const handler = gateway.routes[`${init.method ?? 'GET'} ${url.replace(GATEWAY, '')}`];
  if (!handler) return Promise.reject(new Error(`unexpected gateway call ${url}`));
  return Promise.resolve(handler(init));
}

const response = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  text: async () => body,
});

before(async () => {
  server = createStorefrontServer({
    gatewayUrl: GATEWAY,
    getToken: async () => {
      if (gateway.tokenError) throw new Error('keycloak down');
      return 'token-123';
    },
    fetchImpl: gatewayFetch,
    logger: { error: (message) => errors.push(message) },
  });
  await new Promise((resolve) => server.listen(0, resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(() => new Promise((resolve) => server.close(resolve)));

beforeEach(() => {
  gateway = { calls: [], routes: {}, tokenError: false };
  errors = [];
});

const checkoutBody = {
  items: [
    { skuCode: 'iphone_15', name: 'iPhone 15', price: 799.99, quantity: 2 },
    { skuCode: 'case', price: '19.50', quantity: 1 },
  ],
};

function postCheckout(body) {
  return fetch(`${baseUrl}/api/checkout`, { method: 'POST', body: typeof body === 'string' ? body : JSON.stringify(body) });
}

describe('static assets and health', () => {
  test('GET /healthz reports UP', async () => {
    const res = await fetch(`${baseUrl}/healthz`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { status: 'UP' });
  });

  for (const [path, type, marker] of [
    ['/', 'text/html', '<title>Ecommerce Storefront</title>'],
    ['/app.js', 'text/javascript', "from '/cart.js'"],
    ['/cart.js', 'text/javascript', 'export function cartTotals'],
    ['/styles.css', 'text/css', 'font-family'],
  ]) {
    test(`GET ${path} serves ${type}`, async () => {
      const res = await fetch(`${baseUrl}${path}`);
      assert.equal(res.status, 200);
      assert.match(res.headers.get('content-type'), new RegExp(type));
      assert.match(await res.text(), new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    });
  }

  test('unknown paths return 404 and unsupported methods 405', async () => {
    assert.equal((await fetch(`${baseUrl}/../package.json`)).status, 404);
    assert.equal((await fetch(`${baseUrl}/secret`)).status, 404);
    assert.equal((await fetch(`${baseUrl}/api/products`, { method: 'DELETE' })).status, 405);
    assert.deepEqual(errors, []);
  });
});

describe('GET /api/products', () => {
  test('proxies the catalog through the gateway with a bearer token', async () => {
    const products = [{ id: '1', name: 'iPhone 15', price: 799.99, skuCode: 'iphone_15' }];
    gateway.routes['GET /api/product'] = () => response(200, products);

    const res = await fetch(`${baseUrl}/api/products`);

    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), products);
    assert.equal(gateway.calls[0].init.headers.Authorization, 'Bearer token-123');
  });

  test('maps gateway errors to 502 and logs them', async () => {
    gateway.routes['GET /api/product'] = () => response(401, null);

    const res = await fetch(`${baseUrl}/api/products`);

    assert.equal(res.status, 502);
    assert.match((await res.json()).message, /HTTP 401/);
    assert.equal(errors.length, 1);
  });

  test('maps token/network failures to 502', async () => {
    gateway.tokenError = true;
    const res = await fetch(`${baseUrl}/api/products`);
    assert.equal(res.status, 502);
    assert.deepEqual(await res.json(), { status: 'ERROR', message: 'keycloak down' });
  });
});

describe('POST /api/checkout', () => {
  test('places the order and returns a confirmation with totals', async () => {
    gateway.routes['POST /api/order'] = () => response(201, ORDER_PLACED_MESSAGE);

    const res = await postCheckout(checkoutBody);
    const body = await res.json();

    assert.equal(res.status, 201);
    assert.equal(body.status, 'CONFIRMED');
    assert.equal(body.message, ORDER_PLACED_MESSAGE);
    assert.equal(body.itemCount, 3);
    assert.equal(body.totalCents, 2 * 79999 + 1950);
    assert.equal(body.items[1].name, 'case');
    const sent = gateway.calls[0];
    assert.equal(sent.init.headers.Authorization, 'Bearer token-123');
    assert.equal(sent.init.headers['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(sent.init.body), {
      orderLineItemsDtoList: [
        { skuCode: 'iphone_15', price: '799.99', quantity: 2 },
        { skuCode: 'case', price: '19.50', quantity: 1 },
      ],
    });
  });

  test('reports the order-service fallback (out of stock / inventory down) as REJECTED', async () => {
    const fallback = 'Oops! Something went wrong, please order after some time!';
    gateway.routes['POST /api/order'] = () => response(201, fallback);

    const res = await postCheckout(checkoutBody);

    assert.equal(res.status, 409);
    assert.deepEqual(await res.json(), { status: 'REJECTED', message: fallback, itemCount: 3, totalCents: 161948 });
  });

  test('treats non-2xx gateway responses as REJECTED even with the success text', async () => {
    gateway.routes['POST /api/order'] = () => response(503, ORDER_PLACED_MESSAGE);
    assert.equal((await postCheckout(checkoutBody)).status, 409);
  });

  for (const [name, body, pattern] of [
    ['invalid JSON', '{not json', /valid JSON/],
    ['missing items', {}, /non-empty items/],
    ['empty items', { items: [] }, /non-empty items/],
    ['zero quantity', { items: [{ skuCode: 'a', price: 1, quantity: 0 }] }, /Quantity/],
    ['negative price', { items: [{ skuCode: 'a', price: -1, quantity: 1 }] }, /Invalid price/],
  ]) {
    test(`rejects ${name} with 400 without calling the gateway`, async () => {
      const res = await postCheckout(body);
      assert.equal(res.status, 400);
      assert.match((await res.json()).message, pattern);
      assert.equal(gateway.calls.length, 0);
    });
  }

  test('rejects oversized bodies with 413', async () => {
    const res = await postCheckout('x'.repeat(MAX_BODY_BYTES + 1));
    assert.equal(res.status, 413);
    assert.equal(gateway.calls.length, 0);
  });
});
