# WS-5 integration notes

The coordinating session should apply the following exact
`docker-compose.yml` diff. This change is intentionally kept here rather than
editing the Compose file in this session.

Note: `docker-compose.yml` uses CRLF line endings while this document is LF,
so extract the fenced diff to a file and apply it with
`git apply --ignore-whitespace` (verified to apply cleanly that way against
the current file).

```diff
diff --git a/docker-compose.yml b/docker-compose.yml
index 82606cf..9e910cf 100644
--- a/docker-compose.yml
+++ b/docker-compose.yml
@@ -113,18 +113,15 @@ services:
       - "9411:9411"
 
 
-  # Discovery Server (Eureka Server) Config
-  discovery-server:
-    image: ibatulanandjp/discovery-server:latest
-    container_name: discovery-server
-    pull_policy: always
+  # Consul Config
+  consul:
+    image: hashicorp/consul:1.19
+    container_name: consul
+    command: agent -dev -server -ui -client=0.0.0.0
     ports:
-      - "8761:8761"
-    environment:
-      - SERVER_PORT=8761
-      - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
-    depends_on:
-      - zipkin
+      - "8500:8500"
+    volumes:
+      - ./consul:/consul/config
 
   # API Gateway Service Config
   api-gateway:
@@ -137,13 +134,14 @@ services:
       - "8181"
     environment:
       - LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=TRACE
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
+      - CONSUL_HOST=consul
+      - CONSUL_PORT=8500
       - SERVER_PORT=8080
       - SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI=http://keycloak:8080/realms/spring-boot-microservices-realm
       - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
     depends_on:
       - zipkin
-      - discovery-server
+      - consul
       - keycloak
 
   # Product Service Config
@@ -153,13 +151,12 @@ services:
     pull_policy: always
     environment:
       - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/product-service
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
       - SERVER_PORT=8080
       - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
     depends_on:
       - mongo
       - zipkin
-      - discovery-server
+      - consul
       - api-gateway
 
   # Order Service Config
@@ -172,14 +169,15 @@ services:
       - SPRING_DATASOURCE_USERNAME=ibatulanand
       - SPRING_DATASOURCE_PASSWORD=password
       - SERVER_PORT=8080
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
+      - CONSUL_HOST=consul
+      - CONSUL_PORT=8500
       - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
       - SPRING_KAFKA_BOOTSTRAPSERVERS=broker:29092
     depends_on:
       - mysql-order
       - broker
       - zipkin
-      - discovery-server
+      - consul
       - api-gateway
 
   # Inventory Service Config
@@ -192,12 +190,11 @@ services:
       - SPRING_DATASOURCE_USERNAME=ibatulanand
       - SPRING_DATASOURCE_PASSWORD=password
       - SERVER_PORT=8080
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
       - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
     depends_on:
       - mysql-inventory
       - zipkin
-      - discovery-server
+      - consul
       - api-gateway
 
   # Notification Service Config
@@ -207,13 +204,12 @@ services:
     pull_policy: always
     environment:
       - SERVER_PORT=8080
-      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka
       - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
       - SPRING_KAFKA_BOOTSTRAP_SERVERS=broker:29092
     depends_on:
       - broker
       - zipkin
-      - discovery-server
+      - consul
       - api-gateway
 
   # Prometheus Config
```

The `order-service` and `api-gateway` clients receive `CONSUL_HOST=consul`
and `CONSUL_PORT=8500`. Stork's Consul discovery properties are named
`quarkus.stork.<service-name>.service-discovery.consul-host` and
`quarkus.stork.<service-name>.service-discovery.consul-port`. Quarkus maps
these to environment variables in the style
`QUARKUS_STORK__SERVICE_NAME__SERVICE_DISCOVERY_CONSUL_HOST` (with the service
name segment substituted as appropriate), but service `application.properties`
should preferably default to Consul with
`${CONSUL_HOST:consul}` and `${CONSUL_PORT:8500}`.

The discovery-server was never present in `prometheus/prometheus.yml`'s scrape
configuration (verified), so there is nothing to remove there. The coordinator
may optionally add a Consul job scraping
`consul:8500/v1/agent/metrics?format=prometheus`, but that is not required.

Registration from these files is immediate, but health checks show `critical`
until the services are actually running and serving `/q/health`. The service
definitions use a one-hour `deregister_critical_service_after` grace period to
cover cold starts. If a config-file service is deregistered after that period,
Consul will not re-read the files automatically; run `consul reload` or restart
the Consul agent to re-register it. There is no backend for `/eureka/**`
anymore; WS-6 repoints that route at the Consul UI.
