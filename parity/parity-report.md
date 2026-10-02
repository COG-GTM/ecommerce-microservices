# Totals parity report: POS `calculateTotals` vs order-service `/api/order/quote`

- Generated: 2026-10-02T02:43:29.115Z
- Target: `http://localhost:8081/api/order/quote`
- Old: `pos-webapp/src/lib/totals.ts` `calculateTotals`, with list/sale price and finalSale from the canonical catalog, `PROMO_CATALOG` from `StoreCheckout.tsx`, `TAX_RATE` from `api/config.ts` and `MOCK_SERVICES_AND_FEES` from `api/fixtures.ts`.
- New: order-service `POST /api/order/quote` (the request body is the cart `request` as is).
- Money fields are compared to the cent, `taxRate` exactly and `taxExempt` as a boolean.
- Result: **51/51 carts match**

## Carts

### 01-pos-mockup: MATCH

POS mockup cart: the 3 fixture SKUs (MOCK_LINE_ITEMS) with FALL30, fees 12, not exempt

Lines: 268341-016-L x1, 471902-004-29 x1, 512884-022-M x1. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 159.85 | 159.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 63.54 | 63.54 | 0 |
| taxableSubtotal | 108.31 | 108.31 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 9.34 | 9.34 | 0 |
| total | 117.65 | 117.65 | 0 |
| savedToday | 63.54 | 63.54 | 0 |
| taxExempt | false | false | 0 |

### 02-single-268341-016-XS: MATCH

268341-016-XS alone, qty 1, no promotions

Lines: 268341-016-XS x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 11.98 | 11.98 | 0 |
| taxableSubtotal | 29.97 | 29.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.58 | 2.58 | 0 |
| total | 32.55 | 32.55 | 0 |
| savedToday | 11.98 | 11.98 | 0 |
| taxExempt | false | false | 0 |

### 03-single-268341-016-S: MATCH

268341-016-S alone, qty 1, no promotions

Lines: 268341-016-S x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 11.98 | 11.98 | 0 |
| taxableSubtotal | 29.97 | 29.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.58 | 2.58 | 0 |
| total | 32.55 | 32.55 | 0 |
| savedToday | 11.98 | 11.98 | 0 |
| taxExempt | false | false | 0 |

### 04-single-268341-016-M: MATCH

268341-016-M alone, qty 1, no promotions

Lines: 268341-016-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 11.98 | 11.98 | 0 |
| taxableSubtotal | 29.97 | 29.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.58 | 2.58 | 0 |
| total | 32.55 | 32.55 | 0 |
| savedToday | 11.98 | 11.98 | 0 |
| taxExempt | false | false | 0 |

### 05-single-268341-016-L: MATCH

268341-016-L alone, qty 1, no promotions

Lines: 268341-016-L x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 11.98 | 11.98 | 0 |
| taxableSubtotal | 29.97 | 29.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.58 | 2.58 | 0 |
| total | 32.55 | 32.55 | 0 |
| savedToday | 11.98 | 11.98 | 0 |
| taxExempt | false | false | 0 |

### 06-single-268341-016-XL: MATCH

268341-016-XL alone, qty 1, no promotions

Lines: 268341-016-XL x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 11.98 | 11.98 | 0 |
| taxableSubtotal | 29.97 | 29.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.58 | 2.58 | 0 |
| total | 32.55 | 32.55 | 0 |
| savedToday | 11.98 | 11.98 | 0 |
| taxExempt | false | false | 0 |

### 07-single-268341-001-XS: MATCH

268341-001-XS alone, qty 1, no promotions

Lines: 268341-001-XS x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 41.95 | 41.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.57 | 45.57 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 08-single-268341-001-S: MATCH

268341-001-S alone, qty 1, no promotions

Lines: 268341-001-S x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 41.95 | 41.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.57 | 45.57 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 09-single-268341-001-M: MATCH

268341-001-M alone, qty 1, no promotions

Lines: 268341-001-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 41.95 | 41.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.57 | 45.57 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 10-single-268341-001-L: MATCH

268341-001-L alone, qty 1, no promotions

Lines: 268341-001-L x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 41.95 | 41.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.57 | 45.57 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 11-single-268341-001-XL: MATCH

268341-001-XL alone, qty 1, no promotions

Lines: 268341-001-XL x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 41.95 | 41.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.57 | 45.57 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 12-single-471902-004-27: MATCH

471902-004-27 alone, qty 1, no promotions

Lines: 471902-004-27 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 69.95 | 69.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 81.95 | 81.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 7.07 | 7.07 | 0 |
| total | 89.02 | 89.02 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 13-single-471902-004-28: MATCH

