# WS-4 notification-service integration notes

## Docker Compose

For the `notification-service` compose service, remove the Spring, Eureka, Zipkin,
and broker environment variables and add the Quarkus equivalents:

```yaml
services:
  notification-service:
    environment:
      # REMOVE:
      # SERVER_PORT=8080
      # EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
      # MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
      # SPRING_KAFKA_BOOTSTRAP_SERVERS=broker:29092
      # ADD:
      QUARKUS_HTTP_PORT=8080
      KAFKA_BOOTSTRAP_SERVERS=broker:29092
      QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
    depends_on:
      - broker
      # REMOVE:
      # - discovery-server
```

The equivalent environment diff is:

```diff
     environment:
-      SERVER_PORT=8080
-      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
-      MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
-      SPRING_KAFKA_BOOTSTRAP_SERVERS=broker:29092
+      QUARKUS_HTTP_PORT=8080
+      KAFKA_BOOTSTRAP_SERVERS=broker:29092
+      QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
     depends_on:
       - broker
-      - discovery-server
```

Keep the broker dependency. Drop the `discovery-server` dependency; Zipkin is
now reached through the OpenTelemetry collector.

## Prometheus

For the `notification_service` job, change only the metrics path:

```diff
   - job_name: 'notification_service'
-    metrics_path: '/actuator/prometheus'
+    metrics_path: '/q/metrics'
     static_configs:
       - targets: ['notification-service:8080']
```

The target remains `notification-service:8080`.

The service previously used `server.port=0` (random port) and now listens on a
fixed **8080** (`quarkus.http.port`, overridable via `QUARKUS_HTTP_PORT`), so
the existing Prometheus target `notification-service:8080` is correct.
