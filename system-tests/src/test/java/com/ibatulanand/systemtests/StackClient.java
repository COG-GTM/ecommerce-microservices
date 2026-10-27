package com.ibatulanand.systemtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

/** Thin HTTP/JDBC client for the running docker-compose.test.yml stack. */
final class StackClient {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String GATEWAY_URL = env("GATEWAY_URL", "http://localhost:8181");
    static final String KEYCLOAK_URL = env("KEYCLOAK_URL", "http://localhost:8080");
    static final String KAFKA_BOOTSTRAP = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    static final String ORDER_DB_URL = env("ORDER_DB_URL",
            "jdbc:mysql://localhost:3307/order_service?allowPublicKeyRetrieval=true&useSSL=false");
    static final String ORDER_PLACED = "Order Placed Successfully!";
    static final String ORDER_FALLBACK = "Oops! Something went wrong, please order after some time!";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String token;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String clientSecret() throws IOException {
        String secret = System.getenv("OAUTH_CLIENT_SECRET");
        if (secret != null && !secret.isBlank()) {
            return secret;
        }
        JsonNode realm = JSON.readTree(Files.readString(
                Path.of("..", "realms", "spring-boot-microservices-realm.json")));
        for (JsonNode client : realm.get("clients")) {
            if ("spring-cloud-client".equals(client.get("clientId").asText())) {
                return client.get("secret").asText();
            }
        }
        throw new IllegalStateException("spring-cloud-client not found in realm export");
    }

    String token() throws IOException, InterruptedException {
        if (token == null) {
            String form = "grant_type=client_credentials&client_id=spring-cloud-client&client_secret="
                    + URLEncoder.encode(clientSecret(), StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(
                            KEYCLOAK_URL + "/realms/spring-boot-microservices-realm/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Token request failed: HTTP " + response.statusCode());
            }
            token = JSON.readTree(response.body()).get("access_token").asText();
        }
        return token;
    }

    HttpResponse<String> get(String path, boolean authenticated) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(GATEWAY_URL + path))
                .timeout(Duration.ofSeconds(20)).GET();
        if (authenticated) {
            request.header("Authorization", "Bearer " + token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, Object body, boolean authenticated) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(GATEWAY_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (authenticated) {
            request.header("Authorization", "Bearer " + token());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static Connection orderDb() throws SQLException {
        return DriverManager.getConnection(ORDER_DB_URL, "root", "root");
    }
}
