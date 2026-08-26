# WS-3 order-service integration notes

These are coordinator-owned compose/prometheus changes. WS-3 does not edit
`docker-compose.yml` or `prometheus/prometheus.yml`.

## `order-service` compose environment

Replace the current Spring environment with:

```yaml
environment:
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:mysql://mysql-order:3306/order_service?allowPublicKeyRetrieval=true&useSSL=false
  QUARKUS_DATASOURCE_USERNAME: root
  QUARKUS_DATASOURCE_PASSWORD: mysql
  QUARKUS_HTTP_PORT: 8080
  KAFKA_BOOTSTRAP_SERVERS: broker:29092
  CONSUL_HOST: consul
  CONSUL_PORT: 8500
  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
```

Remove these Spring settings:

```yaml
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SERVER_PORT
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
MANAGEMENT_ZIPKIN_TRACING_ENDPOINT
SPRING_KAFKA_BOOTSTRAPSERVERS
```

Add the coordinator-owned Consul service dependency:

```yaml
depends_on:
  - mysql-order
  - broker
  - consul
```

The application has no discovery or registration client code. Service
registration remains declarative and is owned by WS-5.

## Prometheus scrape path

For the existing `order_service` job, change:

```diff
   job_name: order_service
-  metrics_path: /actuator/prometheus
+  metrics_path: /q/metrics
```

Local development previously used `server.port=8081`; compose overrode it to
8080. Quarkus now defaults to 8080, and `QUARKUS_HTTP_PORT` remains the
compose override.
