package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductVariantDto;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

@SpringBootTest
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

    @AfterEach
    void cleanUp() {
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

        Product product = productRepository.findAll().get(0);
        Assertions.assertEquals("268341", product.getStyleId());
        Assertions.assertEquals("268341-016-L", product.getSkuCode());
        Assertions.assertEquals("WOMEN'S", product.getDepartment());
        Assertions.assertEquals("TOPS", product.getCategory());
        Assertions.assertEquals("Black", product.getColorName());
        Assertions.assertEquals("016", product.getColorCode());
        Assertions.assertEquals("L", product.getSize());
        Assertions.assertEquals(0, new BigDecimal("49.95").compareTo(product.getListPrice()));
        Assertions.assertEquals(0, new BigDecimal("29.97").compareTo(product.getSalePrice()));
        Assertions.assertEquals(40, product.getClearancePercent());
        Assertions.assertTrue(product.isFinalSale());
        Assertions.assertEquals(2, product.getVariants().size());
    }

    @Test
    void shouldReturnProductsWithRetailFields() throws Exception {
        createProduct(getProductRequest());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$[0].category").value("TOPS"))
                .andExpect(jsonPath("$[0].salePrice").value(29.97))
                .andExpect(jsonPath("$[0].finalSale").value(true));
    }

    @Test
    void shouldLookUpProductBySkuCode() throws Exception {
        createProduct(getProductRequest());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product/sku/268341-016-L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$.size").value("L"))
                .andExpect(jsonPath("$.colorName").value("Black"));
    }

    @Test
    void shouldLookUpProductByVariantSkuCodeAndReturnVariantPricing() throws Exception {
        createProduct(getProductRequest());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/product/sku/268341-016-M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleId").value("268341"))
                .andExpect(jsonPath("$.skuCode").value("268341-016-M"))
                .andExpect(jsonPath("$.size").value("M"))
                .andExpect(jsonPath("$.salePrice").value(34.97))
                .andExpect(jsonPath("$.finalSale").value(false));
    }

    @Test
    void shouldReturnNotFoundForUnknownSkuCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/product/sku/000000-000-X"))
                .andExpect(status().isNotFound());
    }

    private void createProduct(ProductRequest productRequest) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated());
    }

    private ProductRequest getProductRequest() {
        return ProductRequest.builder()
                .name("Ribbed Mock-Neck Tank")
                .description("Ribbed cotton mock-neck tank")
                .price(BigDecimal.valueOf(29.97))
                .styleId("268341")
                .skuCode("268341-016-L")
                .department("WOMEN'S")
                .category("TOPS")
                .colorName("Black")
                .colorCode("016")
                .size("L")
                .listPrice(new BigDecimal("49.95"))
                .salePrice(new BigDecimal("29.97"))
                .clearancePercent(40)
                .finalSale(true)
                .variants(List.of(
                        ProductVariantDto.builder()
                                .skuCode("268341-016-L")
                                .colorName("Black")
                                .colorCode("016")
                                .size("L")
                                .listPrice(new BigDecimal("49.95"))
                                .salePrice(new BigDecimal("29.97"))
                                .clearancePercent(40)
                                .finalSale(true)
                                .build(),
                        ProductVariantDto.builder()
                                .skuCode("268341-016-M")
                                .colorName("Black")
                                .colorCode("016")
                                .size("M")
                                .listPrice(new BigDecimal("49.95"))
                                .salePrice(new BigDecimal("34.97"))
                                .clearancePercent(30)
                                .finalSale(false)
                                .build()))
                .build();
    }

}
