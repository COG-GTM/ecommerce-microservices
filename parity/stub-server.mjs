#!/usr/bin/env node
/**
 * Dependency-free stub for product-service and inventory-service so the
 * order-service pricing endpoints can be exercised end to end locally.
 *
 *   node stub-server.mjs [--port=9501]
 *
 *   GET /api/product/sku/{skuCode}    -> product from gap-catalog.json (+ id, price, variants) or 404
 *   GET /api/inventory?skuCode=a&skuCode=b -> [{skuCode, isInStock: true}] for known skus
 */
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = resolve(dirname(fileURLToPath(import.meta.url)));
const catalog = JSON.parse(
  readFileSync(join(dir, '..', 'order-service', 'src', 'test', 'resources', 'gap-catalog.json'), 'utf8'),
);
const bySku = new Map(catalog.map((p) => [p.skuCode, p]));

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(a);
    return m ? [m[1], m[2] ?? 'true'] : [a, 'true'];
  }),
);
const port = Number(args.port ?? 9501);

function send(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(json) });
  res.end(json);
}

const server = createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const skuMatch = /^\/api\/product\/sku\/([^/]+)$/.exec(url.pathname);
  if (req.method === 'GET' && skuMatch) {
    const product = bySku.get(decodeURIComponent(skuMatch[1]));
    if (!product) {
      return send(res, 404, { status: 404, error: 'Not Found', message: `Unknown skuCode: ${skuMatch[1]}` });
    }
    return send(res, 200, {
      id: product.skuCode,
      ...product,
      price: product.salePrice,
      variants: [],
    });
  }
  if (req.method === 'GET' && url.pathname === '/api/inventory') {
    const skus = url.searchParams.getAll('skuCode');
    return send(res, 200, skus.map((skuCode) => ({ skuCode, isInStock: bySku.has(skuCode) })));
  }
  send(res, 404, { status: 404, error: 'Not Found', message: `${req.method} ${url.pathname}` });
});

server.listen(port, () => {
  console.log(`stub-server listening on http://localhost:${port} (${bySku.size} catalog skus)`);
});
