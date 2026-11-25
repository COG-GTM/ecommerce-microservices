package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.payment.SimulatedPaymentTerminal;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3938: card declines, terminal timeouts and retries never double-charge or double-order. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "till.terminal.simulator.timeout-delay-ms=300"})
@AutoConfigureMockMvc
class CardTerminalIdempotencyTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    SimulatedPaymentTerminal terminal;
    @MockBean
    HttpServiceClients services;

    String basketId;

    @BeforeEach
    void setUp() throws Exception {
        when(services.findByBarcode(anyString())).thenReturn(Optional.empty());
        when(services.findByBarcode("1001")).thenReturn(Optional.of(MILK));
        when(services.quickKeys()).thenReturn(List.of(MILK));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenAnswer(i -> "ORDER-" + UUID.randomUUID());
        basketId = newBasket();
        add(basketId, status().isCreated());
        clearInvocations(services);
    }

    @Test
    void declineThenApproveWithANewKeyPlacesOneOrder() throws Exception {
        JsonNode declined = tender(basketId, "k-" + UUID.randomUUID(), "DECLINED", status().isOk());
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("declineReason").asText()).isEqualTo("Card declined by issuer");
        assertThat(declined.get("basket").get("status").asText()).isEqualTo("OPEN");
        assertThat(declined.get("basket").get("lines")).hasSize(1);

        JsonNode approved = tender(basketId, "k-" + UUID.randomUUID(), "APPROVED", status().isOk());
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        verify(services, times(1)).placeSale(any(), anyString(), anyString());
    }

    @Test
    void timeoutThenRetryWithTheSameKeyChargesOnceAndOrdersOnce() throws Exception {
        String key = "k-" + UUID.randomUUID();
        int chargesBefore = terminal.chargeCount();

        JsonNode timedOut = tender(basketId, key, "TIMEOUT", status().isOk());
        assertThat(timedOut.get("status").asText()).isEqualTo("TIMEOUT");
        assertThat(timedOut.get("idempotencyKey").asText()).isEqualTo(key);
        assertThat(timedOut.get("basket").get("status").asText()).isEqualTo("OPEN");
        assertThat(timedOut.get("nextBasketId").asText()).isEqualTo(basketId);
        verify(services, never()).placeSale(any(), anyString(), anyString());

        JsonNode retried = tender(basketId, key, "APPROVED", status().isOk());
        assertThat(retried.get("status").asText()).isEqualTo("APPROVED");
        JsonNode again = tender(basketId, key, "APPROVED", status().isOk());
        assertThat(again.get("orderNumber").asText()).isEqualTo(retried.get("orderNumber").asText());
        assertThat(again.get("authCode").asText()).isEqualTo(retried.get("authCode").asText());

        assertThat(terminal.chargeCount() - chargesBefore).isEqualTo(1);
        verify(services, times(1)).placeSale(any(), anyString(), anyString());
    }

    @Test
    void anUnresolvedTimeoutBlocksOtherPaymentsAndBasketChanges() throws Exception {
        String key = "k-" + UUID.randomUUID();
        tender(basketId, key, "TIMEOUT", status().isOk());

        assertThat(tender(basketId, "k-" + UUID.randomUUID(), "APPROVED", status().isConflict())
                .get("error").asText()).isEqualTo("PAYMENT_UNRESOLVED");
        assertThat(cash(basketId, status().isConflict()).get("error").asText()).isEqualTo("PAYMENT_UNRESOLVED");
        assertThat(add(basketId, status().isConflict()).get("error").asText()).isEqualTo("PAYMENT_UNRESOLVED");

        assertThat(tender(basketId, key, "APPROVED", status().isOk()).get("status").asText()).isEqualTo("APPROVED");
        verify(services, times(1)).placeSale(any(), anyString(), anyString());
    }

    @Test
    void cashCannotSettleATimedOutCardAttemptEvenWithItsKey() throws Exception {
        String key = "k-" + UUID.randomUUID();
        tender(basketId, key, "TIMEOUT", status().isOk());
        Map<String, Object> body = Map.of("basketId", basketId, "tenderType", "CASH", "amountTenderedPence", 500,
                "idempotencyKey", key);
        assertThat(call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), status().isConflict()).get("error").asText())
                .isEqualTo("PAYMENT_UNRESOLVED");
        verify(services, never()).placeSale(any(), anyString(), anyString());
    }

    @Test
    void anApprovedCardWhoseOrderFailsKeepsTheBasketLockedToThatAttempt() throws Exception {
        String key = "k-" + UUID.randomUUID();
        when(services.placeSale(any(), anyString(), anyString())).thenThrow(
                new com.ibatulanand.tillservice.web.TillException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "ORDER_SERVICE_UNAVAILABLE", "down"));
        tender(basketId, key, "APPROVED", status().isServiceUnavailable());
        assertThat(cash(basketId, status().isConflict()).get("error").asText()).isEqualTo("PAYMENT_UNRESOLVED");
        assertThat(add(basketId, status().isConflict()).get("error").asText()).isEqualTo("PAYMENT_UNRESOLVED");

        doReturn("ORDER-retried").when(services).placeSale(any(), anyString(), anyString());
        int chargesBefore = terminal.chargeCount();
        assertThat(tender(basketId, key, "APPROVED", status().isOk()).get("orderNumber").asText()).isEqualTo("ORDER-retried");
        assertThat(terminal.chargeCount()).isEqualTo(chargesBefore);
    }

    @Test
    void aTimedOutKeyCannotBeUsedOnAnotherBasket() throws Exception {
        String key = "k-" + UUID.randomUUID();
        tender(basketId, key, "TIMEOUT", status().isOk());
        String other = newBasket();
        add(other, status().isCreated());
        assertThat(tender(other, key, "APPROVED", status().isConflict()).get("error").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        assertThat(tender(basketId, "bad\nkey", "APPROVED", status().isBadRequest()).get("error").asText())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThat(tender(basketId, "k".repeat(65), "APPROVED", status().isBadRequest()).get("error").asText())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void repeatedDeclineIsReplayedAndAKeyCannotMoveToAnotherBasket() throws Exception {
        String key = "k-" + UUID.randomUUID();
        tender(basketId, key, "DECLINED", status().isOk());
        assertThat(tender(basketId, key, "APPROVED", status().isOk()).get("status").asText()).isEqualTo("DECLINED");

        String other = newBasket();
        add(other, status().isCreated());
        assertThat(tender(other, key, "APPROVED", status().isConflict()).get("error").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verify(services, never()).placeSale(any(), anyString(), anyString());
    }

    @Test
    void concurrentDuplicateRequestsPlaceOneOrder() throws Exception {
        String key = "k-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Callable<JsonNode> attempt = () -> tender(basketId, key, "TIMEOUT", status().isOk());
            List<Future<JsonNode>> results = pool.invokeAll(List.of(attempt, attempt, attempt, attempt));
            long approved = 0;
            for (Future<JsonNode> result : results) {
                if ("APPROVED".equals(result.get().get("status").asText())) {
                    approved++;
                }
            }
            assertThat(approved).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
        verify(services, times(1)).placeSale(any(), anyString(), anyString());
    }

    @Test
    void otherTillsKeepTradingWhileATerminalWaits() throws Exception {
        String other = newBasket();
        add(other, status().isCreated());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> slow = pool.submit(() -> tender(basketId, "k-" + UUID.randomUUID(), "TIMEOUT", status().isOk()));
            Thread.sleep(100);
            long start = System.nanoTime();
            tender(other, "k-" + UUID.randomUUID(), "APPROVED", status().isOk());
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(250);
            assertThat(slow.get().get("status").asText()).isEqualTo("TIMEOUT");
        } finally {
            pool.shutdownNow();
        }
    }

    private String newBasket() throws Exception {
        return call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk()).get("basketId").asText();
    }

    private JsonNode add(String basket, ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basket, "barcode", "1001"))), expected);
    }

    private JsonNode tender(String basket, String key, String outcome, ResultMatcher expected) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("basketId", basket, "tenderType", "CARD",
                "terminalOutcome", outcome, "idempotencyKey", key));
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), expected);
    }

    private JsonNode cash(String basket, ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basket, "tenderType", "CASH",
                        "amountTenderedPence", 500))), expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? json.nullNode() : json.readTree(body);
    }
}
