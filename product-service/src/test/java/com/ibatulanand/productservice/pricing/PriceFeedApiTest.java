package com.ibatulanand.productservice.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"catalogue.seed.grocery=true", "pricing.source=price-feed", "eureka.client.enabled=false"})
@Testcontainers
@AutoConfigureMockMvc
class PriceFeedApiTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:4.4.24");

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    private void publish(String body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/product/price-feed").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void feedPriceIsServedOnTheNextBarcodeLookup() throws Exception {
        mockMvc.perform(get("/api/product/barcode/394102")).andExpect(jsonPath("$.pricePence").value(425));
        mockMvc.perform(put("/api/product/price-feed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prices\":[{\"barcode\":\"394102\",\"pricePence\":399}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PRICE_FEED"))
                .andExpect(jsonPath("$.prices[0].pricePence").value(399));
        mockMvc.perform(get("/api/product/barcode/394102"))
                .andExpect(jsonPath("$.pricePence").value(399))
                .andExpect(jsonPath("$.price").value(3.99));
        mockMvc.perform(get("/api/product/price-feed"))
                .andExpect(jsonPath("$.prices.length()").value(11))
                .andExpect(jsonPath("$.prices[?(@.barcode=='394102')].pricePence").value(399));
        publish("{\"prices\":[{\"barcode\":\"394102\",\"pricePence\":425.0}]}", 200);
        mockMvc.perform(get("/api/product/barcode/394102")).andExpect(jsonPath("$.pricePence").value(425));
    }

    @Test
    void invalidBatchesChangeNothing() throws Exception {
        publish("{\"prices\":[{\"barcode\":\"394103\",\"pricePence\":120},{\"barcode\":\"0000\",\"pricePence\":1}]}", 404);
        publish("{\"prices\":[{\"barcode\":\"394103\",\"pricePence\":0}]}", 400);
        publish("{\"prices\":[]}", 400);
        publish("{\"prices\":[{\"barcode\":\"394103\",\"pricePence\":119.9}]}", 400);
        mockMvc.perform(get("/api/product/barcode/394103")).andExpect(jsonPath("$.pricePence").value(99));
    }

    @Test
    void legacyImportIsRetiredWhenTheFeedIsTheSource() throws Exception {
        mockMvc.perform(post("/api/product/price-feed/legacy-import"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LEGACY_PRICE_FILE_RETIRED"));
    }
}
