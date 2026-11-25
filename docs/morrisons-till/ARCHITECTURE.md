# Morrisons till: architecture and API contract

## Shape of the system

```
till-webapp (React, :5174)  --/api/till/**-->  api-gateway (:8181)  -->  till-service (lb://till-service)
                                                                         |-- product-service   (catalogue, MongoDB)
                                                                         |-- inventory-service (stock, MySQL)
                                                                         '-- order-service     (completed sale, MySQL)
                                                                                   '-- Kafka notificationTopic --> notification-service
```

All service-to-service calls go through Eureka (`lb://...`). The webapp only talks to the gateway (same origin `/api`,
proxied by nginx in the container and by Vite in `npm run dev`).

## Decision: a thin `till-service` facade

The existing services are e-commerce shaped (product, stock check, order). A till needs a basket that lives across
many requests, line edits, tender attempts that may be declined, and receipts. Rather than spreading that state across
product/order services, a new **thin `till-service`** owns:

- till session (store, till, operator, transaction number) and the **in-flight basket** (in memory, keyed by till id);
- **server-authoritative pricing in integer pence** (`BasketPricer` + `PricingRule` extension point);
- tender orchestration via a `PaymentTerminal` port (`SimulatedPaymentTerminal` today).

It owns no catalogue, stock or order data. It delegates through three small ports (`CatalogueClient`, `StockClient`,
`OrderClient`, implemented by `HttpServiceClients`), so a ticket can swap or decorate one without touching the others.

Changes to existing services are additive:

| Service | Change |
| --- | --- |
| product-service | `barcode`, `skuCode`, `pricePence`, `quickKey` on `Product`; `GET /api/product/barcode/{barcode}`, `GET /api/product?quickkey=true`; `vatCode`; `GroceryCatalogueSeeder` (8 quick keys plus 2 barcode-only VAT items, `catalogue.seed.grocery=true`) |
| inventory-service | seeds the 8 grocery SKUs and 2 VAT demo SKUs (500 each) when absent |
| order-service | `POST /api/order/sale` (synchronous, returns the order), `GET /api/order/{orderNumber}`; till metadata (`channel`, `storeNumber`, `tillId`, `transactionId`, `tenderType`, `totalPence`) on order and on the Kafka `OrderPlacedEvent` |
| notification-service | records consumed events in a bounded `NotificationLog`; `GET /api/notification`, `GET /api/notification/{orderNumber}` |
| api-gateway | routes `/api/till/**` and `/api/notification/**`; `dev` profile makes `/api/till/**` public and `GET /api/order/**`, `GET /api/notification/**` readable for tests. Everything else still requires a Keycloak JWT. |

Existing endpoints (`POST /api/order`, `GET/POST /api/product`, `GET /api/inventory`) are unchanged.

## Legacy bridge to modern services

The demo story is a store estate moving off a legacy till application one capability at a time:

1. **Today:** the till UI calls one stable contract, `/api/till/**`. Behind it, `till-service` already composes the modern
   services: catalogue from product-service, stock from inventory-service, the completed sale persisted by
   order-service, and the order event fanned out over Kafka to notification-service. Downstream systems such as
   loyalty, finance and replenishment consume events, not till internals.
2. **Bridging:** a legacy system (for example the nightly price file) is wrapped behind one of the ports. For example a
   `LegacyPriceFeedCatalogueClient` implements `CatalogueClient`, or a feed job writes into product-service. The till
   contract does not change.
3. **Strangling:** each Jira ticket replaces or adds one capability behind the facade (multi-buy as a
   `PricingRule`, VAT as `VatAnalysis` in `BasketPricer`, card declines behind `PaymentTerminal`, store-and-forward as a queued `OrderClient`) and adds its
   own E2E spec. The legacy path is retired once the spec passes against the modern path.

## Contract

Base path through the gateway: `http://localhost:8181/api/till`. All money is **integer pence, GBP**. Clients never
compute totals; they render `subtotalPence`, `savingsPence` and `totalPence` from the last response.

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/session?tillId=07` | - | `TillSession` (creates the till's open basket if none) |
| GET | `/products?quickkey=true` | - | `Product[]` (quick keys, barcode order) |
| GET | `/products/{barcode}` | - | `Product` |
| GET | `/basket/{basketId}` | - | `Basket` |
| POST | `/basket/lines` | `{ basketId, barcode, quantity? }` | `Basket` (same barcode again increments the line) |
| PATCH | `/basket/lines/{lineId}` | `{ basketId, quantity }` | `Basket` |
| DELETE | `/basket/lines/{lineId}?basketId=` | - | `Basket` (void line) |
| POST | `/basket/void` | `{ basketId }` | new empty `Basket` (void sale) |
| POST | `/basket/tender` | `{ basketId, tenderType: CARD\|CONTACTLESS\|CASH, amountTenderedPence?, terminalOutcome?: APPROVED\|DECLINED }` | `TenderResult` |

`tillId` is optional (default `07`). Each till id has its own basket, which is how parallel E2E tests stay isolated.
`terminalOutcome` drives the simulated PIN pad. A real terminal adapter ignores it.

### JSON shapes

```jsonc
// TillSession
{ "storeNumber": "0417", "storeName": "Bradford Demo", "tillId": "07",
  "operator": { "id": "OP-1001", "name": "Demo Operator" }, "currency": "GBP",
  "transactionNumber": "0417-07-0001", "basketId": "b0c1…", "backendConnected": true,
  "services": { "product-service": "UP", "inventory-service": "UP", "order-service": "UP" } }

// Product
{ "barcode": "1001", "skuCode": "MOR-MILK-SS-4PT", "name": "Semi skimmed milk 4 pints", "pricePence": 155, "quickKey": true, "vatCode": "Z" }

