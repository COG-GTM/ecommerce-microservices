# Micro Marketplace: An E-commerce Microservices Application

## Solution Overview

Micro Marketplace is a robust e-commerce application built on a microservices architecture using **Quarkus** and other open-source tools. 
- This platform leverages the power of **Quarkus**, **HashiCorp Consul**, a **Quarkus reactive-routes gateway**, and **KeyCloak** for service development, discovery, gateway management, and security, respectively. 
- It incorporates **SmallRye Fault Tolerance** for resilient synchronous communication (with **SmallRye Stork** for client-side load balancing), and **Apache Kafka** via **SmallRye Reactive Messaging** for seamless event-driven asynchronous communication between services.
- It offers extensive observability into the application using **OpenTelemetry** for distributed tracing (exported through an **OpenTelemetry Collector** into **Zipkin**), and **Micrometer**/**Prometheus** with **Grafana** for monitoring and visualization. 

With a focus on scalability, resilience, and real-time interaction, Micro Marketplace provides a robust foundation for creating feature-rich online marketplaces.


### Solution Architecture
![Solution Architecture](docs/images/architecture/SolutionArchitecture.png)

### Services
- **Product Service:** Responsible for managing product information, including creation, retrieval, and updates. It uses a MongoDB database.
- **Order Service:** Handles order management, including creating and retrieving orders. It uses a MySQL database.
- **Inventory Service:** Manages products inventory. It also uses a MySQL database.
- **Notification Service:** A stateless service responsible for sending notifications to users regarding their orders or other relevant updates.

### Major Components
- **Service Discovery:** HashiCorp Consul is the service registry. Registration is **declarative** - the service definitions in [`consul/`](consul/) are mounted into the Consul agent, so no service contains discovery-client code. Clients resolve instances through SmallRye Stork (`stork://<service-name>`).
- **API Gateway:** A Quarkus application built on reactive routes serves as the entry point for all external requests, resolving backends through Stork/Consul and forwarding status, body, and headers unchanged.
- **Auth Server:** For robust authentication and authorization mechanisms, KeyCloak is used to secure the microservices and protect sensitive data. The gateway validates bearer tokens with `quarkus-oidc`.
- **Circuit Breaker:** SmallRye Fault Tolerance (`@CircuitBreaker`, `@Retry`, `@Timeout`, `@Fallback`) maintains system reliability by preventing cascading failures in microservices.
- **Message Broker:** Apache Kafka forms the backbone of Micro Marketplace's event-driven architecture, facilitating asynchronous notification for orders. Producers and consumers use SmallRye Reactive Messaging.
- **Observability Stack:** Distributed tracing uses OpenTelemetry; services export OTLP/gRPC to an OpenTelemetry Collector, which forwards spans to Zipkin. 
   Metrics are exposed at `/q/metrics` (Micrometer/Prometheus registry) and health at `/q/health`; Prometheus collects the metrics, and Grafana provides a rich dashboard for visualizing and analyzing application performance data.

### Tech Stack Used
<div>
    <table>
        <tr>
            <td>
                <strong>Languages & Frameworks</strong>
            </td>
            <td>
                <a href="ttps://www.java.com/en/">
                    <img alt="Java" src="https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://quarkus.io/" target="_blank">
                    <img alt="Quarkus" src="https://img.shields.io/badge/Quarkus-4695EB?style=flat&logo=quarkus&logoColor=white">
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Databases & Message Queue</strong>
            </td>
            <td>
                <a href="https://mongodb.io/" target="_blank"> 
                    <img alt="MongoDB" src="https://img.shields.io/badge/MongoDB-4EA94B?style=flat&logo=mongodb&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://mysql.com/" target="_blank"> 
                    <img alt="MySQL" src="https://img.shields.io/badge/MySQL-00000F?style=flat&logo=mysql&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://kafka.apache.org/" target="_blank"> 
                    <img alt="Apache Kafka" src="https://img.shields.io/badge/Apache%20Kafka-000?style=flat&logo=apachekafka"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>API Gateway</strong>
            </td>
            <td>
                <a href="https://quarkus.io/guides/reactive-routes" target="_blank">
                    <img alt="Quarkus Reactive Routes" src="https://img.shields.io/badge/Quarkus%20Reactive%20Routes-4695EB.svg?&style=flat&logo=quarkus&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Service Discovery</strong>
            </td>
            <td>
                <a href="https://www.consul.io/" target="_blank">
                    <img alt="HashiCorp Consul" src="https://img.shields.io/badge/HashiCorp%20Consul-F24C53.svg?&style=flat&logo=consul&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://smallrye.io/smallrye-stork/" target="_blank">
                    <img alt="SmallRye Stork" src="https://img.shields.io/badge/SmallRye%20Stork-4B32C3.svg?&style=flat&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Circuit Breaker</strong>
            </td>
            <td>
                <a href="https://smallrye.io/docs/smallrye-fault-tolerance/" target="_blank">
                    <img alt="SmallRye Fault Tolerance" src="https://img.shields.io/badge/SmallRye%20Fault%20Tolerance-4B32C3.svg?&style=flat&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Security</strong>
            </td>
            <td>
                <a href="https://www.keycloak.org/" target="_blank"> 
                    <img alt="KeyCloak" src="https://img.shields.io/badge/KeyCloak-00B8E3.svg?&style=flat&logo=keycloak&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Observability</strong>
            </td>
            <td>
                <a href="https://micrometer.io/" target="_blank"> 
                    <img alt="Micrometer" src="https://img.shields.io/badge/Micrometer-117A71.svg?&style=flat&logo=micrometer&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://zipkin.io/" target="_blank"> 
                    <img alt="Zipkin" src="https://img.shields.io/badge/Zipkin-FE7139.svg?&style=flat&logo=zipkin&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://prometheus.io/" target="_blank"> 
                    <img alt="Prometheus" src="https://img.shields.io/badge/Prometheus-E6522C.svg?&style=flat&logo=prometheus&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://grafana.com/" target="_blank"> 
                    <img alt="Grafana" src="https://img.shields.io/badge/Grafana-F79A2F.svg?&style=flat&logo=grafana&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://opentelemetry.io/" target="_blank">
                    <img alt="OpenTelemetry" src="https://img.shields.io/badge/OpenTelemetry-000000.svg?&style=flat&logo=opentelemetry&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
        <tr>
            <td>
                <strong>Build & Containerization</strong>
            </td>
            <td>
                <a href="https://maven.apache.org/" target="_blank"> 
                    <img alt="Maven" src="https://img.shields.io/badge/Maven-C02748?style=flat&logo=apachemaven&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://www.docker.com/" target="_blank"> 
                    <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white"/>
                </a>
                &emsp;
                <a href="https://github.com/GoogleContainerTools/jib" target="_blank"> 
                    <img alt="Jib" src="https://img.shields.io/badge/Jib-FF6444?style=flat&logo=googlecloud&logoColor=white"/>
                </a>
                &emsp;
            </td>
        </tr>
    </table>
</div>


## Getting Started

### Prerequisites
1. Docker and Docker Compose should be installed.
2. Docker should be running.
3. JDK 17 (for building the service images). Use the bundled Maven wrapper `./mvnw` - Quarkus 3.15 requires Maven >= 3.8.6, so the system `mvn` may be too old.

### Deployment

1. Navigate to the project directory:
   ```shell
   cd ecommerce-microservices
   ```

2. Build the service images into the local Docker daemon with Jib:
   ```shell
   ./mvnw -B -DskipTests package -Dquarkus.container-image.build=true
   ```
   This produces `ibatulanandjp/{api-gateway,product-service,order-service,inventory-service,notification-service}:latest`.
   Compose uses `pull_policy: never`, so it runs exactly these locally built images.

3. Start the containers:
   ```shell
   docker compose up -d
   ```


4. Confirm that the containers are up and running:
   ```shell
   docker ps
   ```

5. Confirm all three services are registered in Consul:
   ```shell
   curl -s http://localhost:8500/v1/catalog/services
   ```


## Usage


### Interacting with Application

- **Getting Credentials from KeyCloak**
  - Access the KeyCloak Admin UI at http://localhost:8080/
  - Go to the Realm `spring-boot-microservices-realm`
  - Go to the Client `spring-cloud-client`
  - Go the the 'Credentials' section, and get the 'Client Secret'


- **Setup Postman Authentication** [Required in the next steps]
  - On the Request page, set Authorization:
    - Type: `OAuth 2.0`
    - Configure New Token with:
      - Token Name: `token`
      - Grant Type: `Client Credentials`
      - Access Token URL: `http://keycloak:8080/realms/spring-boot-microservices-realm/protocol/openid-connect/token`
      - Client ID: `spring-cloud-client`
      - Client Secret: `<client-secret>` (which you copied in the last step from KeyCloak)
    - Click on "Get New Access Token" and then click "Use Token"

    > NOTE: For getting the access token from the keycloak container with the local machine, it is required to add a row with `127.0.0.1 keycloak` in the file: `C:\Windows\System32\drivers\etc\hosts` or `/etc/hosts`  

- **Accessing API Endpoints**
  - **POST /api/product**
    - Method: POST
    - Endpoint: http://localhost:8181/api/product
    - Authorization: Use the OAuth 2.0 token fetched, following the previous step.
    - Body: 
      ```json
      {
         "name": "Iphone 15",
         "description": "Apple Iphone 15",
         "price": 1500
      } 
      ```
    - Output:
        ![Postman](docs/images/outputs/product_api_post_postman.png)
        ![Zipkin](docs/images/outputs/product_api_post_zipkin.png)
  
  - GET /api/product
    - Method: GET
    - Endpoint: http://localhost:8181/api/product
    - Authorization: Use the OAuth 2.0 token fetched, following the previous step.
    - Output:
        ![Postman](docs/images/outputs/product_api_get_postman.png)
 
  - POST /api/order
    - Method: POST
    - Endpoint: http://localhost:8181/api/order
    - Authorization: Use the OAuth 2.0 token fetched, following the previous step.
    - Body:
      ```json
      {
         "orderLineItemsDtoList": [
           {
              "skuCode": "iphone_15_pro",
              "price": 2000,
              "quantity": 1
            }
         ]
      }  
      ```
    - Output:
      ![Postman](docs/images/outputs/order_api_post_postman.png)
      ![Zipkin](docs/images/outputs/order_api_post_zipkin.png)
      ![Notification Service Logs](docs/images/outputs/order_api_post_notification_service_docker_logs.png)

### Components UI

- KeyCloak Admin UI
   - Keycloak UI can be accessed on http://localhost:8080/
   - Realm: `spring-boot-microservices-realm`
     ![KeyCloak Realm](docs/images/outputs/keycloak_realm.png)
   - Client: `spring-cloud-client`
    ![KeyCloak Client](docs/images/outputs/keycloak_client.png)


- Consul UI
   - Registered services can be viewed on http://localhost:8500/ui/
   - The legacy `/eureka/**` gateway path is preserved (permit-all) and now proxies the Consul UI, so http://localhost:8181/eureka also works.


- Zipkin UI
   - Traces for the API calls can be accessed on http://localhost:9411/zipkin/
    ![Zipkin UI](docs/images/outputs/zipkin_ui.png)


- Prometheus UI
    - Prometheus UI can be accessed on http://localhost:9090/
    - Prometheus Graph Query
      ![Graph Query](docs/images/outputs/prometheus_graph.png)
    - Prometheus Targets Health
      ![Targets Health](docs/images/outputs/prometheus_targets.png)
    - Prometheus Service Discovery Status
      ![Service Discovery Status](docs/images/outputs/prometheus_service_discovery.png)

- Grafana Dashboard
  - Grafana Dashboard can be accessed on http://localhost:3000/
  - To visualize the application, create a 'Data Source' and import the dashboard using `grafana-dashboard.json` file.
  - Data Source
    ![Data Source](docs/images/outputs/grafana_data_source.png)
  - Dashboard [Collapsed]
    ![Dashboard Collapsed](docs/images/outputs/grafana_dashboard_collapsed.png)
  - Dashboard
    ![Dashboard-1](docs/images/outputs/grafana_dashboard_pg1.png)
    ![Dashboard-5](docs/images/outputs/grafana_dashboard_pg5.png)


## Environment Cleanup

- To completely stop and remove the containers and other resources (network, volume, etc.), run the following command:
   ```shell
   docker compose down -v
   ```
