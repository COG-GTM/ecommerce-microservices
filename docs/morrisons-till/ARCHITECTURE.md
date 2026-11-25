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
| product-service | `barcode`, `skuCode`, `pricePence`, `quickKey` on `Product`; `GET /api/product/barcode/{barcode}`, `GET /api/product?quickkey=true`; `GroceryCatalogueSeeder` (8 items, `catalogue.seed.grocery=true`) |
| inventory-service | seeds the 8 grocery SKUs (500 each) when absent |
| order-service | `POST /api/order/sale` (synchronous, returns the order; idempotent on `idempotencyKey`), `GET /api/order/{orderNumber}`, `GET /api/order/transaction/{transactionId}`; till metadata (`channel`, `storeNumber`, `tillId`, `transactionId`, `tenderType`, `totalPence`) on order and on the Kafka `OrderPlacedEvent` |
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
3. **Strangling:** each Jira ticket replaces or adds one capability behind the facade (VAT and multi-buy as
   `PricingRule`s, card declines behind `PaymentTerminal`, store-and-forward as a queued `OrderClient`) and adds its
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
| POST | `/basket/lines` | `{ basketId, barcode, quantity?, weightGrams? }` | `Basket` (same barcode again increments the line; weighed items and price-embedded labels always add a new line) |
| PATCH | `/basket/lines/{lineId}` | `{ basketId, quantity }` | `Basket` |
| DELETE | `/basket/lines/{lineId}?basketId=` | - | `Basket` (void line) |
| POST | `/basket/lines/{lineId}/age-check` | `{ basketId, decision: APPROVED\|REFUSED }` | `Basket` (Challenge 25: APPROVED records the till operator as `ageApprovedBy`; REFUSED voids the line) |
| POST | `/basket/void` | `{ basketId }` | new empty `Basket` (void sale) |
| POST | `/basket/tender` | `{ basketId, tenderType: CARD\|CONTACTLESS\|CASH, amountTenderedPence?, terminalOutcome?: APPROVED\|DECLINED\|TIMEOUT, idempotencyKey? }` | `TenderResult` |
| POST | `/basket/member` | `{ basketId, cardNumber }` (More Card, seeded test cards `9826000000000001`/`…0002`) | `Basket` with `member`; 404 `UNKNOWN_CARD` leaves the sale non-member |
| GET | `/refund/{reference}` | - | `RefundableSale` (reference: receipt `R-...`, transaction or order number) |
| POST | `/refund` | `{ reference, tillId?, lines: [{ skuCode, quantity }] }` | `RefundResult`; `409 REFUND_EXCEEDS_SOLD` beyond what is left |

`tillId` is optional (default `07`). Each till id has its own basket, which is how parallel E2E tests stay isolated.
`terminalOutcome` drives the simulated PIN pad. A real terminal adapter ignores it. `TIMEOUT` waits
`till.terminal.simulator.timeout-delay-ms` (default 1500) and models a charge whose response was lost: status `TIMEOUT`,
basket still OPEN and locked until the same `idempotencyKey` is retried. A repeated attempt key replays the first
result, so a retry never charges or orders twice; a different key, cash or a basket change gets 409 `PAYMENT_UNRESOLVED`.

### JSON shapes

```jsonc
// TillSession
{ "storeNumber": "0417", "storeName": "Bradford Demo", "tillId": "07",
  "operator": { "id": "OP-1001", "name": "Demo Operator" }, "currency": "GBP",
  "transactionNumber": "0417-07-0001", "basketId": "b0c1…", "backendConnected": true,
  "services": { "product-service": "UP", "inventory-service": "UP", "order-service": "UP" } }

// Product
{ "barcode": "1001", "skuCode": "MOR-MILK-SS-4PT", "name": "Semi skimmed milk 4 pints", "pricePence": 155, "quickKey": true }

// Basket
{ "basketId": "b0c1…", "tillId": "07", "transactionNumber": "0417-07-0001", "status": "OPEN", "currency": "GBP",
  "lines": [ { "lineId": "l-1", "barcode": "1004", "skuCode": "MOR-BANANA-5", "name": "Bananas x5",
               "quantity": 2, "unitPricePence": 95, "linePence": 190, "pricedBy": "EACH", "weightGrams": null } ],
  "adjustments": [ { "code": "MULTIBUY", "label": "…", "amountPence": -50, "lineId": "l-1" } ],
  "lastLineId": "l-1", "itemCount": 2, "subtotalPence": 190, "savingsPence": 0, "totalPence": 190, "orderNumber": null,
  "member": null }  // More Card attached: { "cardLast4": "0001", "name": "Test Member One", "pointsBalance": 1200 }

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
  "moreCard": null }  // member sale: { "cardLast4": "0001", "memberName": "Test Member One", "pointsEarned": 20, "pointsBalance": 1220 }

// Error (any 4xx/5xx)
{ "error": "UNKNOWN_BARCODE", "message": "No product for barcode 9999" }
```

