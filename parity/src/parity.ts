import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { calculateTotals } from '../../pos-webapp/src/lib/totals.ts';
import { MOCK_SERVICES_AND_FEES } from '../../pos-webapp/src/api/fixtures.ts';
import type { OrderLineItem, OrderTotals, Promotion } from '../../pos-webapp/src/api/types.ts';

const PARITY_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const REPO_DIR = resolve(PARITY_DIR, '..');
const CATALOG_PATH = join(REPO_DIR, 'order-service/src/test/resources/gap-catalog.json');
const STORE_CHECKOUT_PATH = join(REPO_DIR, 'pos-webapp/src/screens/StoreCheckout.tsx');
const CARTS_DIR = join(PARITY_DIR, 'carts');
const EXPECTED_DIR = join(PARITY_DIR, 'expected');
const REPORT_PATH = join(PARITY_DIR, 'parity-report.md');

const MONEY_FIELDS = [
  'merchandiseTotal',
  'servicesAndFees',
  'discountTotal',
  'taxableSubtotal',
  'salesTax',
  'total',
  'savedToday',
] as const;
const ALL_FIELDS = [
  'merchandiseTotal',
  'servicesAndFees',
  'discountTotal',
  'taxableSubtotal',
  'taxRate',
  'salesTax',
  'total',
  'savedToday',
  'taxExempt',
] as const satisfies ReadonlyArray<keyof OrderTotals>;
type Field = (typeof ALL_FIELDS)[number];

interface CatalogEntry {
  styleId: string;
  skuCode: string;
  name: string;
  description: string;
  department: string;
  category: string;
  colorName: string;
  colorCode: string;
  size: string;
  listPrice: number;
  salePrice: number;
  clearancePercent: number;
  finalSale: boolean;
}

interface Cart {
  id: string;
  description: string;
  request: {
    storeId: string;
    registerId: string;
    associateId: string;
    lineItems: Array<{ skuCode: string; quantity: number }>;
    promotions: string[];
    tenders: unknown[];
    taxExempt: boolean;
  };
}

interface FieldResult {
  field: Field;
  old: number | boolean;
  new: unknown;
  match: boolean;
}

interface CartResult {
  cart: Cart;
  old: OrderTotals;
  fields: FieldResult[];
  error?: string;
  staleExpected: boolean;
}

function parseArgs(argv: string[]): Record<string, string> {
  const args: Record<string, string> = {};
  for (const arg of argv) {
    const match = /^--([^=]+)(?:=(.*))?$/.exec(arg);
    if (match) args[match[1]] = match[2] ?? 'true';
  }
  return args;
}

function readJson<T>(path: string): T {
  return JSON.parse(readFileSync(path, 'utf8')) as T;
}

/** PROMO_CATALOG is module-private in StoreCheckout.tsx, so read it from the source instead of copying it. */
function loadPromoCatalog(): Record<string, Promotion> {
  const source = readFileSync(STORE_CHECKOUT_PATH, 'utf8');
  const block = /const PROMO_CATALOG[^=]*=\s*\{([\s\S]*?)\n\};/.exec(source);
  if (!block) throw new Error(`PROMO_CATALOG not found in ${STORE_CHECKOUT_PATH}`);
  const entry =
    /(\w+):\s*\{\s*code:\s*'([^']+)',\s*description:\s*'([^']*)',\s*percentOff:\s*([\d.]+),\s*amountOff:\s*([\d.]+)\s*\}/g;
  const catalog: Record<string, Promotion> = {};
  for (const m of block[1].matchAll(entry)) {
    catalog[m[1]] = {
      code: m[2],
      description: m[3],
      percentOff: Number(m[4]),
      amountOff: Number(m[5]),
    };
  }
  if (Object.keys(catalog).length === 0) throw new Error('PROMO_CATALOG parsed as empty');
  return catalog;
}

/** Mirrors client.ts lookupLineItem: unitPrice = salePrice, finalSale from the catalog. */
function toLineItem(product: CatalogEntry, quantity: number): OrderLineItem {
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
    inventory: null as unknown as OrderLineItem['inventory'],
  };
}