// Basket
{ "basketId": "b0c1…", "tillId": "07", "transactionNumber": "0417-07-0001", "status": "OPEN", "currency": "GBP",
  "lines": [ { "lineId": "l-1", "barcode": "1004", "skuCode": "MOR-BANANA-5", "name": "Bananas x5",
               "quantity": 2, "unitPricePence": 95, "linePence": 190, "vatCode": "Z" } ],
  "adjustments": [ { "code": "MULTIBUY", "label": "…", "amountPence": -50, "lineId": "l-1" } ],
  "lastLineId": "l-1", "itemCount": 2, "subtotalPence": 190, "savingsPence": 50, "totalPence": 140,
  "vatSummary": [ { "code": "Z", "ratePercent": 0, "netPence": 140, "vatPence": 0, "grossPence": 140 } ], "orderNumber": null }

// TenderResult
{ "status": "APPROVED", "tenderType": "CARD", "basket": { /* Basket, status COMPLETED */ },
  "orderNumber": "8df1…", "amountTenderedPence": 190, "changeDuePence": 0, "authCode": "419508",
  "declineReason": null, "receipt": { /* Receipt */ }, "nextBasketId": "c2d3…" }
// Declined: status DECLINED, basket still OPEN with all lines, orderNumber/receipt null, nextBasketId = same basket.

// Receipt
{ "receiptNumber": "R-0417-07-0001", "storeNumber": "0417", "storeName": "Bradford Demo", "tillId": "07",
  "operatorName": "Demo Operator", "transactionNumber": "0417-07-0001", "orderNumber": "8df1…",
  "lines": [ /* BasketLine */ ], "subtotalPence": 190, "savingsPence": 0, "totalPence": 190,
  "tenderType": "CARD", "amountTenderedPence": 190, "changeDuePence": 0, "authCode": "419508", "issuedAt": "2026-11-25T14:36:16Z",
  "vatSummary": [ /* VatBand, same as the basket */ ] }

// Error (any 4xx/5xx)
{ "error": "UNKNOWN_BARCODE", "message": "No product for barcode 9999" }
```

Error codes: `UNKNOWN_BARCODE` / `UNKNOWN_BASKET` / `UNKNOWN_LINE` (404), `OUT_OF_STOCK` / `BASKET_CLOSED` (409),
`EMPTY_BASKET` / `INSUFFICIENT_TENDER` (422), `UNSUPPORTED_TENDER` / validation (400).

### Downstream calls made by till-service

| Purpose | Call |
| --- | --- |
| scan | `GET lb://product-service/api/product/barcode/{barcode}` then `GET lb://inventory-service/api/inventory?skuCode=` |
| quick keys | `GET lb://product-service/api/product?quickkey=true` |
| approved tender | `POST lb://order-service/api/order/sale`, which checks stock, saves the order and publishes `notificationTopic` |

## Seeded catalogue

| Barcode | SKU | Item | Pence | VAT |
| --- | --- | --- | --- | --- |
| 1001 | MOR-MILK-SS-4PT | Semi skimmed milk 4 pints | 155 | Z |
| 1002 | MOR-BREAD-WM-800 | Wholemeal bread 800g | 110 | Z |
| 1003 | MOR-EGGS-FR-6 | Free range eggs x6 | 185 | Z |
| 1004 | MOR-BANANA-5 | Bananas x5 | 95 | Z |
| 1005 | MOR-OJ-1L | Orange juice 1L | 160 | S |
| 1006 | MOR-CHEDDAR-MAT-400 | Mature cheddar 400g | 350 | Z |
| 1007 | MOR-CHICKEN-BF-600 | Chicken breast fillets 600g | 475 | Z |
| 1008 | MOR-CARROT-1KG | Carrots 1kg | 65 | Z |
| 4001 | MOR-CHOC-BAR-100 | Milk chocolate bar 100g (barcode only) | 120 | S |
| 4002 | MOR-WASHUP-500 | Washing up liquid 500ml (barcode only) | 135 | S |

## UK VAT (MBA-3932)

Each product carries `vatCode`: `Z` (zero-rated, 0%) or `S` (standard-rated, 20%); a missing or unknown code is
treated as `S`. Shelf prices are VAT inclusive. Every basket and receipt carries `vatSummary`, one
`{ code, ratePercent, netPence, vatPence, grossPence }` per rate present, computed by `VatAnalysis` in `BasketPricer`.

Rounding: VAT is computed once per rate band, never per line: `vat = round_half_up(gross × rate / (100 + rate))`,
`net = gross − vat`. A band's gross is its lines plus any adjustments tied to those lines; basket-level adjustments
are apportioned across bands pro rata by gross (largest remainder), so band grosses always sum to `totalPence`.
Example: three 1p standard-rated lines give 1p VAT on the 3p band (per-line rounding would give 0p).

## Extension points for the till tickets

| Ticket | Where it plugs in |
| --- | --- |
| Multi-buys, More Card loyalty | new `PricingRule` bean(s), which add `Adjustment`s (savings); the UI already renders `savingsPence`, and `VatAnalysis` apportions them to VAT bands |
| VAT rates | `VatRate` (codes and rates) and `VatAnalysis` (per-band rounding), applied by `BasketPricer` after the rules |
| Weighed items, age-restricted items | product fields plus a line attribute; a `TillService.addLine` guard returns a new error code |
| Cash/change, card terminal declines | `TillService.tender`, `PaymentTerminal` (decline reasons in `declineReason`) |
| Refunds | new `/api/till/refund/**` endpoints plus an order-service refund call |
| Offline store-and-forward | decorate `OrderClient` with a durable queue |
| Legacy price-feed bridge | alternative `CatalogueClient` or a product-service feed importer |
