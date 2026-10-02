package com.ibatulanand.inventoryservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.seed.StoreInventorySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryServiceApplicationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("inventory_service")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreInventorySeeder seeder;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void seededFixtureResponsesMatchPosContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode fixtures = objectMapper.readTree(
                new ClassPathResource("fixtures/pos-inventory-fixtures.json").getInputStream());

        var entries = fixtures.fields();
        while (entries.hasNext()) {
            var fixture = entries.next();
            mockMvc.perform(get("/api/inventory")
                            .param("skuCode", fixture.getKey())
                            .param("storeId", "1969"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(fixture.getValue().toString(), true));
        }
    }

    @Test
    void seederLoadsCanonicalCatalogForAllStores() throws Exception {
        assertEquals(150, jdbcTemplate.queryForObject("select count(*) from t_inventory", Integer.class));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode catalog = objectMapper.readTree(
                new ClassPathResource("seed/gap-catalog.json").getInputStream());
        Set<String> catalogSkus = new HashSet<>();
        catalog.forEach(item -> catalogSkus.add(item.get("skuCode").asText()));

        Set<String> seededSkus = new HashSet<>();
        inventoryRepository.findAll().forEach(inventory -> seededSkus.add(inventory.getSkuCode()));
        assertEquals(catalogSkus, seededSkus);

        for (String skuCode : catalogSkus) {
            assertEquals(5, inventoryRepository.findBySkuCodeAndStoreIdIn(
                    skuCode, Set.of("1969", "1042", "1177", "1284", "1391")).size());
        }
    }

    @Test
    void zeroHomeStoreStockCanHaveNearbyAvailability() throws Exception {
        Inventory inventory = inventoryRepository.findBySkuCodeAndStoreId("268341-001-XS", "1969")
                .orElseThrow();
        assertEquals(0, inventory.getOnHand());

        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "268341-001-XS")
                        .param("storeId", "1969"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(0))
                .andExpect(jsonPath("$.nearbyStores").isArray())
                .andExpect(jsonPath("$.nearbyStores").isNotEmpty());
    }

    @Test
    void legacyQueryReturnsOneEntryForEachRequestedSku() throws Exception {
        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "268341-016-L", "268341-001-XS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].skuCode").value("268341-001-XS"))
                .andExpect(jsonPath("$[1].inStock").value(false));
    }

    @Test
    void unknownSkuAndUnknownStoreReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "unknown").param("storeId", "1969"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/inventory").param("skuCode", "268341-016-L").param("storeId", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rerunningSeederDoesNotDuplicateOrOverwriteSeedRows() throws Exception {
        seeder.run(null);

        assertEquals(150, jdbcTemplate.queryForObject("select count(*) from t_inventory", Integer.class));
        assertFalse(inventoryRepository.findAll().isEmpty());
    }
}
