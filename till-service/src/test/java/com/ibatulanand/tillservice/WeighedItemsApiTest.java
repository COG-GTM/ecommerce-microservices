package com.ibatulanand.tillservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.tillservice.barcode.EmbeddedPriceBarcode;
import com.ibatulanand.tillservice.client.HttpServiceClients;
import com.ibatulanand.tillservice.domain.Basket;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3935: price-embedded EAN-13 labels and items sold by weight. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class WeighedItemsApiTest {

    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);
    static final CatalogueItem HAM = new CatalogueItem("2101", "MOR-DELI-HAM-KG", "Deli honey roast ham", 1290, false, true);
    static final CatalogueItem TOMATOES = new CatalogueItem("2201", "MOR-TOMATO-LOOSE-KG", "Loose tomatoes", 220, false, true);

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
        when(services.findByBarcode("2101")).thenReturn(Optional.of(HAM));
        when(services.findByBarcode("2201")).thenReturn(Optional.of(TOMATOES));
        when(services.quickKeys()).thenReturn(List.of(MILK));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-W1");
        basketId = call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk()).get("basketId").asText();
    }

    @Test
    void embeddedPriceLabelAddsOneLineAtTheLabelPrice() throws Exception {
        add(Map.of("barcode", "2002101003490"), status().isCreated());
        JsonNode basket = add(Map.of("barcode", "2002101003490"), status().isCreated());
        assertThat(basket.get("lines")).hasSize(2);
        JsonNode line = basket.get("lines").get(0);
        assertThat(line.get("name").asText()).isEqualTo("Deli honey roast ham");
        assertThat(line.get("pricedBy").asText()).isEqualTo("LABEL");
        assertThat(line.get("barcode").asText()).isEqualTo("2002101003490");
        assertThat(line.get("unitPricePence").asInt()).isEqualTo(349);
        assertThat(line.get("linePence").asInt()).isEqualTo(349);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(698);
    }

    @Test
    void invalidCheckDigitIsRejectedAndBasketUnchanged() throws Exception {
        mvc.perform(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", "2002101003491"))))
                .andExpect(jsonPath("$.error").value("INVALID_CHECK_DIGIT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("invalid check digit")));
        assertThat(call(get("/api/till/basket/" + basketId), status().isOk()).get("lines")).isEmpty();
    }

    @Test
    void labelForUnknownPluIs404() throws Exception {
        // PLU 9999, 100p, valid check digit
        String barcode = "2009999" + "00100";
        barcode += com.ibatulanand.tillservice.barcode.EmbeddedPriceBarcode.checkDigit(barcode);
        assertThat(add(Map.of("barcode", barcode), status().isNotFound()).get("error").asText()).isEqualTo("UNKNOWN_BARCODE");
    }

    @Test
    void looseItemWithoutWeightPromptsForWeight() throws Exception {
        JsonNode error = add(Map.of("barcode", "2201"), status().isUnprocessableEntity());
        assertThat(error.get("error").asText()).isEqualTo("WEIGHT_REQUIRED");
        assertThat(error.get("message").asText()).contains("Loose tomatoes");
    }

    @Test
    void manualWeightIsPricedPerKgRoundedHalfUp() throws Exception {
        add(Map.of("barcode", "1001"), status().isCreated());
        JsonNode basket = add(Map.of("barcode", "2201", "weightGrams", 452), status().isCreated());
        JsonNode line = basket.get("lines").get(1);
        assertThat(line.get("pricedBy").asText()).isEqualTo("WEIGHT");
        assertThat(line.get("weightGrams").asInt()).isEqualTo(452);
        assertThat(line.get("unitPricePence").asInt()).isEqualTo(220);
        assertThat(line.get("linePence").asInt()).isEqualTo(99); // 0.452 kg x £2.20 = 99.44p
        assertThat(basket.get("lastLineId").asText()).isEqualTo(line.get("lineId").asText());
        assertThat(basket.get("itemCount").asInt()).isEqualTo(2);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(155 + 99);

        basket = add(Map.of("barcode", "2201", "weightGrams", 1250), status().isCreated());
        assertThat(basket.get("lines")).hasSize(3);
        assertThat(basket.get("lines").get(2).get("linePence").asInt()).isEqualTo(275);
    }

    @Test
    void weightIsValidated() throws Exception {
        add(Map.of("barcode", "2201", "weightGrams", 0), status().isBadRequest());
        add(Map.of("barcode", "2201", "weightGrams", 30_001), status().isBadRequest());
        assertThat(add(Map.of("barcode", "1001", "weightGrams", 500), status().isBadRequest()).get("error").asText())
                .isEqualTo("NOT_SOLD_BY_WEIGHT");
    }

    @Test
    void labelForAnItemSoldByTheUnitIsRejected() throws Exception {
        String barcode = "2001001" + "00010";
        barcode += EmbeddedPriceBarcode.checkDigit(barcode);
        assertThat(add(Map.of("barcode", barcode), status().isUnprocessableEntity()).get("error").asText())
                .isEqualTo("NOT_SOLD_BY_WEIGHT");
        assertThat(basket().get("lines")).isEmpty();
    }

    @Test
    void zeroPriceLabelIsRejected() throws Exception {
        String barcode = "2002101" + "00000";
        barcode += EmbeddedPriceBarcode.checkDigit(barcode);
        assertThat(add(Map.of("barcode", barcode), status().isUnprocessableEntity()).get("error").asText())
                .isEqualTo("INVALID_LABEL_PRICE");
    }

    @Test
    void cataloguedPrefix2BarcodeStillNeedsAValidCheckDigit() throws Exception {
        String bad = "212345678901" + (EmbeddedPriceBarcode.checkDigit("212345678901") + 1) % 10;
        when(services.findByBarcode(bad)).thenReturn(Optional.of(
                new CatalogueItem(bad, "MOR-GIFT-CARD", "Gift card", 1000, false)));
        assertThat(add(Map.of("barcode", bad), status().isUnprocessableEntity()).get("error").asText())
                .isEqualTo("INVALID_CHECK_DIGIT");
    }

    @Test
    void cataloguedPrefix2BarcodeIsSoldAsTheCatalogueItem() throws Exception {
        String barcode = "212345678901" + EmbeddedPriceBarcode.checkDigit("212345678901");
        CatalogueItem gift = new CatalogueItem(barcode, "MOR-GIFT-CARD", "Gift card", 1000, false);
        when(services.findByBarcode(barcode)).thenReturn(Optional.of(gift));
        JsonNode line = add(Map.of("barcode", barcode), status().isCreated()).get("lines").get(0);
        assertThat(line.get("pricedBy").asText()).isEqualTo("EACH");
        assertThat(line.get("linePence").asInt()).isEqualTo(1000);
    }

    @Test
    void weighedAndLabelledAddsTakeOneAtATime() throws Exception {
        assertThat(add(Map.of("barcode", "2201", "weightGrams", 500, "quantity", 2), status().isUnprocessableEntity())
                .get("error").asText()).isEqualTo("QUANTITY_NOT_ALLOWED");
        assertThat(add(Map.of("barcode", "2002101003490", "quantity", 3), status().isUnprocessableEntity())
                .get("error").asText()).isEqualTo("QUANTITY_NOT_ALLOWED");
        assertThat(basket().get("lines")).isEmpty();
    }

    @Test
    void weightThatPricesToZeroIsRejected() throws Exception {
        assertThat(add(Map.of("barcode", "2201", "weightGrams", 2), status().isUnprocessableEntity())
                .get("error").asText()).isEqualTo("WEIGHT_TOO_LOW");
        add(Map.of("barcode", "2201", "weightGrams", 3), status().isCreated());
    }

    @Test
    void weighedLineQuantityCannotBeChanged() throws Exception {
        String lineId = add(Map.of("barcode", "2201", "weightGrams", 500), status().isCreated())
                .get("lines").get(0).get("lineId").asText();
        JsonNode error = call(patch("/api/till/basket/lines/" + lineId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "quantity", 2))), status().isUnprocessableEntity());
        assertThat(error.get("error").asText()).isEqualTo("QUANTITY_NOT_ALLOWED");
    }

    @Test
    void tenderChargesWeighedAndLabelledLines() throws Exception {
        add(Map.of("barcode", "2002101003490"), status().isCreated());
        add(Map.of("barcode", "2201", "weightGrams", 452), status().isCreated());
        JsonNode result = call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "tenderType", "CARD",
                        "terminalOutcome", "APPROVED"))), status().isOk());
        assertThat(result.get("receipt").get("totalPence").asInt()).isEqualTo(448);
        assertThat(result.get("receipt").get("lines").get(1).get("weightGrams").asInt()).isEqualTo(452);
        ArgumentCaptor<Basket> sold = ArgumentCaptor.forClass(Basket.class);
        verify(services).placeSale(sold.capture(), anyString(), anyString());
        assertThat(sold.getValue().getTotalPence()).isEqualTo(448);
    }

    private JsonNode basket() throws Exception {
        return call(get("/api/till/basket/" + basketId), status().isOk());
    }

    private JsonNode add(Map<String, Object> fields, ResultMatcher expected) throws Exception {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("basketId", basketId);
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), expected);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