Error codes: `UNKNOWN_BARCODE` / `UNKNOWN_BASKET` / `UNKNOWN_LINE` (404), `OUT_OF_STOCK` / `BASKET_CLOSED` (409),
`EMPTY_BASKET` / `INSUFFICIENT_TENDER` (422), `UNSUPPORTED_TENDER` / validation (400).
Age-restricted items (`Product.minimumAge` set): the line gets `minimumAge` and `ageCheck: PENDING`, and the basket
reports `pendingAgeChecks`. `tender` returns `AGE_CHECK_REQUIRED` (409) until each such line is decided via `age-check`;
`INVALID_AGE_DECISION` (400), `NOT_AGE_RESTRICTED` (422).

Weighed and loose items (MBA-3935): a product with `soldByWeight: true` has `pricePence` per kg. Lines carry
`pricedBy` (`EACH` | `WEIGHT` | `LABEL`) and `weightGrams` (WEIGHT only; `unitPricePence` is then per kg and
`linePence` = weight × price, half-up to the penny). Price-embedded labels are prefix-2 EAN-13 `2 IIIIII PPPPP C`
(item/PLU, price in pence, check digit), e.g. `2002101003490` = PLU 2101 at £3.49. Weighed and labelled lines are
quantity 1. Errors: `INVALID_CHECK_DIGIT`, `WEIGHT_REQUIRED` (UI prompts for kg), `WEIGHT_TOO_LOW`,
`QUANTITY_NOT_ALLOWED` (422); `NOT_SOLD_BY_WEIGHT` (422 for a label on a unit-priced item, 400 for a weight).

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

Promotion products (MBA-3934; scan only, not quick keys). Offers are set in till-service `till.promotions.*`: Greek
yoghurt 3 for £3.50, still water/cola 2 for £2.00, and a £3.50 meal deal (sandwich + crisps/fruit + water/cola).

| Barcode | SKU | Item | Pence |
| --- | --- | --- | --- |
| 393401 | MOR-SANDWICH-CHKBAC | Chicken and bacon sandwich | 300 |
| 393402 | MOR-SANDWICH-EGGMAYO | Egg mayo sandwich | 250 |
| 393403 | MOR-CRISPS-SALTED-30G | Ready salted crisps 30g | 85 |
| 393404 | MOR-FRUIT-POT | Fruit pot | 120 |
| 393405 | MOR-WATER-STILL-500 | Still water 500ml | 90 |
| 393406 | MOR-COLA-500 | Cola 500ml | 135 |
| 393407 | MOR-YOGHURT-GREEK-500 | Greek style yoghurt 500g | 150 |

## Extension points for the till tickets

| Ticket | Where it plugs in |
| --- | --- |
| VAT, multi-buys, More Card loyalty | new `PricingRule` bean(s), which add `Adjustment`s (savings) or VAT lines; the UI already renders `savingsPence` |
| Weighed items, age-restricted items | product fields plus a line attribute; a `TillService.addLine` guard returns a new error code |
| Cash/change, card terminal declines and timeouts | `TillService.tender`, `PaymentTerminal` (decline reasons in `declineReason`, retries keyed by `idempotencyKey`) |
| Refunds | `/api/till/refund/**` (MBA-3939); order-service stores a negative `REFUND` order linked to the sale and emits a `REFUND` event |
| Offline store-and-forward | decorate `OrderClient` with a durable queue |
| Legacy price-feed bridge | alternative `CatalogueClient` or a product-service feed importer |
