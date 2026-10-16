package com.ibatulanand.productservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.config.ProductSeedData;
import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.model.ProductVariant;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProductServiceApplicationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.24");
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductSeedData productSeedData;
    @Autowired
    private MongoTemplate mongoTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeEach
    void resetToGoldenSeed() {
        productRepository.deleteAll();
        productSeedData.run();
    }

    @Test
    void lookupOfFirstGoldenSkuEqualsMockProductsZero() throws Exception {
        String body = getBySku("268341-016-L")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals(fixture(0).toString(), body, JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void everyGoldenSkuEqualsItsFixture(int index) throws Exception {
        JsonNode expected = fixture(index);
        String body = getBySku(expected.get("skuCode").asText())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JSONAssert.assertEquals(expected.toString(), body, JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"268341016L", "268341016l", " 268341-016-l "})
    void lookupNormalizesHangtagFormatting(String scanned) throws Exception {
        getBySku(scanned)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("268341-016-L"))
                .andExpect(jsonPath("$.finalSale").value(true));
    }

    @Test
    void unknownSkuReturnsProblemJson404() throws Exception {
        getBySku("999999-000-X")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("sku-not-found")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.skuCode").value("999999-000-X"));
    }

    @Test
    void listWithoutSkuStillReturnsLegacyFields() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", hasSize(3)))
                .andExpect(jsonPath("$[*].name", hasItem("Vintage Soft Crewneck Tee")))
                .andExpect(jsonPath("$[*].description", hasItem("High Rise Straight Jean")))
                .andExpect(jsonPath("$[*].price", hasItem(59.95)));
    }

    @Test
    void legacyCreateStillWorks() throws Exception {
        ProductRequest legacy = ProductRequest.builder()
                .name("Iphone 15")
                .description("Apple Iphone 15")
                .price(BigDecimal.valueOf(1500))
                .build();

        post(legacy).andExpect(status().isCreated());

        Assertions.assertEquals(4, productRepository.findAll().size());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(jsonPath("$[?(@.name == 'Iphone 15')].price", hasItem(1500.0)));
    }

    @Test
    void createStyleWithVariantsThenLookUpEachVariant() throws Exception {
        post(newStyle("600100", variant("600100-001-S", "Black", "29.95", "29.95", 0, false),
                variant("600100-002-M", "White", "29.95", "20.97", 30, false)))
                .andExpect(status().isCreated());

        Product saved = productRepository.findByStyleId("600100").orElseThrow();
        Assertions.assertEquals(new BigDecimal("29.95"), saved.getPrice());
        Assertions.assertEquals(2, saved.getVariants().size());

        getBySku("600100002M")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styleId").value("600100"))
                .andExpect(jsonPath("$.skuCode").value("600100-002-M"))
                .andExpect(jsonPath("$.salePrice").value(20.97))
                .andExpect(jsonPath("$.clearancePercent").value(30))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].skuCode").value("600100-001-S"))
                .andExpect(jsonPath("$.variants[0].clearancePercent").doesNotExist());
    }

    @Test
    void uniqueSkuIndexRejectsDuplicateAcrossStyles() throws Exception {
        Document index = mongoTemplate.getCollection("product").listIndexes()
                .into(new ArrayList<>()).stream()
                .filter(d -> Product.SKU_CODE_INDEX.equals(d.getString("name")))
                .findFirst().orElseThrow();
        Assertions.assertEquals(Boolean.TRUE, index.getBoolean("unique"));
        Assertions.assertEquals(new Document("variants.skuCode", 1), index.get("key", Document.class));

        post(newStyle("268341", variant("268341-016-L", "Heather Grey", "29.95", "17.97", 40, true)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("duplicate-sku")));

        Product duplicate = Product.builder().styleId("268341").name("dup")
                .variants(List.of(ProductVariant.builder().skuCode("268341-016-L").build())).build();
        Assertions.assertThrows(DuplicateKeyException.class, () -> productRepository.save(duplicate));
        Assertions.assertEquals(3, productRepository.findAll().size());
    }

    @Test
    void legacyProductsWithoutVariantsDoNotCollideOnSkuIndex() throws Exception {
        post(ProductRequest.builder().name("a").price(BigDecimal.ONE).build()).andExpect(status().isCreated());
        post(ProductRequest.builder().name("b").price(BigDecimal.TEN).build()).andExpect(status().isCreated());
        Assertions.assertEquals(5, productRepository.findAll().size());
    }

    @Test
    void rejectsSalePriceAboveListPrice() throws Exception {
        expectValidationFailure(variant("600200-001-S", "Black", "29.95", "39.95", 0, false),
                "variants[0].salePrice");
    }

    @Test
    void rejectsClearancePercentOutOfRange() throws Exception {
        expectValidationFailure(variant("600200-001-S", "Black", "29.95", "29.95", 120, false),
                "variants[0].clearancePercent");
        expectValidationFailure(variant("600200-001-S", "Black", "29.95", "29.95", -5, false),
                "variants[0].clearancePercent");
    }

    @Test
    void rejectsSalePriceThatDoesNotMatchClearancePercent() throws Exception {
        expectValidationFailure(variant("600200-001-S", "Black", "29.95", "19.99", 40, false),
                "variants[0].salePrice");
    }

    private void expectValidationFailure(ProductRequest.Variant variant, String field) throws Exception {
        post(newStyle("600200", variant))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", endsWith("validation-failed")))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        Assertions.assertTrue(productRepository.findByStyleId("600200").isEmpty());
    }

    private ResultActions getBySku(String skuCode) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/api/product").param("skuCode", skuCode));
    }

    private ResultActions post(ProductRequest request) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode fixture(int index) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/mock-products.json").getInputStream()) {
            return objectMapper.readTree(in).get(index);
        }
    }

    static ProductRequest newStyle(String styleId, ProductRequest.Variant... variants) {
        return ProductRequest.builder()
                .styleId(styleId)
                .name("Test Style " + styleId)
                .description("Test Style " + styleId)
                .department("MEN'S")
                .category("TOPS")
                .variants(List.of(variants))
                .build();
    }

    static ProductRequest.Variant variant(String skuCode, String colorName, String listPrice, String salePrice,
                                          int clearancePercent, boolean finalSale) {
        return ProductRequest.Variant.builder()
                .skuCode(skuCode)
                .colorName(colorName)
                .colorCode("#000000")
                .size(skuCode.substring(skuCode.lastIndexOf('-') + 1))
                .listPrice(new BigDecimal(listPrice))
                .salePrice(new BigDecimal(salePrice))
                .clearancePercent(clearancePercent)
                .finalSale(finalSale)
                .build();
    }
}
