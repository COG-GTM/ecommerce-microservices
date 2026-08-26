# WS-2 integration notes — `inventory-service` on Quarkus

Changes the coordinating session must apply to the files WS-2 is not allowed to touch
(`docker-compose.yml`, `prometheus/prometheus.yml`).

## `docker-compose.yml` — `inventory-service` service

Replace the Spring environment block with the Quarkus equivalents. Quarkus maps
`QUARKUS_*` env vars onto config keys automatically, so no application code or property
indirection is needed.

```yaml
  # Inventory Service Config
  inventory-service:
    container_name: inventory-service
    image: ibatulanandjp/inventory-service:latest
    pull_policy: always
    environment:
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql-inventory:3306/inventory_service?allowPublicKeyRetrieval=true&useSSL=false
      - QUARKUS_DATASOURCE_USERNAME=ibatulanand
      - QUARKUS_DATASOURCE_PASSWORD=password
      - QUARKUS_HTTP_PORT=8080
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
    depends_on:
      - mysql-inventory
      - otel-collector
      - api-gateway
```

Removed / replaced:

| Old | New |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `QUARKUS_DATASOURCE_JDBC_URL` |
| `SPRING_DATASOURCE_USERNAME` | `QUARKUS_DATASOURCE_USERNAME` |
| `SPRING_DATASOURCE_PASSWORD` | `QUARKUS_DATASOURCE_PASSWORD` |
| `SERVER_PORT=8080` | `QUARKUS_HTTP_PORT=8080` |
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans` | `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317` (OTLP gRPC; the collector forwards to the existing `zipkin` container) |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/` | **removed** — no discovery client in the module; registration is declarative via Consul (WS-5) |

`depends_on` should also drop `discovery-server` and drop `zipkin` in favour of the new
`otel-collector` container once it exists (tracing is exporter-only, so it is not a hard
dependency).

Extra flags Quarkus honours if needed: `QUARKUS_HIBERNATE_ORM_DATABASE_GENERATION` (defaults to
`update`, matching today's `ddl-auto=update`) and `INVENTORY_SEED_ENABLED=false` to switch off
the startup demo-data seeding.

## Build prerequisite: Maven >= 3.8.6 (affects every workstream)

The `quarkus-maven-plugin` cannot run under the Maven 3.6.3 that is preinstalled on the dev
image — `mvn -B -pl inventory-service test` fails during `generate-code-tests` with:

```
java.lang.NoSuchMethodError: 'boolean org.apache.maven.settings.Mirror.isBlocked()'
```

Quarkus 3.15 requires Maven 3.8.6 or newer; all gates pass under Maven 3.9.8 with JDK 17. Because
the reactor now contains a Quarkus module, this affects the **root** `mvn package` too, so it is a
program-wide prerequisite rather than a WS-2 detail. The coordinating session should either add a
Maven wrapper at the repo root (`./mvnw`, pinned to 3.9.x — root-level file, so WS-2 must not
create it) or pin the Maven version in whatever build/CI image is used.

## Behaviour change: startup demo-data seeding

The Spring `CommandLineRunner` inserted `iphone_15`/100 and `iphone_15_pro`/0 on **every** boot,
so restarts accumulated duplicate rows (observed: 4 rows after two boots). The Quarkus
`StartupEvent` observer seeds the same two SKUs but is **idempotent** (inserts only when the
`sku_code` is absent) and can be disabled with `INVENTORY_SEED_ENABLED=false`. The REST contract is
unaffected; existing deployments with duplicated rows will simply stop accumulating more.

## Consul service definition (for WS-5)

The module contains no registration code. WS-5 should register:

```json
{
  "service": {
    "name": "inventory-service",
    "port": 8080,
    "address": "inventory-service",
    "checks": [
      { "http": "http://inventory-service:8080/q/health", "interval": "10s" }
    ]
  }
}
```

## `prometheus/prometheus.yml` — scrape path

```diff
   - job_name: 'inventory_service'
-    metrics_path: '/actuator/prometheus'
+    metrics_path: '/q/metrics'
     static_configs:
       - targets: ['inventory-service:8080']
         labels:
           application: 'Inventory Service Application'
```

## Endpoint moves (for dashboards / probes)

| Old | New |
| --- | --- |
| `/actuator/prometheus` | `/q/metrics` |
| `/actuator/health` | `/q/health` (`/q/health/live`, `/q/health/ready`; the readiness check includes the datasource) |

The business endpoint `GET /api/inventory?skuCode=...` is unchanged — see
`docs/quarkus-migration/contracts/inventory-rest-contract.md`.