/** Mirrors PromotionsBar (trim + uppercase) and StoreCheckout.handleApplyPromotion (dedupe). */
function toPromotions(codes: string[], catalog: Record<string, Promotion>): Promotion[] {
  const applied: Promotion[] = [];
  for (const raw of codes) {
    const code = raw.trim().toUpperCase();
    const promo = catalog[code];
    if (!promo) throw new Error(`unknown promotion code ${raw}`);
    if (!applied.some((p) => p.code === promo.code)) applied.push(promo);
  }
  return applied;
}

function computeOld(
  cart: Cart,
  catalog: Map<string, CatalogEntry>,
  promos: Record<string, Promotion>,
): OrderTotals {
  const lineItems = cart.request.lineItems.map(({ skuCode, quantity }) => {
    const product = catalog.get(skuCode);
    if (!product) throw new Error(`${cart.id}: skuCode ${skuCode} is not in the canonical catalog`);
    return toLineItem(product, quantity);
  });
  return calculateTotals({
    lineItems,
    promotions: toPromotions(cart.request.promotions, promos),
    servicesAndFees: MOCK_SERVICES_AND_FEES,
    taxExempt: cart.request.taxExempt,
  });
}

const cents = (v: number) => Math.round(v * 100);

function compareField(field: Field, oldValue: number | boolean, newValue: unknown): boolean {
  if (field === 'taxExempt') return newValue === oldValue;
  if (typeof newValue !== 'number' || !Number.isFinite(newValue)) return false;
  if (field === 'taxRate') return newValue === oldValue;
  return cents(newValue) === cents(oldValue as number);
}

function fmt(field: Field, value: unknown): string {
  if (value === undefined) return '_missing_';
  if (typeof value === 'number' && (MONEY_FIELDS as readonly string[]).includes(field)) {
    return value.toFixed(2);
  }
  return String(value);
}

function fmtDiff(r: FieldResult): string {
  if (r.match) return '0';
  if (typeof r.old === 'number' && typeof r.new === 'number') {
    if (r.field === 'taxRate') return String(r.new - r.old);
    const d = (cents(r.new) - cents(r.old)) / 100;
    return `**${d > 0 ? '+' : ''}${d.toFixed(2)}**`;
  }
  return '**mismatch**';
}

async function quote(
  url: string,
  token: string | undefined,
  cart: Cart,
): Promise<Record<string, unknown>> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(cart.request),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`);
  const body = JSON.parse(text) as Record<string, unknown>;
  return (body.totals ?? body) as Record<string, unknown>;
}

function writeReport(results: CartResult[], target: string): void {
  const failing = results.filter((r) => r.error || r.fields.some((f) => !f.match));
  const lines: string[] = [];
  lines.push('# Totals parity report: POS `calculateTotals` vs order-service `/api/order/quote`', '');
  lines.push(`- Generated: ${new Date().toISOString()}`);
  lines.push(`- Target: \`${target}\``);
  lines.push(
    '- Old: `pos-webapp/src/lib/totals.ts` `calculateTotals`, with list/sale price and finalSale from the canonical catalog, ' +
      '`PROMO_CATALOG` from `StoreCheckout.tsx`, `TAX_RATE` from `api/config.ts` and `MOCK_SERVICES_AND_FEES` from `api/fixtures.ts`.',
  );
  lines.push('- New: order-service `POST /api/order/quote` (the request body is the cart `request` as is).');
  lines.push('- Money fields are compared to the cent, `taxRate` exactly and `taxExempt` as a boolean.');
  lines.push(
    `- Result: **${results.length - failing.length}/${results.length} carts match**` +
      (failing.length ? `; ${failing.length} differ: ${failing.map((r) => `\`${r.cart.id}\``).join(', ')}` : ''),
    '',
  );
  if (failing.length) {
    lines.push('## Differences', '', '| Cart | Field | Old | New | Diff |', '|---|---|---|---|---|');
    for (const r of failing) {
      if (r.error) lines.push(`| ${r.cart.id} | _request failed_ | | | ${r.error.replace(/\|/g, '\\|')} |`);
      for (const f of r.fields.filter((x) => !x.match)) {
        lines.push(
          `| ${r.cart.id} | ${f.field} | ${fmt(f.field, f.old)} | ${fmt(f.field, f.new)} | ${fmtDiff(f)} |`,
        );
      }
    }
    lines.push('');
  }
  lines.push('## Carts', '');
  for (const r of results) {
    const ok = !r.error && r.fields.every((f) => f.match);
    lines.push(`### ${r.cart.id}: ${ok ? 'MATCH' : 'DIFF'}`, '');
    lines.push(`${r.cart.description}`, '');
    const items = r.cart.request.lineItems.map((l) => `${l.skuCode} x${l.quantity}`).join(', ');
    lines.push(
      `Lines: ${items}. Promotions: ${r.cart.request.promotions.join(' + ') || 'none'}. Tax exempt: ${r.cart.request.taxExempt}.`,
      '',
    );
    if (r.staleExpected) lines.push('> Warning: `parity/expected` is stale for this cart. Rerun `--mode=offline`.', '');
    if (r.error) lines.push(`> Request failed: ${r.error}`, '');
    lines.push('| Field | Old (POS) | New (order-service) | Diff |', '|---|---|---|---|');
    for (const f of r.fields) {
      lines.push(`| ${f.field} | ${fmt(f.field, f.old)} | ${fmt(f.field, f.new)} | ${fmtDiff(f)} |`);
    }
    lines.push('');
  }
  writeFileSync(REPORT_PATH, lines.join('\n'));
}

