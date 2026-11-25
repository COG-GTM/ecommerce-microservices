package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3934: multi-buy and meal-deal promotions configured in application.properties, through /api/till. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class PromotionApiTest {

    static final List<CatalogueItem> ITEMS = List.of(
            new CatalogueItem("393401", "MOR-SANDWICH-CHKBAC", "Chicken and bacon sandwich", 300, false),
            new CatalogueItem("393403", "MOR-CRISPS-SALTED-30G", "Ready salted crisps 30g", 85, false),
            new CatalogueItem("393406", "MOR-COLA-500", "Cola 500ml", 135, false),
            new CatalogueItem("393407", "MOR-YOGHURT-GREEK-500", "Greek style yoghurt 500g", 150, false));

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @MockBean
    HttpServiceClients services;

    String basketId;

    @BeforeEach
    void setUp() throws Exception {
        when(services.findByBarcode(anyString())).thenReturn(Optional.empty());
        ITEMS.forEach(item -> when(services.findByBarcode(item.barcode())).thenReturn(Optional.of(item)));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-3934");
        basketId = call(get("/api/till/session?tillId=" + UUID.randomUUID())).get("basketId").asText();
    }

    @Test
    void threeForPriceTriggersOnThirdScanAndUntriggersOnQtyChangeAndVoid() throws Exception {
        add("393407");
        JsonNode basket = add("393407");
        assertThat(basket.get("adjustments")).isEmpty();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(300);

        basket = add("393407");
        String lineId = basket.get("lines").get(0).get("lineId").asText();
        JsonNode saving = basket.get("adjustments").get(0);
        assertThat(saving.get("code").asText()).isEqualTo("MULTIBUY");
        assertThat(saving.get("label").asText()).isEqualTo("Greek yoghurt 3 for £3.50");
        assertThat(saving.get("amountPence").asInt()).isEqualTo(-100);
        assertThat(saving.get("lineId").asText()).isEqualTo(lineId);
        assertThat(basket.get("savingsPence").asInt()).isEqualTo(100);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(350);

        basket = call(patch("/api/till/basket/lines/" + lineId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "quantity", 2))));
        assertThat(basket.get("adjustments")).isEmpty();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(300);

        add("393407");
        basket = call(delete("/api/till/basket/lines/" + lineId + "?basketId=" + basketId));
        assertThat(basket.get("adjustments")).isEmpty();
        assertThat(basket.get("totalPence").asInt()).isZero();
    }

    @Test
    void colaGoesToTheMealDealNotTheDrinksMultiBuyAndTenderChargesTheDiscountedTotal() throws Exception {
        add("393401");
        add("393403");
        add("393406");
        JsonNode basket = add("393406");
        assertThat(basket.get("adjustments")).hasSize(1);
        assertThat(basket.get("adjustments").get(0).get("code").asText()).isEqualTo("MEAL_DEAL");
        assertThat(basket.get("adjustments").get(0).get("amountPence").asInt()).isEqualTo(-170);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(300 + 85 + 270 - 170);

        JsonNode result = call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "tenderType", "CARD",
                        "terminalOutcome", "APPROVED", "expectedTotalPence", 485))));
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(result.get("receipt").get("savingsPence").asInt()).isEqualTo(170);
        assertThat(result.get("receipt").get("totalPence").asInt()).isEqualTo(485);
    }

    private JsonNode add(String barcode) throws Exception {
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode))));
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }
}