471902-004-28 alone, qty 1, no promotions

Lines: 471902-004-28 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 69.95 | 69.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 81.95 | 81.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 7.07 | 7.07 | 0 |
| total | 89.02 | 89.02 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 14-single-471902-004-29: MATCH

471902-004-29 alone, qty 1, no promotions

Lines: 471902-004-29 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 69.95 | 69.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 81.95 | 81.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 7.07 | 7.07 | 0 |
| total | 89.02 | 89.02 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 15-single-471902-004-30: MATCH

471902-004-30 alone, qty 1, no promotions

Lines: 471902-004-30 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 69.95 | 69.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 81.95 | 81.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 7.07 | 7.07 | 0 |
| total | 89.02 | 89.02 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 16-single-512884-022-S: MATCH

512884-022-S alone, qty 1, no promotions

Lines: 512884-022-S x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 59.95 | 59.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 17.98 | 17.98 | 0 |
| taxableSubtotal | 53.97 | 53.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 4.65 | 4.65 | 0 |
| total | 58.62 | 58.62 | 0 |
| savedToday | 17.98 | 17.98 | 0 |
| taxExempt | false | false | 0 |

### 17-single-512884-022-M: MATCH

512884-022-M alone, qty 1, no promotions

Lines: 512884-022-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 59.95 | 59.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 17.98 | 17.98 | 0 |
| taxableSubtotal | 53.97 | 53.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 4.65 | 4.65 | 0 |
| total | 58.62 | 58.62 | 0 |
| savedToday | 17.98 | 17.98 | 0 |
| taxExempt | false | false | 0 |

### 18-single-512884-022-L: MATCH

512884-022-L alone, qty 1, no promotions

Lines: 512884-022-L x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 59.95 | 59.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 17.98 | 17.98 | 0 |
| taxableSubtotal | 53.97 | 53.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 4.65 | 4.65 | 0 |
| total | 58.62 | 58.62 | 0 |
| savedToday | 17.98 | 17.98 | 0 |
| taxExempt | false | false | 0 |

### 19-single-512884-022-XL: MATCH

512884-022-XL alone, qty 1, no promotions

Lines: 512884-022-XL x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 59.95 | 59.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 17.98 | 17.98 | 0 |
| taxableSubtotal | 53.97 | 53.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 4.65 | 4.65 | 0 |
| total | 58.62 | 58.62 | 0 |
| savedToday | 17.98 | 17.98 | 0 |
| taxExempt | false | false | 0 |

### 20-single-603115-110-S: MATCH

603115-110-S alone, qty 1, no promotions

Lines: 603115-110-S x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 49.95 | 49.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 61.95 | 61.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 5.34 | 5.34 | 0 |
| total | 67.29 | 67.29 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 21-single-603115-110-M: MATCH

603115-110-M alone, qty 1, no promotions

Lines: 603115-110-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 49.95 | 49.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 61.95 | 61.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 5.34 | 5.34 | 0 |
| total | 67.29 | 67.29 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 22-single-603115-110-L: MATCH

603115-110-L alone, qty 1, no promotions

Lines: 603115-110-L x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 49.95 | 49.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 0.00 | 0.00 | 0 |
| taxableSubtotal | 61.95 | 61.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 5.34 | 5.34 | 0 |
| total | 67.29 | 67.29 | 0 |
| savedToday | 0.00 | 0.00 | 0 |
| taxExempt | false | false | 0 |

### 23-single-734420-300-XS: MATCH

734420-300-XS alone, qty 1, no promotions

Lines: 734420-300-XS x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 39.95 | 39.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 41.97 | 41.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.59 | 45.59 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 24-single-734420-300-S: MATCH

734420-300-S alone, qty 1, no promotions

Lines: 734420-300-S x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 39.95 | 39.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 41.97 | 41.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.59 | 45.59 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 25-single-734420-300-M: MATCH

734420-300-M alone, qty 1, no promotions

Lines: 734420-300-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 39.95 | 39.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 41.97 | 41.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.62 | 3.62 | 0 |
| total | 45.59 | 45.59 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 26-single-845006-105-0-3M: MATCH

845006-105-0-3M alone, qty 1, no promotions

Lines: 845006-105-0-3M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 19.95 | 19.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 21.97 | 21.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 1.89 | 1.89 | 0 |
| total | 23.86 | 23.86 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 27-single-845006-105-3-6M: MATCH

845006-105-3-6M alone, qty 1, no promotions

