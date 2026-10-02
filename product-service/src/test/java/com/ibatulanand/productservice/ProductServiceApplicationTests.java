package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.seed.CatalogSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "catalog.seed.enabled=false",
        "eureka.client.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class ProductServiceApplicationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.24");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeEach
    void cleanCatalog() {
        productRepository.deleteAll();
    }

    private void seed() throws Exception {
        new CatalogSeeder(productRepository, objectMapper).seed();
    }

    @Test
    void contextStartsWithSeedingDisabled() {
        // context up without CatalogSeeder bean (catalog.seed.enabled=false)
    }

    @Test
    void seedingTwiceUpsertsThirtyProducts() throws Exception {
        CatalogSeeder seeder = new CatalogSeeder(productRepository, objectMapper);
        seeder.seed();
        seeder.seed();
        org.junit.jupiter.api.Assertions.assertEquals(30, productRepository.count());
    }

    @Test
    void seededProductsMatchPosFixtures() throws Exception {
        seed();

        assertFixtureProduct("268341-016-L", "268341", "Vintage Soft Crewneck Tee",
                "WOMEN'S", "TOPS", "Heather Grey", "#9b9ea3", "L",
                29.95, 17.97, 40, true, 10);
        assertFixtureProduct("471902-004-29", "471902", "High Rise Straight Jean",
                "WOMEN'S", "DENIM", "Medium Indigo", "#41597d", "29 Reg",
                69.95, 69.95, 0, false, 4);
        assertFixtureProduct("512884-022-M", "512884", "Logo Fleece Hoodie",
                "MEN'S", "FLEECE", "Navy Uniform", "#1f2a44", "M",
                59.95, 41.97, 30, false, 4);
    }

    private void assertFixtureProduct(String skuCode, String styleId, String name,
                                      String department, String category, String colorName,
                                      String colorCode, String size, double listPrice,
                                      double salePrice, int clearancePercent, boolean finalSale,
                                      int variantCount) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/product/sku/" + skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleId").value(styleId))
                .andExpect(jsonPath("$.skuCode").value(skuCode))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(name))
                .andExpect(jsonPath("$.department").value(department))
                .andExpect(jsonPath("$.category").value(category))
                .andExpect(jsonPath("$.colorName").value(colorName))
                .andExpect(jsonPath("$.colorCode").value(colorCode))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.listPrice").value(listPrice))
                .andExpect(jsonPath("$.salePrice").value(salePrice))
                .andExpect(jsonPath("$.price").value(salePrice))
                .andExpect(jsonPath("$.clearancePercent").value(clearancePercent))
                .andExpect(jsonPath("$.finalSale").value(finalSale))
                .andExpect(jsonPath("$.variants.length()").value(variantCount));
        result.andExpect(jsonPath("$.variants[0].skuCode").exists());
    }

    @Test
    void getProductEndpointsFilterAndReturnSeededCatalog() throws Exception {
        seed();

        mockMvc.perform(get("/api/product").param("skuCode", "268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"));

        mockMvc.perform(get("/api/product").param("skuCode", "999999-999-X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30));

        mockMvc.perform(get("/api/product/sku/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProductWithNewFieldsAndDuplicateSkuConflicts() throws Exception {
        seed();

        ProductRequest request = ProductRequest.builder()
                .styleId("600001")
                .skuCode("600001-001-M")
                .name("Modern Khaki Pant")
                .description("Modern Khaki Pant")
                .department("MEN'S")
                .category("PANTS")
                .colorName("Khaki")
                .colorCode("#b5a07e")
                .size("M")
                .listPrice(new BigDecimal("49.95"))
                .salePrice(new BigDecimal("34.97"))
                .clearancePercent(30)
                .finalSale(false)
                .build();
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skuCode").value("600001-001-M"))
                .andExpect(jsonPath("$.salePrice").value(34.97))
                .andExpect(jsonPath("$.price").value(34.97));

        mockMvc.perform(get("/api/product/sku/600001-001-M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleId").value("600001"));

        // same skuCode again → unique index conflict → 409
        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void legacyCreateProductStillWorks() throws Exception {
        ProductRequest productRequest = ProductRequest.builder()
                .name("Iphone 15")
                .description("Apple Iphone 15")
                .skuCode("legacy-iphone-15")
                .price(BigDecimal.valueOf(1500))
                .build();
        String productRequestString = objectMapper.writeValueAsString(productRequest);

        mockMvc.perform(post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestString))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(1, productRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, BigDecimal.valueOf(1500).compareTo(
                        productRepository.findBySkuCode("legacy-iphone-15").get().getSalePrice()));
    }
}
