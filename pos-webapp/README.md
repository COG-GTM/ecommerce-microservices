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
| `VITE_KEYCLOAK_URL` | `http://localhost:8080` | Keycloak base URL for the PKCE login |
| `VITE_KEYCLOAK_REALM` | `spring-boot-microservices-realm` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `pos-webapp` | Public Keycloak client (no secret) |
| `VITE_API_TOKEN` | _(empty)_ | Local-only override: a pasted access token skips the login redirect |

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

The gateway is an OAuth2 resource server backed by Keycloak. Every `/api/**`
call needs a bearer token from realm `spring-boot-microservices-realm` that
carries the `pos-associate` realm role (`realm_access.roles`):

| Request | Gateway response |
| --- | --- |
| Token with `pos-associate` | routed to the service |
| Valid token without `pos-associate` | `403` |
| No token / invalid token | `401` |
| CORS preflight `OPTIONS` (no token) | `200` |

With `VITE_USE_MOCK=false` the app signs in before it renders
(`src/api/auth.ts`, `keycloak-js`):

1. The browser is redirected to Keycloak (`http://localhost:8080`) using the
   **public** client `pos-webapp`: Authorization Code + PKCE (S256), redirect URI
   `http://localhost:5173/*`, direct-access grants disabled. There is no client
   secret in the bundle.
2. After login, the access token is kept in memory only and refreshed before it
   expires. `getAccessToken()` supplies it to every gateway request.
3. The token carries an `associate_id` claim (from the Keycloak user attribute)
   alongside the realm roles.

`VITE_USE_MOCK=true` never contacts Keycloak. Setting `VITE_API_TOKEN` in
`.env.local` skips the redirect and sends that token instead (local dev only;
`.env.local` is git-ignored, never commit a token).

**Issuer.** Keycloak runs with a fixed public hostname
(`KC_HOSTNAME=http://localhost:8080` in `docker-compose.yml`), so every token's
`iss` is `http://localhost:8080/realms/spring-boot-microservices-realm`. The
gateway validates that issuer and fetches signing keys from `jwk-set-uri`
(`http://keycloak:8080/...` inside compose, `http://localhost:8080/...` when run
from source).

#### Demo credentials

The realm import (`realms/spring-boot-microservices-realm.json`) creates two
users for local demos only. These passwords are demo values, not secrets; do not
reuse them anywhere real.

| Username | Password | Realm roles | `associate_id` |
| --- | --- | --- | --- |
| `associate1` | `pos-demo-associate` | `pos-associate` | `A-4471` |
| `noaccess1` | `pos-demo-noaccess` | _(none for POS)_ → 403 at the gateway | — |

The realm also defines `pos-manager` (a composite that includes
`pos-associate`).

#### Running the live path locally

```bash
docker compose up -d keycloak mongo        # realm is imported on first start
mvn -pl discovery-server spring-boot:run
mvn -pl product-service spring-boot:run
mvn -pl api-gateway spring-boot:run        # :8181
cd pos-webapp && VITE_USE_MOCK=false npm run dev
```

Open `http://localhost:5173`, sign in as `associate1`; the header shows
"Live gateway" and `GET http://localhost:8181/api/product` carries
`Authorization: Bearer ...`.

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