Lines: 845006-105-3-6M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 19.95 | 19.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 21.97 | 21.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 1.89 | 1.89 | 0 |
| total | 23.86 | 23.86 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 28-single-845006-105-6-12M: MATCH

845006-105-6-12M alone, qty 1, no promotions

Lines: 845006-105-6-12M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 19.95 | 19.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 9.98 | 9.98 | 0 |
| taxableSubtotal | 21.97 | 21.97 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 1.89 | 1.89 | 0 |
| total | 23.86 | 23.86 | 0 |
| savedToday | 9.98 | 9.98 | 0 |
| taxExempt | false | false | 0 |

### 29-single-912377-210-2: MATCH

912377-210-2 alone, qty 1, no promotions

Lines: 912377-210-2 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 44.95 | 44.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 26.97 | 26.97 | 0 |
| taxableSubtotal | 29.98 | 29.98 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.59 | 2.59 | 0 |
| total | 32.57 | 32.57 | 0 |
| savedToday | 26.97 | 26.97 | 0 |
| taxExempt | false | false | 0 |

### 30-single-912377-210-4: MATCH

912377-210-4 alone, qty 1, no promotions

Lines: 912377-210-4 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 44.95 | 44.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 26.97 | 26.97 | 0 |
| taxableSubtotal | 29.98 | 29.98 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.59 | 2.59 | 0 |
| total | 32.57 | 32.57 | 0 |
| savedToday | 26.97 | 26.97 | 0 |
| taxExempt | false | false | 0 |

### 31-single-912377-210-6: MATCH

912377-210-6 alone, qty 1, no promotions

Lines: 912377-210-6 x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 44.95 | 44.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 26.97 | 26.97 | 0 |
| taxableSubtotal | 29.98 | 29.98 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.59 | 2.59 | 0 |
| total | 32.57 | 32.57 | 0 |
| savedToday | 26.97 | 26.97 | 0 |
| taxExempt | false | false | 0 |

### 32-multi-qty-hoodie-x3-fall30: MATCH

One SKU at qty 3 with FALL30

Lines: 512884-022-L x3. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 179.85 | 179.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 91.71 | 91.71 | 0 |
| taxableSubtotal | 100.14 | 100.14 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 8.64 | 8.64 | 0 |
| total | 108.78 | 108.78 | 0 |
| savedToday | 91.71 | 91.71 | 0 |
| taxExempt | false | false | 0 |

### 33-multi-qty-mixed-card10: MATCH

Several non-final-sale lines at qty 2-4 with CARD10

Lines: 471902-004-28 x2, 603115-110-M x4, 734420-300-S x2. Promotions: CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 419.60 | 419.60 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 59.92 | 59.92 | 0 |
| taxableSubtotal | 371.68 | 371.68 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 32.06 | 32.06 | 0 |
| total | 403.74 | 403.74 | 0 |
| savedToday | 59.92 | 59.92 | 0 |
| taxExempt | false | false | 0 |

### 34-all-final-sale-fall30: MATCH

Only final-sale lines with FALL30: promotion must not apply

Lines: 268341-016-M x2, 845006-105-3-6M x1, 912377-210-4 x1. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 124.80 | 124.80 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 60.91 | 60.91 | 0 |
| taxableSubtotal | 75.89 | 75.89 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 6.55 | 6.55 | 0 |
| total | 82.44 | 82.44 | 0 |
| savedToday | 60.91 | 60.91 | 0 |
| taxExempt | false | false | 0 |

### 35-all-final-sale-stacked: MATCH

Only final-sale lines with FALL30+CARD10: neither promotion applies

Lines: 268341-016-XS x1, 845006-105-0-3M x3. Promotions: FALL30 + CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 89.80 | 89.80 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 41.92 | 41.92 | 0 |
| taxableSubtotal | 59.88 | 59.88 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 5.16 | 5.16 | 0 |
| total | 65.04 | 65.04 | 0 |
| savedToday | 41.92 | 41.92 | 0 |
| taxExempt | false | false | 0 |

### 36-stacked-fall30-card10: MATCH

Mockup cart with FALL30 then CARD10 stacked

Lines: 268341-016-L x1, 471902-004-29 x1, 512884-022-M x1. Promotions: FALL30 + CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 159.85 | 159.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 74.73 | 74.73 | 0 |
| taxableSubtotal | 97.12 | 97.12 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 8.38 | 8.38 | 0 |
| total | 105.50 | 105.50 | 0 |
| savedToday | 74.73 | 74.73 | 0 |
| taxExempt | false | false | 0 |

### 37-stacked-card10-fall30: MATCH

Mockup cart with CARD10 then FALL30 (order must not matter)

