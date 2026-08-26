# WS-6: Quarkus API Gateway integration notes

The API gateway is now a Quarkus 3.15.6 application using reactive routes,
Quarkus OIDC bearer authentication, SmallRye Stork, and the Vert.x Mutiny
WebClient. The former `quarkus-vertx-web` dependency was not available in
Quarkus 3.15.6; the gateway uses `io.quarkus:quarkus-reactive-routes`.

## Compose changes

Apply this diff to the `api-gateway` service in `docker-compose.yml`:

```diff
   api-gateway:
     image: ibatulanandjp/api-gateway:latest
     container_name: api-gateway
     pull_policy: always
     ports:
       - "8181:8080"
     expose:
       - "8181"
     environment:
-      - LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=TRACE
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
-      - SERVER_PORT=8080
-      - SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI=http://keycloak:8080/realms/spring-boot-microservices-realm
-      - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
+      - QUARKUS_HTTP_PORT=8080
+      - QUARKUS_OIDC_AUTH_SERVER_URL=http://keycloak:8080/realms/spring-boot-microservices-realm
+      - CONSUL_HOST=consul
+      - CONSUL_PORT=8500
+      - CONSUL_UI_URL=http://consul:8500
+      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
     depends_on:
-      - zipkin
-      - discovery-server
       - keycloak
+      - consul
```

The local default is `quarkus.http.port=8181`, while Compose sets
`QUARKUS_HTTP_PORT=8080` inside the container. The published port remains
`8181:8080`.

Prometheus should scrape the gateway at `/q/metrics` instead of
`/actuator/prometheus`.

## Routes and compatibility

`/api/product` and `/api/order` are exact-path reactive proxy routes. Each
service instance is selected through Stork and the request is forwarded with
the Vert.x WebClient. Request and response status, body bytes, and applicable
headers are preserved. Hop-by-hop headers, `Host`, and inbound
`Content-Length` are omitted; the WebClient recomputes transport headers.
This preserves compatibility with the still-Spring product and order
backends: no new required application headers are introduced.

The existing `/eureka/**` compatibility surface remains permit-all. It now
proxies to the Consul UI using `CONSUL_UI_URL`:

* `/eureka` and `/eureka/web` map to `<consulUiUrl>/ui/`.
* `/eureka/<rest>` maps to `<consulUiUrl>/ui/<rest>`.

`/q/health`, `/q/health/*`, and `/q/metrics` are also permit-all. All other
paths require a bearer token and never redirect unauthenticated callers.

The gateway removes inbound `traceparent`, `tracestate`, and `baggage` while
copying headers, then injects the current OpenTelemetry context. Forwarded
requests therefore carry the gateway request span as their trace parent.
