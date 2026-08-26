# Testing guide

Test conventions for this repository, ported from the layered structure used in
`spring-boot-realworld-example-app`.

## Running the tests

```bash
mvn -B verify                 # every module, plus a JaCoCo report per module
mvn -B verify -pl order-service
```

Testcontainers-based tests need a running Docker daemon. They are skipped only when Docker is
unavailable, in which case `verify` fails — do not weaken a test to work around a missing daemon.

## Layers

Each module mirrors its `src/main/java` package layout under `src/test/java` and tests four
layers. Pick the narrowest layer that can express the behaviour.

| Layer | Annotation | What it covers |
| --- | --- | --- |
| Domain / mapping | none (plain JUnit + Mockito) | Entity and DTO logic, service classes with mocked collaborators |
| Web slice | `@WebMvcTest(XController.class)` + RestAssured MockMvc | Status codes, request binding, JSON response shape |
| Persistence slice | `@DataJpaTest` / `@DataMongoTest` on a container base class | Repository queries against the real engine |
| Integration | `@SpringBootTest` on an integration base class | End-to-end flow through the whole application context |

## Base classes

Every module that needs a container has a base class under
`com.ibatulanand.<service>.support`, playing the same role as `DbTestBase` in the realworld app:

- `product-service`: `AbstractMongoTest` (`@DataMongoTest`) and `AbstractIntegrationTest`
  (`@SpringBootTest` + `@AutoConfigureMockMvc`), both backed by a shared `MongoDBContainer`.
- `inventory-service` / `order-service`: `AbstractMySqlTest` (`@DataJpaTest`) and
  `AbstractIntegrationTest`, both backed by a shared `MySQLContainer`.

Containers are `static` and started once per JVM, so all tests in a module reuse a single
database. Do not declare containers inside individual test classes.

Fixtures live in a single `TestFixtures` class per module, mirroring `TestHelper` in the
realworld app. Build test data there rather than inline, so a DTO change touches one file.

## Naming

Test methods read `should_<expected>_<condition>`, matching the realworld suite:

```java
@Test
void should_place_order_when_all_items_in_stock() { ... }

@Test
void should_reject_order_when_item_out_of_stock() { ... }
```

Test classes are named after the class under test: `OrderServiceTest`, `OrderControllerTest`,
`OrderRepositoryTest`, `OrderApiIntegrationTest`.

## Test configuration

Each module has `src/test/resources/application-test.properties`, activated by
`@ActiveProfiles("test")` on the base classes. It disables Eureka registration and tracing
export so tests never reach out to infrastructure that is not part of the test. Datasource and
broker addresses come from `@DynamicPropertySource` on the base class, never from a properties
file.
