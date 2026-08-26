package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@Testcontainers
class InventoryServiceApplicationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        inventoryRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void serviceAndEndpointReturnInventoryStatus() {
        Inventory inStock = new Inventory(null, "integration-in-stock", 10);
        Inventory outOfStock = new Inventory(null, "integration-out-of-stock", 0);
        inventoryRepository.saveAll(List.of(inStock, outOfStock));

        List<InventoryResponse> serviceResponse = inventoryService.isInStock(
                List.of("integration-in-stock", "integration-out-of-stock"));

        assertThat(serviceResponse)
                .extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("integration-in-stock", true),
                        org.assertj.core.groups.Tuple.tuple("integration-out-of-stock", false));

        ResponseEntity<InventoryResponse[]> endpointResponse = restTemplate.getForEntity(
                "/api/inventory?skuCode=integration-in-stock&skuCode=integration-out-of-stock",
                InventoryResponse[].class);

        assertThat(endpointResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endpointResponse.getBody())
                .extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("integration-in-stock", true),
                        org.assertj.core.groups.Tuple.tuple("integration-out-of-stock", false));
    }
}
