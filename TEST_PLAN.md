# Micro Marketplace — TEST PLAN

This document describes how to bring up the full platform locally and verify it end-to-end.
It is self-contained: a fresh engineer (or agent) can execute it with no extra context.

Ticket reference: [WOR-58](https://cog-gtm.atlassian.net/browse/WOR-58)

## 1. Prerequisites

- Docker + Docker Compose
- `curl` and `jq`
- Add a hosts entry so Keycloak token issuer URLs resolve from the host:
  ```shell
  echo "127.0.0.1 keycloak" | sudo tee -a /etc/hosts
  ```

## 2. Environment Bring-Up

All infrastructure **and** all services are defined in `docker-compose.yml` (service images are
published to Docker Hub under `ibatulanandjp/*` and pulled automatically).

```shell
docker compose up -d
docker ps   # all containers should be Up
```

Start order is handled by `depends_on`: infrastructure (MySQL x2, MongoDB, Zookeeper/Kafka,
Keycloak+its MySQL, Zipkin) -> `discovery-server` -> `api-gateway` -> business services
(product/order/inventory/notification) -> Prometheus -> Grafana.

Wait until all four business services plus `api-gateway` are registered in Eureka
(http://localhost:8761) with status **UP**. This can take 1–3 minutes after the containers start.

Alternative (source build): `mvn spring-boot:run` per module — start `discovery-server` first,
then `api-gateway`, then the business services; infrastructure still comes from docker compose.
Note the local ports in each module's `application.properties` (gateway `8181`, order `8081`,
others use random ports and are reached through the gateway).

### Key endpoints

| Component | URL |
|---|---|
| API Gateway | http://localhost:8181 |
| Eureka dashboard | http://localhost:8761 |
| Keycloak admin | http://localhost:8080 (admin / admin) |
| Zipkin | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / password) |

## 3. Keycloak Token Acquisition

The gateway is an OAuth2 resource server for realm `spring-boot-microservices-realm`
(imported automatically from `realms/`). Use the `spring-cloud-client` client with the
Client Credentials grant. The client secret ships with the demo realm import — read it from
Keycloak admin UI (Clients -> spring-cloud-client -> Credentials) or from
`realms/spring-boot-microservices-realm.json`.

```shell
TOKEN=$(curl -s -X POST \
  "http://keycloak:8080/realms/spring-boot-microservices-realm/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-cloud-client" \
  -d "client_secret=<client-secret>" | jq -r .access_token)
```

Sanity check: a gateway call without a token must return `401`:

```shell
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8181/api/product   # expect 401
```

## 4. Golden-Path Scenarios (through the gateway)

All calls use `Authorization: Bearer $TOKEN`.

### 4.1 Create product

```shell
curl -s -X POST http://localhost:8181/api/product \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Iphone 15","description":"Apple Iphone 15","price":1500}' \
  -o /dev/null -w "%{http_code}\n"
```
Expected: `201`.

### 4.2 List products

```shell
curl -s http://localhost:8181/api/product -H "Authorization: Bearer $TOKEN" | jq .
```
Expected: `200` with a JSON array containing the created product (`id`, `name`, `description`, `price`).

### 4.3 Seed inventory

Order placement checks stock in inventory-service by SKU code. Seed a row for the SKU used below
(inventory-service exposes only a read API, so seed directly in MySQL):

```shell
docker exec mysql-inventory mysql -uibatulanand -ppassword inventory_service \
  -e "CREATE TABLE IF NOT EXISTS t_inventory (id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_code VARCHAR(255), quantity INT); \
      INSERT INTO t_inventory (sku_code, quantity) VALUES ('iphone_15', 10);"
```

### 4.4 Place order (in stock)

```shell
curl -s -X POST http://localhost:8181/api/order \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"orderLineItemsDtoList":[{"skuCode":"iphone_15","price":1500,"quantity":1}]}'
```
Expected: `201` with body `Order Placed Successfully!`.

### 4.5 Async notification via Kafka

The order producer publishes an `OrderPlacedEvent` to topic `notificationTopic`; notification-service
consumes it and logs the order number.

```shell
docker logs notification-service --tail 20 | grep "Received Notification for Order"
```
Expected: a log line `Received Notification for Order - <order-number>` appearing shortly after 4.4.

## 5. Negative Scenarios

### 5.1 Out-of-stock order

```shell
curl -s -X POST http://localhost:8181/api/order \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"orderLineItemsDtoList":[{"skuCode":"no_such_sku","price":10,"quantity":1}]}'
```
Expected: the fallback message `Oops! Something went wrong, please order after some time!`
(the service throws `IllegalArgumentException` for missing/zero stock, which triggers the
Resilience4j fallback in `OrderController`). No new notification appears in notification-service logs.

### 5.2 Circuit-breaker fallback (inventory down)

```shell
docker stop inventory-service
# place the valid order from 4.4 again
curl -s -X POST http://localhost:8181/api/order \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"orderLineItemsDtoList":[{"skuCode":"iphone_15","price":1500,"quantity":1}]}'
docker start inventory-service
```
Expected: the fallback message (no 5xx surfaced to the client). Circuit-breaker state is visible at
the order-service actuator (`/actuator/health` shows `circuitBreakers.details.inventory`).
After restarting inventory-service, wait for it to re-register in Eureka before re-running 4.4.

## 6. Observability Checks

- **Eureka** (http://localhost:8761): `PRODUCT-SERVICE`, `ORDER-SERVICE`, `INVENTORY-SERVICE`,
  `NOTIFICATION-SERVICE`, `API-GATEWAY` all show status **UP**.
- **Zipkin** (http://localhost:9411): after running 4.4, "Run Query" shows a trace spanning
  `api-gateway -> order-service -> inventory-service` with one shared trace ID.
- **Grafana** (http://localhost:3000): the provisioned dashboard (`grafana-dashboard.json`) renders
  request-rate/latency panels fed by Prometheus.

## 7. Scripted E2E Run

`scripts/e2e-golden-path.sh` executes sections 3–5 automatically against a running stack and prints
PASS/FAIL per scenario:

```shell
./scripts/e2e-golden-path.sh
```

## 8. Automated Test Suites

Each service module has unit and integration tests (JUnit 5 + Mockito + Testcontainers — MongoDB
for product-service, MySQL for order/inventory, Kafka for order producer and notification consumer).
They are self-contained (only Docker required):

```shell
mvn test            # all modules
mvn -pl order-service test   # single module
```
