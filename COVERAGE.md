# Test coverage: before and after

How it was measured: JaCoCo 0.8.12 on Java 17 for the six Spring Boot modules, plus `node --test --experimental-test-coverage` for the new `storefront`. Line and branch counts come from each module's `target/site/jacoco/jacoco.csv`. Lombok-generated code is excluded by `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`, which JaCoCo skips). The same rule was applied to the baseline so the before and after numbers are comparable.

- **Before:** `origin/master` with its existing tests, run under the JaCoCo agent.
- **After:** this branch, `mvn -B clean verify` (unit report, which the gate checks).

## Service layout

| Module | Language / build | Runtime dependencies | Tests on master |
|---|---|---|---|
| product-service | Java 17, Spring Boot 3.1, Maven | MongoDB | 1 Testcontainers context test (errored) |
| order-service | Java 17, Spring Boot 3.1, Maven | MySQL, Kafka producer, calls inventory-service over HTTP via Eureka, Resilience4j | 1 context test (errored, needed a live MySQL) |
| inventory-service | Java 17, Spring Boot 3.1, Maven | MySQL | 1 context test (errored, needed a live MySQL) |
| notification-service | Java 17, Spring Boot 3.1, Maven | Kafka consumer | none |
| api-gateway | Java 17, Spring Cloud Gateway, Maven | Keycloak (JWT), Eureka | none |
| discovery-server | Java 17, Eureka server, Maven | none | none |
| storefront (new) | Node 22, no runtime dependencies | api-gateway, Keycloak client-credentials | new |

Master had no CI workflow. All 3 of its existing tests errored, so none passed.

## Line / branch coverage

| Module | Before (lines) | Before % | After (lines) | After % | After branches |
|---|---|---|---|---|---|
| product-service | 12 / 23 | 52.2% | 25 / 25 | **100%** | n/a (no branches) |
| order-service | 3 / 35 | 8.6% | 35 / 35 | **100%** | 2 / 2 (100%) |
| inventory-service | 12 / 22 | 54.5% | 22 / 22 | **100%** | 2 / 2 (100%) |
| notification-service | 0 / 6 | 0.0% | 6 / 6 | **100%** | n/a |
| api-gateway | 0 / 12 | 0.0% | 12 / 12 | **100%** | n/a |
| discovery-server | 0 / 6 | 0.0% | 6 / 6 | **100%** | n/a |
| **Java overall** | **27 / 104** | **26.0%** | **106 / 106** | **100%** | **4 / 4 (100%)** |
| storefront (new) | — | — | 100% lines / branches / functions | **100%** | 100% |

Branch coverage before was 0 / 4 (0%). product-service gained 2 lines because of the new `skuCode` field, which the storefront needs to map catalog items to inventory SKUs.

### Justified excludes

- Lombok-generated accessors, builders and constructors (`@Generated`, via `lombok.config`). This is generated code with no logic of its own.
- `storefront/src/main.js` is the 3-line process entrypoint (`loadConfig(process.env)` → `listen`). All of its logic is in `config.js` and `server.js`, which are covered at 100%.

There are no other excludes. `@SpringBootApplication` `main` methods are covered by small unit tests.

## Tests per tier

| Tier | Where | Count | Real dependencies |
|---|---|---|---|
| Unit (Java) | `*/src/test/**/*Test.java` (Surefire) | 40 | mocks and stubs only |
| Unit (storefront) | `storefront/test/*.test.js` | 39 | none |
| Service integration | `*/src/test/**/*IT.java` (Failsafe) | 7 | Testcontainers: MongoDB 4.4, MySQL 8.0, Kafka (cp-kafka 7.5) |
| System integration | `system-tests/` (`-Psystem-tests`) | 6 | full `docker-compose.test.yml` topology, real HTTP through api-gateway with Keycloak JWT |
| Browser E2E | `e2e/tests/checkout.spec.ts` (Playwright, Chromium) | 3 | same composed stack, through `storefront` |

### Critical flows covered by the system and E2E tiers

- Anonymous gateway calls are rejected (401).
- A product created through the gateway is stored in MongoDB and listed.
- Order placement: gateway → order-service → inventory-service (HTTP via Eureka) → row written in MySQL → `OrderPlacedEvent` on Kafka `notificationTopic` → consumed by notification-service.
- An out-of-stock order is rejected with no order row and no Kafka event.
- A mixed cart (one line out of stock) is rejected as a whole, with no partial persistence. The order transaction rolls back.
- Inventory outage (container stopped): the circuit-breaker fallback answers with no side effects, and orders succeed again once inventory restarts.
- Browser: browse products → add to cart → checkout → order confirmation; cart quantity edits and removals; out-of-stock checkout rejected with the cart kept.

**Not present in the codebase, so not tested:** there is no payment service, and order-service does not decrement inventory (it only checks stock). Testing these would mean inventing new domain behavior first.

### Bug found by the system tier

Order placement always failed when running from the packaged jar. `OrderController` ran `orderService.placeOrder` through `CompletableFuture.supplyAsync(...)` on the common `ForkJoinPool`. Those threads use the system classloader, so Kafka could not load the configured `JsonSerializer` ("Failed to construct kafka producer"). Resilience4j then turned this into the generic fallback message. Unit tests and Testcontainers ITs run from an exploded classpath, so they could not see the bug. The fix runs the work on Spring's `applicationTaskExecutor`, and a unit test now pins that behavior.

## E2E recordings and artifacts

`e2e/playwright.config.ts` sets `use: { video: 'on', trace: 'on', screenshot: 'on' }`, so every test records itself on every run:

- Videos: `e2e/test-results/<test>/video.webm`
- Traces (open with `npx playwright show-trace`): `e2e/test-results/<test>/trace.zip`
- Screenshots: `e2e/test-results/<test>/test-finished-1.png`
- HTML report: `e2e/playwright-report/index.html`; JUnit: `e2e/test-results/junit.xml`

In CI, these are uploaded on every run (pass or fail) as the **`playwright-e2e-recordings`** artifact of the `CI` workflow (Actions run → Artifacts).

## CI gates (`.github/workflows/ci.yml`, runs on every PR)

1. **unit**: `mvn -B verify -DskipITs`. The JaCoCo `unit-coverage-gate` fails the build if line or branch `COVEREDRATIO` drops below `coverage.line.minimum` / `coverage.branch.minimum` (both `1.00`, set in the root `pom.xml`). It also runs `npm run test:coverage` in `storefront`, which uses node's built-in thresholds of 100% lines, branches and functions.
2. **integration**: `mvn -B verify -DskipUTs` (Testcontainers ITs).
3. **system-e2e**: `scripts/system-stack.sh up`, then `mvn -Psystem-tests verify -pl system-tests`, then `npx playwright test`. Uploads recordings and reports, then tears the stack down.

## Reproduce locally

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # Lombok in this repo fails on JDK 21
mvn -B clean verify                                   # unit + Testcontainers ITs + 100% gate
(cd storefront && npm run test:coverage)              # Node >= 22.8
scripts/system-stack.sh up                            # builds jars + images, waits for readiness
SYSTEM_TESTS_COMPOSE_FILE=$PWD/docker-compose.test.yml mvn -B -Psystem-tests verify -pl system-tests
(cd e2e && npm ci && npx playwright install chromium && npx playwright test)
scripts/system-stack.sh down
```
