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
| `VITE_API_TOKEN` | _(empty)_ | Keycloak access token used for local dev requests |

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

The gateway previously had no CORS configuration. `api-gateway`'s `CorsConfig`
now registers a `CorsWebFilter` for all paths, with the allowed origins driven by
`app.cors.allowed-origins` (defaults to `http://localhost:5173` and
`http://127.0.0.1:5173`). Override for other environments with the
`APP_CORS_ALLOWED_ORIGINS` environment variable. Preflight `OPTIONS` requests are
permitted in the security filter chain so the browser can complete the handshake
before the bearer token is checked.

### Authentication

The gateway is an OAuth2 resource server backed by Keycloak
(`http://localhost:8181/realms/spring-boot-microservices-realm`), so every
`/api/**` call needs a bearer token. There is no login flow in this app yet. For
local development, obtain a token from Keycloak and put it in `.env.local` as
`VITE_API_TOKEN`:

```bash
curl -s -X POST \
  'http://localhost:8080/realms/spring-boot-microservices-realm/protocol/openid-connect/token' \
  -d 'grant_type=client_credentials' \
  -d "client_id=$KEYCLOAK_CLIENT_ID" \
  -d "client_secret=$KEYCLOAK_CLIENT_SECRET"
```

Never commit a token or client secret; `.env.local` is git-ignored.

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

## Live mode contracts (POS-5)

With `VITE_USE_MOCK=false` the client speaks the backend contracts frozen in
`src/api/types.ts` (checked at runtime by `src/api/contract.test.ts`):

| Call | Success | Errors |
| --- | --- | --- |
| `GET /api/product?skuCode=` | one `Product` object | `404 sku-not-found` → "No item found" |
| `GET /api/inventory?skuCode=&storeId=` | one `Inventory` row | `404` → "No item found" |
| `POST /api/order` | `201 OrderResponse` with server-priced `totals` | `409 out-of-stock`, `422 invalid-promotion` / `validation-failed`, `503 dependency-unavailable` (`application/problem+json`) |

- The bag starts empty and the request carries `services`, per-line
  `fulfillmentType` / `fulfillmentStoreId`, `receiptDelivery` and
  `taxExemptCertificate` (set in the "Fulfillment & receipt" panel). No prices are
  sent; order-service re-prices every line.
- The charge confirmation shows order-service's `totals`. Any difference from the
  register preview (`calculateTotals`) is shown as a warning.
- `problem+json` errors are shown in the UI with status, title, `type` slug and
  detail. Promo codes the register doesn't know are sent to the server and come
  back as `422 invalid-promotion` if they are not valid.
- Mock mode is unchanged: preloaded fixture bag, browser-side totals, no network.

The golden cart (3 fixture SKUs, `FALL30`, $12.00 services & fees, 8.625% tax)
totals 159.85 / 63.54 / 108.31 / 9.34 / **117.65** in both the client preview and
`MOCK_ORDER_RESPONSE`.
