package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "product.seed.gap-catalog=false")
@Testcontainers
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
    void clearProducts() {
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductRequest productRequest = getProductRequest();
        String productRequestString = objectMapper.writeValueAsString(productRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productRequestString))
                .andExpect(status().isCreated());
        Assertions.assertEquals(1, productRepository.findAll().size());
    }

    @Test
    void shouldFilterProductsBySkuCode() throws Exception {
        productRepository.saveAll(gapProducts());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product")
                        .param("skuCode", "268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$[0].styleId").value("268341"))
                .andExpect(jsonPath("$[0].salePrice").value(17.97))
                .andExpect(jsonPath("$[0].clearancePercent").value(40))
                .andExpect(jsonPath("$[0].finalSale").value(true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product")
                        .param("skuCode", "000000-000-X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldCreateProductWithPosFields() throws Exception {
        ProductRequest productRequest = ProductRequest.builder()
                .name("Vintage Soft Crewneck Tee")
                .description("Vintage Soft Crewneck Tee")
                .price(new BigDecimal("17.97"))
                .styleId("268341")
                .skuCode("268341-016-L")
                .department("WOMEN'S")
                .category("TOPS")
                .colorName("Heather Grey")
                .colorCode("#9b9ea3")
                .size("L")
                .listPrice(new BigDecimal("29.95"))
                .salePrice(new BigDecimal("17.97"))
                .clearancePercent(40)
                .finalSale(true)
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated());

        Product savedProduct = productRepository.findBySkuCode("268341-016-L").get(0);
        Assertions.assertEquals("268341", savedProduct.getStyleId());
        Assertions.assertEquals("WOMEN'S", savedProduct.getDepartment());
        Assertions.assertEquals("TOPS", savedProduct.getCategory());
        Assertions.assertEquals("Heather Grey", savedProduct.getColorName());
        Assertions.assertEquals("#9b9ea3", savedProduct.getColorCode());
        Assertions.assertEquals("L", savedProduct.getSize());
        Assertions.assertEquals(new BigDecimal("29.95"), savedProduct.getListPrice());
        Assertions.assertEquals(new BigDecimal("17.97"), savedProduct.getSalePrice());
        Assertions.assertEquals(40, savedProduct.getClearancePercent());
        Assertions.assertEquals(true, savedProduct.getFinalSale());
    }

    private ProductRequest getProductRequest() {
        return ProductRequest.builder()
                .name("Iphone 15")
                .description("Apple Iphone 15")
                .price(BigDecimal.valueOf(1500))
                .build();
    }

    private List<Product> gapProducts() {
        return List.of(
                Product.builder()
                        .styleId("268341")
                        .skuCode("268341-016-L")
                        .name("Vintage Soft Crewneck Tee")
                        .description("Vintage Soft Crewneck Tee")
                        .department("WOMEN'S")
                        .category("TOPS")
                        .colorName("Heather Grey")
                        .colorCode("#9b9ea3")
                        .size("L")
                        .price(new BigDecimal("17.97"))
                        .listPrice(new BigDecimal("29.95"))
                        .salePrice(new BigDecimal("17.97"))
                        .clearancePercent(40)
                        .finalSale(true)
                        .build(),
                Product.builder()
                        .styleId("471902")
                        .skuCode("471902-004-29")
                        .name("High Rise Straight Jean")
                        .description("High Rise Straight Jean")
                        .department("WOMEN'S")
                        .category("DENIM")
                        .colorName("Medium Indigo")
                        .colorCode("#41597d")
                        .size("29 Reg")
                        .price(new BigDecimal("69.95"))
                        .listPrice(new BigDecimal("69.95"))
                        .salePrice(new BigDecimal("69.95"))
                        .clearancePercent(0)
                        .finalSale(false)
                        .build()
        );
    }

}
