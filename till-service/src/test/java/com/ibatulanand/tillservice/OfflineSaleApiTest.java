package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.web.TillException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3940: store-and-forward replay of cash sales the till completed while offline. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class OfflineSaleApiTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @MockBean
    HttpServiceClients services;

    String saleId;

    @BeforeEach
    void setUp() {
        saleId = UUID.randomUUID().toString();
        when(services.findByBarcode(anyString())).thenReturn(Optional.empty());
        when(services.findByBarcode("1001")).thenReturn(Optional.of(MILK));
        when(services.placeSale(any(), anyString(), anyString(), anyString())).thenReturn("ORDER-OFF-1");
    }

    @Test
    void replaysCashSaleWithIdempotencyKeyAtThePriceCharged() throws Exception {
        JsonNode result = sync(sale("CASH", List.of(line("1001", 2, 140))), status().isOk());

        ArgumentCaptor<Basket> basket = ArgumentCaptor.forClass(Basket.class);
        verify(services).placeSale(basket.capture(), eq("0417"), eq("CASH"), eq("offline-" + saleId));
        assertThat(basket.getValue().getTillId()).isEqualTo("till-off");
        assertThat(basket.getValue().getTransactionNumber()).isEqualTo("0417-till-off-OFF1");
        assertThat(basket.getValue().getLines()).singleElement().satisfies(l -> {
            assertThat(l.getSkuCode()).isEqualTo("MOR-MILK-SS-4PT");
            assertThat(l.getUnitPricePence()).isEqualTo(140);
        });
        assertThat(result.get("orderNumber").asText()).isEqualTo("ORDER-OFF-1");
        assertThat(result.get("saleId").asText()).isEqualTo(saleId);
        assertThat(result.get("totalPence").asInt()).isEqualTo(280);
        assertThat(result.get("amountTenderedPence").asInt()).isEqualTo(500);
    }

    @Test
    void replayingTheSameSaleUsesTheSameKey() throws Exception {
        Map<String, Object> sale = sale("CASH", List.of(line("1001", 1, 155)));
        JsonNode first = sync(sale, status().isOk());
        JsonNode again = sync(sale, status().isOk());

        assertThat(again.get("orderNumber").asText()).isEqualTo(first.get("orderNumber").asText());
        verify(services, org.mockito.Mockito.times(2))
                .placeSale(any(), anyString(), anyString(), eq("offline-" + saleId));
    }

    @Test
    void itemsMissingFromTheCatalogueKeepTheTillsCachedValues() throws Exception {
        Map<String, Object> cached = Map.of("barcode", "7777", "skuCode", "MOR-CACHED", "name", "Cached item",
                "quantity", 1, "unitPricePence", 250);
        JsonNode result = sync(sale("CASH", List.of(cached)), status().isOk());
        assertThat(result.get("totalPence").asInt()).isEqualTo(250);
    }

    @Test
    void cardIsNeverTakenOffline() throws Exception {
        JsonNode error = sync(sale("CARD", List.of(line("1001", 1, 155))), status().isBadRequest());
        assertThat(error.get("error").asText()).isEqualTo("UNSUPPORTED_TENDER");
        verify(services, never()).placeSale(any(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownBarcodeWithoutCachedValuesIsRejected() throws Exception {
        sync(sale("CASH", List.of(Map.of("barcode", "0000", "quantity", 1))), status().isUnprocessableEntity());
    }

    @Test
    void emptySaleIsInvalid() throws Exception {
        sync(sale("CASH", List.of()), status().isBadRequest());
    }

    @Test
    void orderServiceDownKeepsTheSaleQueued() throws Exception {
        when(services.placeSale(any(), anyString(), anyString(), anyString()))
                .thenThrow(new TillException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE", "order-service down"));
        JsonNode error = sync(sale("CASH", List.of(line("1001", 1, 155))), status().isServiceUnavailable());
        assertThat(error.get("error").asText()).isEqualTo("BACKEND_UNAVAILABLE");
    }

    private Map<String, Object> sale(String tender, List<Map<String, Object>> lines) {
        return Map.of("saleId", saleId, "tillId", "till-off", "transactionNumber", "0417-till-off-OFF1",
                "tenderType", tender, "lines", lines, "amountTenderedPence", 500);
    }

    private static Map<String, Object> line(String barcode, int quantity, int cachedPricePence) {
        return Map.of("barcode", barcode, "skuCode", "CACHED-" + barcode, "name", "Cached " + barcode,
                "quantity", quantity, "unitPricePence", cachedPricePence);
    }

    private JsonNode sync(Map<String, Object> body, ResultMatcher expected) throws Exception {
        String response = mvc.perform(post("/api/till/offline-sales").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(expected).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : json.readTree(response);
    }
}
