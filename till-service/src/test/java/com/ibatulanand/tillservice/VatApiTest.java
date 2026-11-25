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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3932: per-item UK VAT codes and the VAT summary on the basket and receipt. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class VatApiTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true, "Z");
    static final CatalogueItem CHOCOLATE = new CatalogueItem("4001", "MOR-CHOC-BAR-100", "Milk chocolate bar 100g", 120, false, "S");
    static final CatalogueItem WASHING_UP = new CatalogueItem("4002", "MOR-WASHUP-500", "Washing up liquid 500ml", 135, false, "S");

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
        when(services.findByBarcode("1001")).thenReturn(Optional.of(MILK));
        when(services.findByBarcode("4001")).thenReturn(Optional.of(CHOCOLATE));
        when(services.findByBarcode("4002")).thenReturn(Optional.of(WASHING_UP));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-VAT");
        basketId = read(mvc.perform(get("/api/till/session?tillId=" + UUID.randomUUID())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("basketId").asText();
    }

    @Test
    void linesCarryVatCodeAndBasketHasSummaryPerRate() throws Exception {
        add("1001", 1);
        add("4001", 2);
        JsonNode basket = add("4002", 1);

        assertThat(basket.get("lines").get(0).get("vatCode").asText()).isEqualTo("Z");
        assertThat(basket.get("lines").get(1).get("vatCode").asText()).isEqualTo("S");
        assertThat(basket.get("totalPence").asInt()).isEqualTo(530);
        JsonNode vat = basket.get("vatSummary");
        assertThat(vat).hasSize(2);
        assertBand(vat.get(0), "Z", 0, 155, 0, 155);
        assertBand(vat.get(1), "S", 20, 312, 63, 375);
    }

    @Test
    void emptyBasketHasEmptyVatSummary() throws Exception {
        JsonNode basket = read(mvc.perform(get("/api/till/basket/" + basketId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(basket.get("vatSummary")).isEmpty();
    }

    @Test
    void receiptCarriesTheSameVatSummary() throws Exception {
        add("1001", 1);
        add("4002", 1);
        JsonNode result = read(mvc.perform(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("basketId", basketId, "tenderType", "CARD",
                                "terminalOutcome", "APPROVED"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode receipt = result.get("receipt");
        assertThat(receipt.get("totalPence").asInt()).isEqualTo(290);
        assertThat(receipt.get("lines").get(1).get("vatCode").asText()).isEqualTo("S");
        assertBand(receipt.get("vatSummary").get(0), "Z", 0, 155, 0, 155);
        assertBand(receipt.get("vatSummary").get(1), "S", 20, 112, 23, 135);
    }

    private static void assertBand(JsonNode band, String code, int rate, int net, int vat, int gross) {
        assertThat(band.get("code").asText()).isEqualTo(code);
        assertThat(band.get("ratePercent").asInt()).isEqualTo(rate);
        assertThat(band.get("netPence").asInt()).isEqualTo(net);
        assertThat(band.get("vatPence").asInt()).isEqualTo(vat);
        assertThat(band.get("grossPence").asInt()).isEqualTo(gross);
    }

    private JsonNode add(String barcode, int quantity) throws Exception {
        return read(mvc.perform(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode, "quantity", quantity))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
