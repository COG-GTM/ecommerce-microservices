package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.domain.TenderLine;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3937: cash tender with server-side change, and part cash plus card (split tender). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class CashTenderApiTest {

    static final CatalogueItem CHEDDAR = new CatalogueItem("1006", "MOR-CHEDDAR-MAT-400", "Mature cheddar 400g", 350, true);
    static final CatalogueItem CHICKEN = new CatalogueItem("1007", "MOR-CHICKEN-BF-600", "Chicken breast fillets 600g", 475, true);

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
        when(services.findByBarcode("1006")).thenReturn(Optional.of(CHEDDAR));
        when(services.findByBarcode("1007")).thenReturn(Optional.of(CHICKEN));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-3937");
        basketId = call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk()).get("basketId").asText();
        add("1006");
        add("1007"); // total 825p
    }

    @Test
    void exactCashHasNoChangeAndOneTenderLine() throws Exception {
        JsonNode result = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 825), status().isOk());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(result.get("changeDuePence").asInt()).isZero();
        assertThat(result.get("receipt").get("tenderType").asText()).isEqualTo("CASH");
        assertThat(result.get("receipt").get("tenders")).hasSize(1);
        verify(services).placeSale(any(), eq("0417"), eq("CASH"));
    }

    @Test
    void cashQuoteAndTenderComputeChangeServerSide() throws Exception {
        JsonNode quote = call(get("/api/till/basket/" + basketId + "/cash-quote?amountTenderedPence=1000"), status().isOk());
        assertThat(quote.get("changeDuePence").asInt()).isEqualTo(175);
        assertThat(quote.get("remainingPence").asInt()).isZero();
        JsonNode short5 = call(get("/api/till/basket/" + basketId + "/cash-quote?amountTenderedPence=500"), status().isOk());
        assertThat(short5.get("changeDuePence").asInt()).isZero();
        assertThat(short5.get("remainingPence").asInt()).isEqualTo(325);
        call(get("/api/till/basket/" + basketId + "/cash-quote?amountTenderedPence=-1"), status().isBadRequest());

        JsonNode result = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 1000), status().isOk());
        assertThat(result.get("changeDuePence").asInt()).isEqualTo(175);
        JsonNode line = result.get("receipt").get("tenders").get(0);
        assertThat(line.get("amountPence").asInt()).isEqualTo(825);
        assertThat(line.get("tenderedPence").asInt()).isEqualTo(1000);
        assertThat(line.get("changeDuePence").asInt()).isEqualTo(175);
    }

    @Test
    void shortCashWithoutPartialFlagIsStillRejected() throws Exception {
        JsonNode error = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 500), status().isUnprocessableEntity());
        assertThat(error.get("error").asText()).isEqualTo("INSUFFICIENT_TENDER");
        tender(Map.of("tenderType", "CASH", "amountTenderedPence", 0, "partialPayment", true), status().isUnprocessableEntity());
    }

    @Test
    void partCashLeavesBalanceThatCardSettlesAsSplitTender() throws Exception {
        JsonNode partial = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 500, "partialPayment", true),
                status().isOk());
        assertThat(partial.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(partial.get("balanceDuePence").asInt()).isEqualTo(325);
        assertThat(partial.get("nextBasketId").asText()).isEqualTo(basketId);
        assertThat(partial.get("basket").get("status").asText()).isEqualTo("OPEN");
        assertThat(partial.get("basket").get("paidPence").asInt()).isEqualTo(500);
        assertThat(partial.get("basket").get("balanceDuePence").asInt()).isEqualTo(325);
        verify(services, never()).placeSale(any(), anyString(), anyString());

        JsonNode result = tender(Map.of("tenderType", "CARD", "terminalOutcome", "APPROVED", "expectedTotalPence", 825),
                status().isOk());
        assertThat(result.get("status").asText()).isEqualTo("APPROVED");
        assertThat(result.get("amountTenderedPence").asInt()).isEqualTo(325);
        JsonNode receipt = result.get("receipt");
        assertThat(receipt.get("tenderType").asText()).isEqualTo("SPLIT");
        assertThat(receipt.get("totalPence").asInt()).isEqualTo(825);
        assertThat(receipt.get("tenders")).hasSize(2);
        assertThat(receipt.get("tenders").get(1).get("tenderType").asText()).isEqualTo("CARD");
        assertThat(receipt.get("tenders").get(1).get("amountPence").asInt()).isEqualTo(325);

        ArgumentCaptor<Basket> sold = ArgumentCaptor.forClass(Basket.class);
        verify(services).placeSale(sold.capture(), eq("0417"), eq("SPLIT"));
        assertThat(sold.getValue().getTenders()).extracting(TenderLine::tenderType, TenderLine::amountPence)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CASH", 500),
                        org.assertj.core.groups.Tuple.tuple("CARD", 325));
    }

    @Test
    void declinedCardAfterPartCashKeepsTheCashAndTheBalance() throws Exception {
        tender(Map.of("tenderType", "CASH", "amountTenderedPence", 500, "partialPayment", true), status().isOk());
        JsonNode declined = tender(Map.of("tenderType", "CARD", "terminalOutcome", "DECLINED"), status().isOk());
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("basket").get("paidPence").asInt()).isEqualTo(500);
        assertThat(declined.get("balanceDuePence").asInt()).isEqualTo(325);
        JsonNode cash = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 500), status().isOk());
        assertThat(cash.get("status").asText()).isEqualTo("APPROVED");
        assertThat(cash.get("changeDuePence").asInt()).isEqualTo(175);
        verify(services).placeSale(any(), eq("0417"), eq("SPLIT"));
    }

    @Test
    void basketLinesAreLockedOnceAPaymentIsTaken() throws Exception {
        JsonNode partial = tender(Map.of("tenderType", "CASH", "amountTenderedPence", 200, "partialPayment", true),
                status().isOk());
        String lineId = partial.get("basket").get("lines").get(0).get("lineId").asText();
        JsonNode error = call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", "1006"))), status().isConflict());
        assertThat(error.get("error").asText()).isEqualTo("TENDER_IN_PROGRESS");
        call(patch("/api/till/basket/lines/" + lineId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "quantity", 3))), status().isConflict());
    }

    @Test
    void failedOrderPersistenceDoesNotKeepTheFinalTenderLine() throws Exception {
        when(services.placeSale(any(), anyString(), anyString()))
                .thenThrow(new com.ibatulanand.tillservice.web.TillException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE", "down"));
        tender(Map.of("tenderType", "CASH", "amountTenderedPence", 900), status().isServiceUnavailable());
        JsonNode basket = call(get("/api/till/basket/" + basketId), status().isOk());
        assertThat(basket.get("tenders")).isEmpty();
        assertThat(basket.get("status").asText()).isEqualTo("OPEN");
    }

    private void add(String barcode) throws Exception {
        call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode))), status().isCreated());
    }

    private JsonNode tender(Map<String, Object> body, ResultMatcher expected) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(body);
        request.put("basketId", basketId);
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)), expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
