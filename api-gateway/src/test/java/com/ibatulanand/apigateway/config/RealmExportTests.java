package com.ibatulanand.apigateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealmExportTests {

    private static final Path REALM_EXPORT = Path.of("..", "realms", "spring-boot-microservices-realm.json");
    private static final List<String> API_ROLES =
            List.of(SecurityConfig.PRODUCT_READ, SecurityConfig.PRODUCT_WRITE, SecurityConfig.ORDER_WRITE);

    private static JsonNode realm;

    @BeforeAll
    static void loadRealm() throws IOException {
        realm = new ObjectMapper().readTree(REALM_EXPORT.toFile());
    }

    private static JsonNode client(String clientId) {
        for (JsonNode client : realm.path("clients")) {
            if (clientId.equals(client.path("clientId").asText())) {
                return client;
            }
        }
        throw new AssertionError("client not found: " + clientId);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    void noClientSecretIsCommitted() {
        for (JsonNode client : realm.path("clients")) {
            assertThat(client.has("secret")).as("secret for %s", client.path("clientId").asText()).isFalse();
        }
    }

    @Test
    void noRealmKeyMaterialIsCommitted() {
        assertThat(realm.path("components").has("org.keycloak.keys.KeyProvider")).isFalse();
    }

    @Test
    void gatewayClientIsLeastPrivilege() {
        JsonNode client = client("spring-cloud-client");

        assertThat(client.path("fullScopeAllowed").asBoolean(true)).isFalse();
        assertThat(client.path("redirectUris")).isEmpty();
        assertThat(client.path("webOrigins")).isEmpty();
    }

    @Test
    void realmDefinesAndScopesTheRolesTheGatewayEnforces() {
        List<String> realmRoles = new ArrayList<>();
        realm.path("roles").path("realm").forEach(role -> realmRoles.add(role.path("name").asText()));
        assertThat(realmRoles).containsAll(API_ROLES);

        List<String> scoped = new ArrayList<>();
        for (JsonNode mapping : realm.path("scopeMappings")) {
            if ("spring-cloud-client".equals(mapping.path("client").asText())) {
                scoped.addAll(texts(mapping.path("roles")));
            }
        }
        assertThat(scoped).containsExactlyInAnyOrderElementsOf(API_ROLES);
    }
}
