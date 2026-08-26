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
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
```

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
