# WS-1 product-service integration notes

The product service now runs on Quarkus and does not contain Eureka client
code. Apply these integration changes outside the module.

## Docker Compose

The current `docker-compose.yml` product-service block uses:

```yaml
  product-service:
    container_name: product-service
    image: ibatulanandjp/product-service:latest
    pull_policy: always
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/product-service
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
      - SERVER_PORT=8080
      - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
```

Replace its environment entries with:

```yaml
    environment:
      - QUARKUS_MONGODB_CONNECTION_STRING=mongodb://mongo:27017
      - QUARKUS_MONGODB_DATABASE=product-service
      - QUARKUS_HTTP_PORT=8080
      - QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

Use the `QUARKUS_`-prefixed form: verified empirically on Quarkus 3.15.6 that the
plain OTel SDK variable `OTEL_EXPORTER_OTLP_ENDPOINT` is ignored (the exporter
still targeted `localhost:4317` from `application.properties`). Only
`quarkus.otel.service.name` / `quarkus.otel.resource.attributes` have `otel.*`
aliases, and only under `quarkus.otel.mp.compatibility=true`.

Remove `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` for this service. Service
registration is declarative via Consul and is owned by WS-5; there is no
discovery client code in this module.

## Prometheus

The current `product_service` stanza is:

```yaml
  - job_name: 'product_service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['product-service:8080']
        labels:
          application: 'Product Service Application'
```

Change only its metrics path to:

```yaml
  - job_name: 'product_service'
    metrics_path: '/q/metrics'
    static_configs:
      - targets: ['product-service:8080']
        labels:
          application: 'Product Service Application'
```

The service now listens on fixed port `8080` instead of Spring's
`server.port=0`.

## Build tooling

The box's default `mvn` is Apache Maven 3.6.3. It cannot run the Quarkus
3.15.6 plugin and fails with
`java.lang.NoSuchMethodError: 'boolean org.apache.maven.settings.Mirror.isBlocked()'`.
Maven 3.9.x is required for any module using the Quarkus build.

WS-1 was built with Apache Maven 3.9.7 at:

```text
/home/ubuntu/.m2/wrapper/dists/apache-maven-3.9.7/2a4cb831/bin/mvn
```

The coordinator should either commit a Maven wrapper (`mvnw`, which is
root-owned and outside WS-1 scope) or pin Maven 3.9.x in the CI/build setup.
This requirement applies to every workstream, not only WS-1.