Lines: 268341-016-L x1, 471902-004-29 x1, 512884-022-M x1. Promotions: CARD10 + FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 159.85 | 159.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 74.73 | 74.73 | 0 |
| taxableSubtotal | 97.12 | 97.12 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 8.38 | 8.38 | 0 |
| total | 105.50 | 105.50 | 0 |
| savedToday | 74.73 | 74.73 | 0 |
| taxExempt | false | false | 0 |

### 38-tax-exempt-mockup: MATCH

Mockup cart with FALL30, tax exempt

Lines: 268341-016-L x1, 471902-004-29 x1, 512884-022-M x1. Promotions: FALL30. Tax exempt: true.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 159.85 | 159.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 63.54 | 63.54 | 0 |
| taxableSubtotal | 108.31 | 108.31 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 0.00 | 0.00 | 0 |
| total | 108.31 | 108.31 | 0 |
| savedToday | 63.54 | 63.54 | 0 |
| taxExempt | true | true | 0 |

### 39-tax-exempt-stacked-mixed: MATCH

Mixed cart with FALL30+CARD10, tax exempt

Lines: 268341-001-L x2, 512884-022-XL x1, 912377-210-6 x1. Promotions: FALL30 + CARD10. Tax exempt: true.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 164.80 | 164.80 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 85.70 | 85.70 | 0 |
| taxableSubtotal | 91.10 | 91.10 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 0.00 | 0.00 | 0 |
| total | 91.10 | 91.10 | 0 |
| savedToday | 85.70 | 85.70 | 0 |
| taxExempt | true | true | 0 |

### 40-no-promos-mockup: MATCH

Mockup cart without promotions

Lines: 268341-016-L x1, 471902-004-29 x1, 512884-022-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 159.85 | 159.85 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 29.96 | 29.96 | 0 |
| taxableSubtotal | 141.89 | 141.89 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 12.24 | 12.24 | 0 |
| total | 154.13 | 154.13 | 0 |
| savedToday | 29.96 | 29.96 | 0 |
| taxExempt | false | false | 0 |

### 41-no-promos-mixed-qty: MATCH

Mixed cart, qty > 1, no promotions

Lines: 603115-110-S x2, 845006-105-6-12M x2, 734420-300-M x1. Promotions: none. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 179.75 | 179.75 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 29.94 | 29.94 | 0 |
| taxableSubtotal | 161.81 | 161.81 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 13.96 | 13.96 | 0 |
| total | 175.77 | 175.77 | 0 |
| savedToday | 29.94 | 29.94 | 0 |
| taxExempt | false | false | 0 |

### 42-penny-half-cent-promo-fall30: MATCH

Promo lands exactly on a half cent (29.95 x 30% = 8.985)

Lines: 268341-001-M x1. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 8.99 | 8.99 | 0 |
| taxableSubtotal | 32.96 | 32.96 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 2.84 | 2.84 | 0 |
| total | 35.80 | 35.80 | 0 |
| savedToday | 8.99 | 8.99 | 0 |
| taxExempt | false | false | 0 |

### 43-penny-half-cent-promo-card10: MATCH

Promo lands exactly on a half cent (29.95 x 10% = 2.995)

Lines: 268341-001-S x1. Promotions: CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 29.95 | 29.95 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 3.00 | 3.00 | 0 |
| taxableSubtotal | 38.95 | 38.95 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 3.36 | 3.36 | 0 |
| total | 42.31 | 42.31 | 0 |
| savedToday | 3.00 | 3.00 | 0 |
| taxExempt | false | false | 0 |

### 44-penny-half-cent-discount-tee-x9: MATCH

Discount lands exactly on a half cent (269.55 x 30% = 80.865)

Lines: 268341-001-XL x9. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 269.55 | 269.55 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 80.86 | 80.86 | 0 |
| taxableSubtotal | 200.69 | 200.69 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 17.31 | 17.31 | 0 |
| total | 218.00 | 218.00 | 0 |
| savedToday | 80.86 | 80.86 | 0 |
| taxExempt | false | false | 0 |

### 45-penny-half-cent-discount-jean-x11: MATCH

Discount lands exactly on a half cent (769.45 x 10% = 76.945)

Lines: 471902-004-30 x11. Promotions: CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 769.45 | 769.45 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 76.94 | 76.94 | 0 |
| taxableSubtotal | 704.51 | 704.51 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 60.76 | 60.76 | 0 |
| total | 765.27 | 765.27 | 0 |
| savedToday | 76.94 | 76.94 | 0 |
| taxExempt | false | false | 0 |

