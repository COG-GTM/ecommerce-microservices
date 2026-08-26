package com.ibatulanand.orderservice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/**
 * Single MySQL container shared by every test in the module, started on first use and reused for
 * the lifetime of the JVM.
 */
public final class MySqlContainerSupport {

    static final MySQLContainer<?> MY_SQL = new MySQLContainer<>("mysql:8.0");

    static {
        MY_SQL.start();
    }

    private MySqlContainerSupport() {
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MY_SQL::getJdbcUrl);
        registry.add("spring.datasource.username", MY_SQL::getUsername);
        registry.add("spring.datasource.password", MY_SQL::getPassword);
    }
}
