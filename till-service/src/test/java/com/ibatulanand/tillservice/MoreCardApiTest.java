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

/** MBA-3933 More Card loyalty: member attach, member prices as savings, server-side points on the receipt. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class MoreCardApiTest {

    static final String MEMBER_CARD = "9826000000000002";
    static final CatalogueItem MILK = new CatalogueItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155, true);
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
        when(services.findByBarcode("1001")).thenReturn(Optional.of(MILK));
        when(services.findByBarcode("1006")).thenReturn(Optional.of(CHEDDAR));
        when(services.findByBarcode("1007")).thenReturn(Optional.of(CHICKEN));
        when(services.isInStock(anyString())).thenReturn(true);
        when(services.placeSale(any(), anyString(), anyString())).thenReturn("ORDER-123");
        basketId = call(get("/api/till/session?tillId=" + UUID.randomUUID()), status().isOk()).get("basketId").asText();
    }

    @Test
    void memberCardAttachesMemberAndAppliesMemberPricesAsSavings() throws Exception {
        add("1006", 2);
        add("1001", 1);
        JsonNode basket = attach(MEMBER_CARD, status().isOk());
        assertThat(basket.get("member").get("name").asText()).isEqualTo("Test Member Two");
        assertThat(basket.get("member").get("cardLast4").asText()).isEqualTo("0002");
        assertThat(basket.has("memberCardNumber")).isFalse();
        assertThat(basket.get("subtotalPence").asInt()).isEqualTo(350 * 2 + 155);
        assertThat(basket.get("savingsPence").asInt()).isEqualTo(100);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(755);
        assertThat(basket.get("adjustments").get(0).get("code").asText()).isEqualTo("MORE_CARD");

        basket = add("1007", 1);
        assertThat(basket.get("savingsPence").asInt()).isEqualTo(100 + 75);
        assertThat(basket.get("totalPence").asInt()).isEqualTo(755 + 400);
    }

    @Test
    void cardNumberWithSpacesIsAccepted() throws Exception {
        assertThat(attach("9826 0000 0000 0002", status().isOk()).get("member").get("cardLast4").asText())
                .isEqualTo("0002");
    }

    @Test
    void nonMemberPaysShelfPricesAndEarnsNoPoints() throws Exception {
        add("1006", 1);
        JsonNode basket = add("1001", 1);
        assertThat(basket.get("member").isNull()).isTrue();
        assertThat(basket.get("savingsPence").asInt()).isZero();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(505);
        JsonNode receipt = tender().get("receipt");
        assertThat(receipt.get("totalPence").asInt()).isEqualTo(505);
        assertThat(receipt.get("moreCard").isNull()).isTrue();
    }

    @Test
    void pointsAreCalculatedServerSideOnTheAmountPaidAndShownOnTheReceipt() throws Exception {
        attach(MEMBER_CARD, status().isOk());
        add("1006", 1);
        add("1001", 1);
        JsonNode first = tender();
        assertThat(first.get("receipt").get("totalPence").asInt()).isEqualTo(455);
        JsonNode moreCard = first.get("receipt").get("moreCard");
        assertThat(moreCard.get("pointsEarned").asInt()).isEqualTo(20);
        assertThat(moreCard.get("cardLast4").asText()).isEqualTo("0002");
        int balance = moreCard.get("pointsBalance").asInt();

        basketId = first.get("nextBasketId").asText();
        JsonNode next = call(get("/api/till/basket/" + basketId), status().isOk());
        assertThat(next.get("member").isNull()).isTrue();
        attach(MEMBER_CARD, status().isOk());
        add("1007", 1);
        JsonNode second = tender().get("receipt").get("moreCard");
        assertThat(second.get("pointsEarned").asInt()).isEqualTo(20);
        assertThat(second.get("pointsBalance").asInt()).isEqualTo(balance + 20);
        assertThat(call(get("/api/till/basket/" + basketId), status().isOk()).get("member").get("pointsBalance").asInt())
                .isEqualTo(balance + 20);
    }

    @Test
    void unknownCardIs404AndTheSaleContinuesAsNonMember() throws Exception {
        add("1006", 1);
        JsonNode error = attach("9826999999999999", status().isNotFound());
        assertThat(error.get("error").asText()).isEqualTo("UNKNOWN_CARD");
        assertThat(error.get("message").asText()).isEqualTo("More Card not recognised. Continuing as non-member.");
        JsonNode basket = add("1001", 1);
        assertThat(basket.get("member").isNull()).isTrue();
        assertThat(basket.get("totalPence").asInt()).isEqualTo(505);
        assertThat(tender().get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void unknownCardAfterAMemberCardKeepsTheFirstCard() throws Exception {
        attach(MEMBER_CARD, status().isOk());
        JsonNode error = attach("9826999999999999", status().isNotFound());
        assertThat(error.get("message").asText()).isEqualTo("More Card not recognised. Keeping Test Member Two's card.");
        assertThat(call(get("/api/till/basket/" + basketId), status().isOk()).get("member").get("cardLast4").asText())
                .isEqualTo("0002");
    }

    @Test
    void memberCannotBeAttachedToAClosedBasketOrWithoutACardNumber() throws Exception {
        add("1001", 1);
        tender();
        attach(MEMBER_CARD, status().isConflict());
        call(post("/api/till/basket/member").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "cardNumber", ""))), status().isBadRequest());
    }

    private JsonNode attach(String card, ResultMatcher expected) throws Exception {
        return call(post("/api/till/basket/member").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "cardNumber", card))), expected);
    }

    private JsonNode add(String barcode, int quantity) throws Exception {
        return call(post("/api/till/basket/lines").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "barcode", barcode, "quantity", quantity))),
                status().isCreated());
    }

    private JsonNode tender() throws Exception {
        return call(post("/api/till/basket/tender").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("basketId", basketId, "tenderType", "CARD",
                        "terminalOutcome", "APPROVED"))), status().isOk());
    }

    private JsonNode call(MockHttpServletRequestBuilder request, ResultMatcher expected) throws Exception {
        String body = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
