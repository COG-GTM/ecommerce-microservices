package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.web.RefundDtos.RefundLineRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundOrder;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableLine;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableSale;
import com.ibatulanand.tillservice.web.TillException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract tests for /api/till/refund with order-service stubbed at the client boundary. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class RefundApiTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @MockBean
    HttpServiceClients services;

    @BeforeEach
    void setUp() {
        when(services.quickKeys()).thenReturn(List.of(MILK));
        doThrow(new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_ORDER", "No completed sale"))
                .when(services).refundable(anyString());
        doReturn(sale(1)).when(services).refundable("R-0001-T1-0001");
        doReturn(sale(1)).when(services).refundable("ORDER-1");
    }

    @Test
    void lookupReturnsSaleLinesWithNamesAndRefundableQuantities() throws Exception {
        JsonNode sale = call(get("/api/till/refund/R-0001-T1-0001"), null, status().isOk());
        assertThat(sale.get("orderNumber").asText()).isEqualTo("ORDER-1");
        JsonNode line = sale.get("lines").get(0);
        assertThat(line.get("name").asText()).isEqualTo("Semi skimmed milk 4 pints");
        assertThat(line.get("soldQuantity").asInt()).isEqualTo(3);
        assertThat(line.get("refundableQuantity").asInt()).isEqualTo(2);
    }

    @Test
    void unknownReceiptIs404() throws Exception {
        JsonNode error = call(get("/api/till/refund/R-NOPE"), null, status().isNotFound());
        assertThat(error.get("error").asText()).isEqualTo("UNKNOWN_ORDER");
    }

    @Test
    void refundPersistsThroughOrderServiceAndReturnsTheRemainingQuantities() throws Exception {
        when(services.refund(eq("ORDER-1"), anyString(), eq("T9"), anyString(), anyList()))
                .thenReturn(new RefundOrder("REFUND-1", "ORDER-1", "0001-T9-R0001", -310));
        JsonNode result = call(post("/api/till/refund"), Map.of("tillId", "T9", "reference", "R-0001-T1-0001",
                "lines", List.of(Map.of("skuCode", "MOR-MILK-SS-4PT", "quantity", 2))), status().isCreated());

        assertThat(result.get("status").asText()).isEqualTo("REFUNDED");
        assertThat(result.get("refundNumber").asText()).isEqualTo("REFUND-1");
        assertThat(result.get("originalOrderNumber").asText()).isEqualTo("ORDER-1");
        assertThat(result.get("refundPence").asInt()).isEqualTo(310);
        assertThat(result.get("transactionNumber").asText()).contains("-T9-R");
        assertThat(result.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void refundTotalIsTheSaleNotTheShelfPriceAndTheReceiptNameWins() throws Exception {
        doReturn(new RefundableSale("ORDER-2", "0001-T1-0002", "0001", "T1", "CARD", 250, 0, 250,
                List.of(new RefundableLine(1L, "MOR-MILK-SS-4PT", "Milk as printed", null, 250, 3, 0, 3)), List.of()))
                .when(services).refundable("ORDER-2");
        when(services.refund(eq("ORDER-2"), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new RefundOrder("REFUND-2", "ORDER-2", "0001-T1-R0001", null));
        JsonNode result = call(post("/api/till/refund"), Map.of("reference", "ORDER-2",
                "lines", List.of(Map.of("skuCode", "MOR-MILK-SS-4PT", "quantity", 2))), status().isCreated());

        assertThat(result.get("refundPence").asInt()).isEqualTo(166);
        assertThat(result.get("lines").get(0).get("name").asText()).isEqualTo("Milk as printed");
    }

    @Test
    void weighedPacksRefundTheirOwnPaidPriceAndOrderServiceIsToldWhichPack() throws Exception {
        doReturn(new RefundableSale("ORDER-3", "0001-T1-0003", "0001", "T1", "CARD", 801, 0, 801, List.of(
                new RefundableLine(11L, "MOR-SALMON", "Salmon fillets", 412, 412, 1, 0, 1),
                new RefundableLine(12L, "MOR-SALMON", "Salmon fillets", 389, 389, 1, 0, 1)), List.of()))
                .when(services).refundable("ORDER-3");
        when(services.refund(eq("ORDER-3"), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new RefundOrder("REFUND-3", "ORDER-3", "0001-T1-R0001", -389));
        JsonNode result = call(post("/api/till/refund"), Map.of("reference", "ORDER-3",
                "lines", List.of(Map.of("lineId", 12, "skuCode", "MOR-SALMON", "quantity", 1))), status().isCreated());

        assertThat(result.get("refundPence").asInt()).isEqualTo(389);
        assertThat(result.get("lines").get(0).get("unitPricePence").asInt()).isEqualTo(389);
        verify(services).refund(eq("ORDER-3"), anyString(), anyString(), anyString(),
                eq(List.of(new RefundLineRequest("MOR-SALMON", 1, 12L))));
    }

    @Test
    void refundLinesCarryExactUnitPricesNotARoundedAverage() throws Exception {
        doReturn(new RefundableSale("ORDER-4", "0001-T1-0004", "0001", "T1", "CARD", 250, 0, 250,
                List.of(new RefundableLine(21L, "MOR-YOGHURT", "Greek yoghurt", null, 250, 3, 0, 3)), List.of()))
                .when(services).refundable("ORDER-4");
        when(services.refund(eq("ORDER-4"), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new RefundOrder("REFUND-4", "ORDER-4", "0001-T1-R0001", -250));
        JsonNode result = call(post("/api/till/refund"), Map.of("reference", "ORDER-4",
                "lines", List.of(Map.of("skuCode", "MOR-YOGHURT", "quantity", 3))), status().isCreated());

        JsonNode lines = result.get("lines");
        assertThat(lines).hasSize(2);
        int total = 0;
        for (JsonNode line : lines) {
            assertThat(line.get("unitPricePence").asInt() * line.get("quantity").asInt())
                    .isEqualTo(line.get("lineTotalPence").asInt());
            total += line.get("lineTotalPence").asInt();
        }
        assertThat(total).isEqualTo(250).isEqualTo(result.get("refundPence").asInt());
    }

    @Test
    void overRefundIsRejectedBeforeCallingOrderService() throws Exception {
        JsonNode error = call(post("/api/till/refund"), Map.of("reference", "ORDER-1",
                "lines", List.of(Map.of("skuCode", "MOR-MILK-SS-4PT", "quantity", 3))), status().isConflict());
        assertThat(error.get("error").asText()).isEqualTo("REFUND_EXCEEDS_SOLD");
        verify(services, never()).refund(anyString(), anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void orderServiceRejectionIsPassedThrough() throws Exception {
        when(services.refund(anyString(), anyString(), anyString(), anyString(), anyList())).thenThrow(
                new TillException(HttpStatus.CONFLICT, "REFUND_EXCEEDS_SOLD", "already refunded"));
        JsonNode error = call(post("/api/till/refund"), Map.of("reference", "ORDER-1",
                "lines", List.of(Map.of("skuCode", "MOR-MILK-SS-4PT", "quantity", 1))), status().isConflict());
        assertThat(error.get("error").asText()).isEqualTo("REFUND_EXCEEDS_SOLD");
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        call(post("/api/till/refund"), Map.of("reference", "ORDER-1", "lines", List.of()), status().isBadRequest());
        call(post("/api/till/refund"), Map.of("reference", "ORDER-1", "lines", java.util.Collections.singletonList(null)),
                status().isBadRequest());
        call(post("/api/till/refund"), Map.of("reference", "ORDER-1",
                "lines", List.of(Map.of("skuCode", "MOR-MILK-SS-4PT", "quantity", 0))), status().isBadRequest());
        JsonNode error = call(post("/api/till/refund"), Map.of("reference", "ORDER-1",
                "lines", List.of(Map.of("skuCode", "MOR-BREAD", "quantity", 1))), status().isBadRequest());
        assertThat(error.get("error").asText()).isEqualTo("INVALID_REFUND");
        verify(services, never()).refund(any(), any(), any(), any(), any());
    }

    private static RefundableSale sale(int alreadyRefunded) {
        return new RefundableSale("ORDER-1", "0001-T1-0001", "0001", "T1", "CARD", 465, 155 * alreadyRefunded,
                465 - 155 * alreadyRefunded,
                List.of(new RefundableLine(1L, "MOR-MILK-SS-4PT", null, 155, 465, 3, alreadyRefunded, 3 - alreadyRefunded)),
                List.of());
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                          Object body, ResultMatcher expected) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String response = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response);
    }
}
