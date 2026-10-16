package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the service on a database created by the pre-POS-2 schema ({@code t_inventory(id, quantity, sku_code)})
 * and checks that existing stock is still visible through the legacy array form after ddl-auto=update.
 */
@SpringBootTest
class LegacySchemaUpgradeTests {

    private static final org.testcontainers.containers.MySQLContainer<?> MYSQL = MySqlContainerTest.MYSQL;

    private static final String DB = "inventory_legacy_upgrade";

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) throws Exception {
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + DB);
            s.execute("GRANT ALL ON " + DB + ".* TO '" + MYSQL.getUsername() + "'@'%'");
            s.execute("CREATE TABLE IF NOT EXISTS " + DB + ".t_inventory "
                    + "(id BIGINT AUTO_INCREMENT PRIMARY KEY, quantity INT, sku_code VARCHAR(255))");
            s.execute("DELETE FROM " + DB + ".t_inventory");
            s.execute("INSERT INTO " + DB + ".t_inventory (quantity, sku_code) VALUES (100, 'iphone_15'), (0, 'iphone_15_pro')");
        }
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl().replace("/inventory_service", "/" + DB));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("management.tracing.sampling.probability", () -> "0.0");
    }

    @Autowired
    private InventoryService inventoryService;

    @Test
    void existingStockSurvivesSchemaUpgrade() {
        List<InventoryResponse> response = inventoryService.isInStock(List.of("iphone_15", "iphone_15_pro"));

        assertThat(response).extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", true),
                        org.assertj.core.groups.Tuple.tuple("iphone_15_pro", false));
    }
}
