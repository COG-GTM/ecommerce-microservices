# POS totals parity

Compares the POS client-side pricing (`pos-webapp/src/lib/totals.ts` `calculateTotals`)
with the server-side engine in `order-service` (`PricingEngine`, exposed at
`POST /api/order/quote`) over the sample carts in `carts/`.

- `carts/*.json` — 51 sample carts (single/multi line, promos, tax-exempt, half-cent edge cases).
- `expected/*.json` — golden totals captured from the POS `calculateTotals` (offline mode).
  Every field must match the order-service result exactly; there is no allow-list.
- `pricing.rounding-mode` (`order-service`, default `LEGACY_POS`): `LEGACY_POS` reproduces
  the POS's IEEE-754 double arithmetic and `Math.round((v + EPSILON) * 100) / 100` rounding
  bit-for-bit. `HALF_UP` uses exact `BigDecimal` math and can differ on values that land
  exactly on a half cent (the double is slightly *below* the half cent and rounds down,
  while `HALF_UP` rounds up).
- `src/parity.ts` — the harness (vite-node).
- `stub-server.mjs` — dependency-free stub for product-service and inventory-service.

## Usage

```bash
npm ci

# Regenerate expected/ from the POS calculateTotals (run after touching totals.ts or carts)
npm run parity -- --mode=offline

# Through the API gateway (JWT optional)
npm run parity -- --base-url=http://localhost:8181 --token=$JWT

# Directly against order-service
npm run parity -- --direct=http://localhost:8081
```

The report is written to `parity-report.md`. Exit code 0 = all carts match; 1 = any diff.

## Local stack (live verification)

```bash
docker run -d --name parity-mysql -e MYSQL_ROOT_PASSWORD=mysql -p 3306:3306 mysql:8.0
docker run -d --name parity-kafka -p 9092:9092 apache/kafka:3.8.0
node parity/stub-server.mjs --port=9501

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
mvn -DskipTests install   # from the repo root

java -jar order-service/target/order-service-1.0-SNAPSHOT.jar \
  --eureka.client.enabled=false \
  --spring.cloud.discovery.client.simple.instances.product-service[0].uri=http://localhost:9501 \
  --spring.cloud.discovery.client.simple.instances.inventory-service[0].uri=http://localhost:9501

cd parity && npm run parity -- --direct=http://localhost:8081
```

Expected direct result: **51/51 carts match**, exit code 0.

## API surface

- `POST /api/order/quote` — priced cart + totals, no side effects. `X-Store-Id` header
  overrides `request.storeId`. Client-sent prices are ignored (unknown JSON fields dropped).
- `POST /api/order` — quote + tender validation + stock check + persist + Kafka event.
  Headers `X-Store-Id`, `X-Register-Id`, `X-Associate-Id`, `X-User-Roles` override the
  body; `taxExempt` requires the `pos-manager` role.