async function main(): Promise<number> {
  const args = parseArgs(process.argv.slice(2));
  const catalog = new Map(readJson<CatalogEntry[]>(CATALOG_PATH).map((p) => [p.skuCode, p]));
  const promos = loadPromoCatalog();
  const carts = readdirSync(CARTS_DIR)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => readJson<Cart>(join(CARTS_DIR, f)));

  if (args.mode === 'offline') {
    mkdirSync(EXPECTED_DIR, { recursive: true });
    for (const cart of carts) {
      const totals = computeOld(cart, catalog, promos);
      writeFileSync(join(EXPECTED_DIR, `${cart.id}.json`), `${JSON.stringify({ id: cart.id, totals }, null, 2)}\n`);
    }
    console.log(`Wrote ${carts.length} expected totals to ${EXPECTED_DIR}`);
    return 0;
  }

  const base = args.direct ?? args['base-url'];
  if (!base) {
    console.error('Usage: npm run parity -- --mode=offline | --base-url=http://localhost:8181 --token=JWT | --direct=http://localhost:8081');
    return 2;
  }
  const url = `${base.replace(/\/$/, '')}/api/order/quote`;
  const token = args.direct ? args.token : (args.token ?? process.env.PARITY_TOKEN);

  const results: CartResult[] = [];
  for (const cart of carts) {
    const old = computeOld(cart, catalog, promos);
    const expectedPath = join(EXPECTED_DIR, `${cart.id}.json`);
    const staleExpected =
      !existsSync(expectedPath) ||
      JSON.stringify(readJson<{ totals: OrderTotals }>(expectedPath).totals) !== JSON.stringify(old);
    let fresh: Record<string, unknown> = {};
    let error: string | undefined;
    try {
      fresh = await quote(url, token, cart);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
    const fields = ALL_FIELDS.map((field) => ({
      field,
      old: old[field],
      new: fresh[field],
      match: !error && compareField(field, old[field], fresh[field]),
    }));
    results.push({ cart, old, fields, error, staleExpected });
    const bad = fields.filter((f) => !f.match).map((f) => f.field);
    console.log(`${error || bad.length ? 'DIFF ' : 'MATCH'} ${cart.id}${error ? ` (${error})` : bad.length ? ` [${bad.join(', ')}]` : ''}`);
  }

  writeReport(results, url);
  const failing = results.filter((r) => r.error || r.fields.some((f) => !f.match)).length;
  console.log(`${results.length - failing}/${results.length} carts match. Report: ${REPORT_PATH}`);
  return failing === 0 ? 0 : 1;
}

main().then(
  (code) => process.exit(code),
  (err) => {
    console.error(err);
    process.exit(2);
  },
);
