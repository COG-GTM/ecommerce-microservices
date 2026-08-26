package com.ibatulanand.inventoryservice;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class InventoryLegacySchemaResource implements QuarkusTestResourceLifecycleManager {

    private MySQLContainer<?> mysql;

    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("inventory_service")
                .withUsername("root")
                .withPassword("mysql");
        mysql.start();
        createLegacySchema();
        return Map.of(
                "quarkus.datasource.jdbc.url", mysql.getJdbcUrl(),
                "quarkus.datasource.username", mysql.getUsername(),
                "quarkus.datasource.password", mysql.getPassword(),
                "quarkus.datasource.devservices.enabled", "false");
    }

    private void createLegacySchema() {
        String ddl = """
                CREATE TABLE `t_inventory` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `quantity` int DEFAULT NULL,
                  `sku_code` varchar(255) DEFAULT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """;
        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create legacy inventory schema", e);
        }
    }

    @Override
    public void stop() {
        if (mysql != null) {
            mysql.stop();
        }
    }
}
