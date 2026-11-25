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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract tests for /api/till with the downstream services stubbed at the client boundary. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class TillApiTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);
    static final CatalogueItem BANANAS = new CatalogueItem("1004", "MOR-BANANA-5", "Bananas x5", 95, true);

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
        when(services.findByBarcode("1004")).thenReturn(Optional.of(BANANAS));
        when(services.quickKeys()).thenReturn(List.of(MILK, BANANAS));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-123");
        JsonNode session = call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk());
        assertThat(session.get("currency").asText()).isEqualTo("GBP");
        basketId = session.get("basketId").asText();
    }

    @Test
    void scanAddsLinesAndServerComputesTotalsInPence() throws Exception {
        add("1001", 1);
        add("1004", 2);
        JsonNode basket = add("1001", 1);
        assertThat(basket.get("lines")).hasSize(2);
        assertThat(basket.get("itemCount").asInt()).isEqualTo(4);
        assertThat(basket.get("subtotalPence").asInt()).isEqualTo(155 * 2 + 95 * 2);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(500);
        assertThat(basket.get("lastLineId").asText()).isEqualTo(basket.get("lines").get(0).get("lineId").asText());
    }

    @Test
    void quantityChangeAndVoidLine() throws Exception {
        add("1001", 1);
        String lineId = add("1004", 1).get("lines").get(1).get("lineId").asText();
        JsonNode basket = call(patch("/api/till/basket/lines/" + lineId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "quantity", 3))), status().isOk());
        assertThat(basket.get("totalPence").asInt()).isEqualTo(155 + 285);
        basket = call(delete("/api/till/basket/lines/" + lineId + "?basketId=" + basketId), status().isOk());
        assertThat(basket.get("lines")).hasSize(1);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(155);
    }

    @Test
    void voidSaleStartsANewTransaction() throws Exception {
        add("1001", 1);
        JsonNode next = call(post("/api/till/basket/void").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId))), status().isOk());
        assertThat(next.get("basketId").asText()).isNotEqualTo(basketId);
        assertThat(next.get("lines")).isEmpty();
        call(get("/api/till/basket/" + basketId), status().isOk());
        add(basketId, "1001", status().isConflict());
    }

    @Test
    void unknownBarcodeIs404AndOutOfStockIs409() throws Exception {
        add(basketId, "9999", status().isNotFound());
        when(services.isInStock("MOR-BANANA-5")).thenReturn(false);
        add(basketId, "1004", status().isConflict());
    }

    @Test
    void declinedCardKeepsBasketOpenAndPersistsNothing() throws Exception {
        add("1001", 1);
        JsonNode result = tender(Map.of("basketId", basketId, "tenderType", "CARD", "terminalOutcome", "DECLINED"),
                status().isOk());
        assertThat(result.get("status").asText()).isEqualTo("DECLINED");
        assertThat(result.get("basket").get("status").asText()).isEqualTo("OPEN");
        assertThat(result.get("nextBasketId").asText()).isEqualTo(basketId);
        verify(services, never()).placeSale(any(), anyString(), anyString());
    }

    @Test
    void approvedCardPersistsOrderAndReturnsReceipt() throws Exception {
        add("1001", 2);
        JsonNode result = tender(Map.of("basketId", basketId, "tenderType", "CARD", "terminalOutcome", "APPROVED"),
                status().isOk());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(result.get("orderNumber").asText()).isEqualTo("ORDER-123");
        assertThat(result.get("receipt").get("totalPence").asInt()).isEqualTo(310);
        assertThat(result.get("nextBasketId").asText()).isNotEqualTo(basketId);
        verify(services).placeSale(any(), eq("0417"), eq("CARD"));
    }

    @Test
    void cashTenderReturnsChangeAndRejectsShortfall() throws Exception {
        add("1001", 1);
        tender(Map.of("basketId", basketId, "tenderType", "CASH", "amountTenderedPence", 100),
                status().isUnprocessableEntity());
        JsonNode result = tender(Map.of("basketId", basketId, "tenderType", "CASH", "amountTenderedPence", 500),
                status().isOk());
        assertThat(result.get("changeDuePence").asInt()).isEqualTo(345);
    }

    @Test
    void seededMoreCardsAreNotAcceptedOutsideTheDevAndDemoProfiles() throws Exception {
        add("1001", 1);
        JsonNode error = call(post("/api/till/basket/member").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "cardNumber", "9826000000000001"))),
                status().isServiceUnavailable());
        assertThat(error.get("error").asText()).isEqualTo("MORE_CARD_UNAVAILABLE");
        JsonNode basket = call(get("/api/till/basket/" + basketId), status().isOk());
        assertThat(basket.get("member").isNull()).isTrue();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(155);
    }

    @Test
    void emptyBasketCannotBeTendered() throws Exception {
        tender(Map.of("basketId", basketId, "tenderType", "CARD"), status().isUnprocessableEntity());
    }

    private JsonNode add(String barcode, int quantity) throws Exception {
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode, "quantity", quantity))),
                status().isCreated());
    }

    private void add(String basket, String barcode, ResultMatcher expected) throws Exception {
        call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basket, "barcode", barcode))), expected);
    }

    private JsonNode tender(Map<String, Object> body, ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
