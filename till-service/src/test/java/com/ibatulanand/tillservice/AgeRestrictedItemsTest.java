package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3936: Challenge 25 approval for age-restricted lines. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class AgeRestrictedItemsTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);
    static final CatalogueItem LAGER = new CatalogueItem("3001", "MOR-LAGER-4X440", "Lager 4 x 440ml", 450, false, 18);

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
        when(services.findByBarcode("3001")).thenReturn(Optional.of(LAGER));
        when(services.quickKeys()).thenReturn(List.of(MILK));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-AGE");
        basketId = call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk()).get("basketId").asText();
    }

    @Test
    void restrictedLineIsPendingAndUnrestrictedLineHasNoCheck() throws Exception {
        add("1001");
        JsonNode basket = add("3001");
        assertThat(basket.get("pendingAgeChecks").asInt()).isEqualTo(1);
        assertThat(basket.get("lines").get(0).get("ageCheck").isNull()).isTrue();
        JsonNode lager = basket.get("lines").get(1);
        assertThat(lager.get("minimumAge").asInt()).isEqualTo(18);
        assertThat(lager.get("ageCheck").asText()).isEqualTo("PENDING");
    }

    @Test
    void tenderIsBlockedUntilEveryRestrictedLineIsApproved() throws Exception {
        add("1001");
        String lineId = add("3001").get("lastLineId").asText();
        JsonNode error = tender(status().isConflict());
        assertThat(error.get("error").asText()).isEqualTo("AGE_CHECK_REQUIRED");
        verify(services, never()).placeSale(any(), anyString(), anyString());

        JsonNode basket = ageCheck(lineId, Map.of("basketId", basketId, "decision", "APPROVED", "operatorId", "OP-FORGED"),
                status().isOk());
        assertThat(basket.get("pendingAgeChecks").asInt()).isZero();
        assertThat(basket.get("lines").get(1).get("ageCheck").asText()).isEqualTo("APPROVED");
        assertThat(basket.get("lines").get(1).get("ageApprovedBy").asText()).isEqualTo("OP-1001");

        JsonNode result = tender(status().isOk());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        ArgumentCaptor<Basket> sold = ArgumentCaptor.forClass(Basket.class);
        verify(services).placeSale(sold.capture(), eq("0417"), eq("CARD"));
        BasketLine soldLager = sold.getValue().getLines().get(1);
        assertThat(soldLager.getAgeApprovedBy()).isEqualTo("OP-1001");
        assertThat(soldLager.getMinimumAge()).isEqualTo(18);
    }

    @Test
    void approvalRecordsTheSessionOperatorAndIgnoresAClientSuppliedOne() throws Exception {
        String lineId = add("3001").get("lastLineId").asText();
        JsonNode basket = ageCheck(lineId, Map.of("basketId", basketId, "decision", "approved", "operatorId", "OP-FORGED"),
                status().isOk());
        assertThat(basket.get("lines").get(0).get("ageApprovedBy").asText()).isEqualTo("OP-1001");
    }

    @Test
    void refusalRemovesTheLineAndUnblocksPayment() throws Exception {
        add("1001");
        String lineId = add("3001").get("lastLineId").asText();
        JsonNode basket = ageCheck(lineId, Map.of("basketId", basketId, "decision", "REFUSED"), status().isOk());
        assertThat(basket.get("lines")).hasSize(1);
        assertThat(basket.get("pendingAgeChecks").asInt()).isZero();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(155);
        assertThat(tender(status().isOk()).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void invalidDecisionsAreRejected() throws Exception {
        String milkLine = add("1001").get("lastLineId").asText();
        String lagerLine = add("3001").get("lastLineId").asText();
        assertThat(ageCheck(milkLine, Map.of("basketId", basketId, "decision", "APPROVED"),
                status().isUnprocessableEntity()).get("error").asText()).isEqualTo("NOT_AGE_RESTRICTED");
        assertThat(ageCheck(lagerLine, Map.of("basketId", basketId, "decision", "MAYBE"),
                status().isBadRequest()).get("error").asText()).isEqualTo("INVALID_AGE_DECISION");
        ageCheck("no-such-line", Map.of("basketId", basketId, "decision", "APPROVED"), status().isNotFound());
    }

    private JsonNode add(String barcode) throws Exception {
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode))), status().isCreated());
    }

    private JsonNode ageCheck(String lineId, Map<String, Object> body, ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/lines/" + lineId + "/age-check").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), expected);
    }

    private JsonNode tender(ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "tenderType", "CARD",
                        "terminalOutcome", "APPROVED"))), expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