### 46-penny-half-cent-discount-markdown-mix: MATCH

Markdown + promo land on a half cent (3 clearance tees + 1 full-price tee, CARD10)

Lines: 268341-016-S x3, 268341-001-M x1. Promotions: CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 119.80 | 119.80 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 38.93 | 38.93 | 0 |
| taxableSubtotal | 92.87 | 92.87 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 8.01 | 8.01 | 0 |
| total | 100.88 | 100.88 | 0 |
| savedToday | 38.93 | 38.93 | 0 |
| taxExempt | false | false | 0 |

### 47-penny-half-cent-discount-and-tax: MATCH

Discount on a half cent, which also moves the tax by a cent (jean + baby bodysuit, CARD10)

Lines: 471902-004-27 x1, 845006-105-0-3M x1. Promotions: CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 89.90 | 89.90 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 16.97 | 16.97 | 0 |
| taxableSubtotal | 84.93 | 84.93 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 7.33 | 7.33 | 0 |
| total | 92.26 | 92.26 | 0 |
| savedToday | 16.97 | 16.97 | 0 |
| taxExempt | false | false | 0 |

### 48-penny-half-cent-tax-172: MATCH

Taxable 172.00 so the tax is exactly 14.835

Lines: 512884-022-S x3, 734420-300-XS x2, 845006-105-3-6M x3. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 319.60 | 319.60 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 159.60 | 159.60 | 0 |
| taxableSubtotal | 172.00 | 172.00 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 14.84 | 14.84 | 0 |
| total | 186.84 | 186.84 | 0 |
| savedToday | 159.60 | 159.60 | 0 |
| taxExempt | false | false | 0 |

### 49-penny-half-cent-tax-180: MATCH

Taxable 180.00 so the tax is exactly 15.525

Lines: 268341-016-L x1, 512884-022-M x3, 734420-300-S x2, 845006-105-6-12M x2. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 329.60 | 329.60 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 161.60 | 161.60 | 0 |
| taxableSubtotal | 180.00 | 180.00 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 15.52 | 15.52 | 0 |
| total | 195.52 | 195.52 | 0 |
| savedToday | 161.60 | 161.60 | 0 |
| taxExempt | false | false | 0 |

### 50-large-all-30-skus-stacked: MATCH

Large cart: every canonical SKU once, FALL30+CARD10

Lines: 268341-016-XS x1, 268341-016-S x1, 268341-016-M x1, 268341-016-L x1, 268341-016-XL x1, 268341-001-XS x1, 268341-001-S x1, 268341-001-M x1, 268341-001-L x1, 268341-001-XL x1, 471902-004-27 x1, 471902-004-28 x1, 471902-004-29 x1, 471902-004-30 x1, 512884-022-S x1, 512884-022-M x1, 512884-022-L x1, 512884-022-XL x1, 603115-110-S x1, 603115-110-M x1, 603115-110-L x1, 734420-300-XS x1, 734420-300-S x1, 734420-300-M x1, 845006-105-0-3M x1, 845006-105-3-6M x1, 845006-105-6-12M x1, 912377-210-2 x1, 912377-210-4 x1, 912377-210-6 x1. Promotions: FALL30 + CARD10. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 1283.50 | 1283.50 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 607.49 | 607.49 | 0 |
| taxableSubtotal | 688.01 | 688.01 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 59.34 | 59.34 | 0 |
| total | 747.35 | 747.35 | 0 |
| savedToday | 607.49 | 607.49 | 0 |
| taxExempt | false | false | 0 |

### 51-large-high-qty-fall30: MATCH

Large cart: 10 lines at qty 2-6 with FALL30

Lines: 268341-016-XL x6, 268341-001-XS x5, 471902-004-29 x4, 512884-022-S x3, 603115-110-L x2, 734420-300-M x6, 845006-105-6-12M x5, 912377-210-2 x4, 471902-004-30 x3, 512884-022-XL x2. Promotions: FALL30. Tax exempt: false.

| Field | Old (POS) | New (order-service) | Diff |
|---|---|---|---|
| merchandiseTotal | 1738.00 | 1738.00 | 0 |
| servicesAndFees | 12.00 | 12.00 | 0 |
| discountTotal | 718.13 | 718.13 | 0 |
| taxableSubtotal | 1031.87 | 1031.87 | 0 |
| taxRate | 0.08625 | 0.08625 | 0 |
| salesTax | 89.00 | 89.00 | 0 |
| total | 1120.87 | 1120.87 | 0 |
| savedToday | 718.13 | 718.13 | 0 |
| taxExempt | false | false | 0 |
