# Morrisons till: running and testing

Everything runs locally. This repo has no GitHub CI.

## Prerequisites

- Docker with Compose v2 (tested on Docker 29).
- JDK 17 for the Maven build. `scripts/till/mvn.sh` finds it, or set `TILL_JAVA_HOME`. Lombok in this repo does not
  compile on JDK 21.
- Maven 3.8+, Node 20+.
- Docker 29 and Testcontainers: the BOM is pinned to Testcontainers `1.21.3`. Also set `api.version=1.44` in
  `~/.docker-java.properties`, otherwise MySQL Testcontainers tests cannot talk to the daemon.

## Commands

```bash
make till-up       # mvn package (JDK 17) + docker compose up --build --wait + gateway/till readiness poll
make till-e2e      # Playwright: api + desktop (1920x1080) + tablet (1024x768) projects against the running stack
make till-test     # till-service JUnit/MockMvc contract tests
make till-down     # stop and remove volumes
make till-logs     # follow container logs
make till-dev      # Vite hot-reload UI on :5174 against the gateway on :8181
make till-report   # open the last Playwright HTML report (till-webapp/e2e-report)
```

`make till-up` waits for the till to work end to end, then prints the URLs:

- Till: http://localhost:5174. Presenter mode with API chips: http://localhost:5174/?api=1
- Gateway: http://localhost:8181/api/till/session
- Eureka http://localhost:8761, Keycloak http://localhost:8080 (realm `spring-boot-microservices-realm`)

Useful variables: `TILL_SKIP_BUILD=1` reuses built jars, `TILL_WAIT_TIMEOUT` (seconds, default 420),
`TILL_URL` / `TILL_GATEWAY_URL` point Playwright at another stack.

### Auth

The stack starts the gateway with `SPRING_PROFILES_ACTIVE=dev`, so `/api/till/**` needs no login and tests read orders
and notifications with plain `GET`s. Every other route still needs a Keycloak JWT. The `api` spec asserts that
`GET /api/product` returns 401. To run the fully secured path, use
`TILL_GATEWAY_PROFILE=default make till-up`. The till routes then need a bearer token from the
`spring-boot-microservices-realm` realm.

### Pinned images

Kafka `cp-kafka:latest` exits on startup, so Zookeeper and the broker are pinned to `7.6.1`. Keycloak `24.0.5` imports
`realms/spring-boot-microservices-realm.json` (the file must be named `<realm>-realm.json`). MySQL is pinned to
`8.0.39` and Mongo to `7.0`.

## Test layers

| Layer | Where | Runs against |
| --- | --- | --- |
| Unit/contract (JUnit + MockMvc) | `till-service/src/test/java/**` | till-service with in-memory fake ports: pricing, tender rules, error codes |
| Backend API (Playwright `api` project) | `till-webapp/e2e/api/*.spec.ts` | real gateway and services: contract shapes, persistence, Kafka notification, secured routes |
| UI E2E (Playwright `desktop` / `tablet`) | `till-webapp/e2e/specs/*.spec.ts` | real browser, webapp, gateway and services. Nothing is mocked. |

Playwright records **video and a screenshot for every test** (`till-webapp/e2e-results/`) and writes an HTML report to
`till-webapp/e2e-report/`. Traces are kept on failure.

## Adding a scenario for your ticket (10 parallel PRs without conflicts)

Rules:

1. **One spec file per ticket**, named after it, for example `e2e/specs/mor-123-vat.spec.ts`, plus `e2e/api/mor-123-vat.spec.ts` for
   API assertions. Do not add cases to another ticket's spec or to the foundation specs (`basket`, `card`, `cash`,
   `till-api`).
2. **Import the shared fixtures**: `import { test, expect } from '../fixtures/till'`. You get:
   - `till`: a `TillPage` bound to a **unique till id**, so your basket is isolated from every other test and
     worker. Always start with `await till.open()`.
   - `backend`: read-side assertions on the real services (`backend.order(n)`, `backend.notification(n)`).
3. **Selectors live in page objects, not specs.** Use `TillPage` methods and `data-testid`s. If your ticket adds UI
   (for example an age-check dialog), add **a new page object file** `e2e/pages/AgeCheckDialog.ts` instead of growing
   `TillPage`. If you need a `TillPage` helper, add a single new method at the end of the class. Additive one-method
   edits rarely conflict.
4. **New fixtures go in their own file**, for example `e2e/fixtures/loyalty.ts`, which extends `test` from `./till`. Do not edit
   `till.ts` for ticket-specific setup.
5. **Data**: use the seeded barcodes `1001`-`1008`. If your ticket needs new products (weighed or age-restricted), seed them
   in product-service with a new barcode range per ticket (for example `2xxx` weighed, `3xxx` age-restricted), so seeds
   never collide.
6. Tag one end-to-end happy path with `@tablet` so it also runs on the 1024x768 project.
7. Tests must not depend on order or shared state. The suite runs `fullyParallel` on one stack.

Template:

```ts
// e2e/specs/mor-123-multibuy.spec.ts
import { expect, test } from '../fixtures/till'

test.describe('MOR-123 multi-buy', () => {
  test('3 for 2 on bananas shows savings and charges server total @tablet', async ({ till, backend }) => {
    await till.open()
    for (let i = 0; i < 3; i++) await till.quickKey('1004')
    await expect(till.savings).toHaveText('£0.95')
    await expect(till.total).toHaveText('£1.90')
    await till.pay()
    await till.payByCard('approved')
    const order = await backend.order(await till.orderNumber())
    expect(order.totalPence).toBe(190)
  })
})
```

Run only your spec while developing: `cd till-webapp && npx playwright test mor-123`.
