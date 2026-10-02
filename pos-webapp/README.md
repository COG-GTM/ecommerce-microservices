# pos-webapp — Gap Store Checkout (POS)

React + TypeScript (Vite) point-of-sale checkout screen for the store associate:
hangtag lookup, bag line items with color/size/price/availability detail,
promotions, order summary with 8.625% sales tax, and tender selection.

This module is a standalone JS app. It is intentionally **not** part of the Maven
reactor and is not referenced by the root `pom.xml`.

## Running

```bash
cd pos-webapp
npm install
npm run dev      # http://localhost:5173
```

Other scripts:

```bash
npm run build      # type-check + production bundle into dist/
npm run test       # Vitest + React Testing Library
npm run lint       # oxlint
npm run typecheck  # tsc -b
```

## Configuration

Copy `.env.example` to `.env.local` and adjust:

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8181` | Spring Cloud Gateway base URL |
| `VITE_USE_MOCK` | `true` | `true` renders bundled fixtures; `false` calls the gateway |
| `VITE_KEYCLOAK_URL` | `http://localhost:8080` | Keycloak server URL |
| `VITE_KEYCLOAK_REALM` | `spring-boot-microservices-realm` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `gap-pos` | Public OpenID Connect client |

## API gateway integration

The app talks to the gateway (`api-gateway`, port 8181), which fronts
`product-service`, `order-service`, and `inventory-service`:

| Call | Endpoint |
| --- | --- |
| Product catalog | `GET /api/product` |
| Product by SKU | `GET /api/product?skuCode=...` |
| Store inventory | `GET /api/inventory?skuCode=...&storeId=...` |
| Place order | `POST /api/order` |

### CORS

The gateway's security-chain CORS configuration allows the Vite origins
`http://localhost:5173` and `http://127.0.0.1:5173` by default. Override them
with `APP_CORS_ALLOWED_ORIGINS` in the gateway environment. Only API routes
receive CORS headers, and only genuine preflight requests are permitted before
bearer-token authorization.

### Authentication

With `VITE_USE_MOCK=false`, the app signs in through Keycloak using the
authorization-code flow with PKCE S256 via `keycloak-js`. It checks for an
existing Keycloak session on load, silently refreshes access tokens for gateway
calls, and provides Sign out. The gateway listens on port **8181**; Keycloak is
on port **8080**. The token issuer is
`http://localhost:8080/realms/spring-boot-microservices-realm`, not the gateway
URL.

The imported demo accounts and their demo-only passwords are documented in
[`../realms/README.md`](../realms/README.md):

| Username | Password | POS access |
| --- | --- | --- |
| `associate1` | `GapDemo-Associate1` | Associate checkout |
| `manager1` | `GapDemo-Manager1` | Associate and manager checkout |
| `catalogadmin` | `GapDemo-Catalog1` | Product administration only |

Mock mode skips Keycloak entirely and immediately uses the bundled mock identity.
Never use the demo accounts outside local development.

## Mock mode

`VITE_USE_MOCK=true` (the default) serves the fixtures in `src/api/fixtures.ts`,
seeded with the exact values from the Store Checkout mockup. Full POS behaviour
depends on the extended product/inventory/order schemas from the companion
backend plan (style/color/size variants, clearance and final-sale flags,
per-store on-hand and nearby-store availability, ship-from-store eligibility,
order-level promotions, services and fees, and computed totals). Until those
schemas are merged, run the frontend in mock mode.

## Layout

```
src/
  api/        config, typed contracts (Product, Inventory, Order), client, fixtures
  components/ Header, ItemLookup, CartLineItem, PromotionsBar, OrderSummary, TenderPanel
  lib/        totals.ts — merchandise, markdowns, promotions, tax, total
  screens/    StoreCheckout.tsx — the composed checkout screen
```

Totals are computed by `calculateTotals`: merchandise is valued at list price,
markdowns and promotions accumulate into one discount total (promotions skip
final-sale lines), then
`taxable subtotal = merchandise − discounts + services & fees`, and
`total = taxable subtotal + sales tax`, with tax zeroed when the transaction is
tax exempt.
