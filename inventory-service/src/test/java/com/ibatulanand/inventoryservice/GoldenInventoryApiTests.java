package com.ibatulanand.inventoryservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-data contract test: the dev-profile seed must reproduce MOCK_INVENTORY from
 * pos-webapp/src/api/fixtures.ts (copied verbatim into src/test/resources/mock-inventory.json).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GoldenInventoryApiTests extends MySqlContainerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CommandLineRunner loadGoldenData;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreRepository storeRepository;

    @ParameterizedTest
    @ValueSource(strings = {"268341-016-L", "471902-004-29", "512884-022-M"})
    void singleStoreFormEqualsMockInventory(String skuCode) throws Exception {
        JsonNode expected = mockInventory().get(skuCode);

        String body = mockMvc.perform(get("/api/inventory").param("skuCode", skuCode).param("storeId", "1969"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body)).isEqualTo(expected);
    }

    @Test
    void hoodieIsNotShipFromStoreEligible() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "512884-022-M").param("storeId", "1969"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipFromStoreEligible").value(false));
    }

    @Test
    void unknownSkuIsProblem404() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "000000-000-X").param("storeId", "1969"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("sku-not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownStoreIsProblem404() throws Exception {
        mockMvc.perform(get("/api/inventory").param("skuCode", "268341-016-L").param("storeId", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("store-not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void multipleSkusWithStoreIdIsValidationProblem() throws Exception {
        mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "268341-016-L", "471902-004-29").param("storeId", "1969"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("validation-failed"));
    }

    @Test
    void legacyArrayFormKeepsShapeAndAddsUnknownSkuRow() throws Exception {
        String body = mockMvc.perform(get("/api/inventory")
                        .param("skuCode", "268341-016-L", "471902-004-29", "512884-022-M", "000000-000-X"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body)).isEqualTo(objectMapper.readTree("""
                [
                  {"skuCode": "268341-016-L", "inStock": true},
                  {"skuCode": "471902-004-29", "inStock": true},
                  {"skuCode": "512884-022-M", "inStock": true},
                  {"skuCode": "000000-000-X", "inStock": false}
                ]
                """));
    }

    @Test
    void goldenSeedIsIdempotent() throws Exception {
        long inventoryRows = inventoryRepository.count();
        long stores = storeRepository.count();

        loadGoldenData.run();

        assertThat(inventoryRepository.count()).isEqualTo(inventoryRows);
        assertThat(storeRepository.count()).isEqualTo(stores);
    }

    private JsonNode mockInventory() throws Exception {
        try (InputStream in = new ClassPathResource("mock-inventory.json").getInputStream()) {
            return objectMapper.readTree(in);
        }
    }
}
