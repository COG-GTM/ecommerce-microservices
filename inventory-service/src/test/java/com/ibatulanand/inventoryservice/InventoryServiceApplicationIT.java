package com.ibatulanand.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "management.tracing.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class InventoryServiceApplicationIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33").withDatabaseName("inventory_service");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seededInventoryIsQueryableFromRealMySql() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "iphone_15", "iphone_15_pro", "does_not_exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.skuCode=='iphone_15')].inStock").value(true))
                .andExpect(jsonPath("$[?(@.skuCode=='iphone_15_pro')].inStock").value(false));
    }
}
