package com.ibatulanand.orderservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.client.InventoryClient;
import com.ibatulanand.orderservice.client.ProductClient;
import com.ibatulanand.orderservice.config.PricingProperties;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.UpstreamUnavailableException;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.OrderService;
import com.ibatulanand.orderservice.service.PricingService;
import com.ibatulanand.orderservice.service.PromotionResolver;
import com.ibatulanand.orderservice.service.TenderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@EnableConfigurationProperties(PricingProperties.class)
@Import({PricingService.class, OrderService.class, TenderValidator.class, PromotionResolver.class})
class OrderControllerTest {

    private static final String MOCKUP_BODY = """
            {
              "storeId": "1969",
              "registerId": "04",
              "associateId": "A-4471",
              "lineItems": [
                {"skuCode": "268341-016-L", "quantity": 1, "unitPrice": 0.01},
                {"skuCode": "471902-004-29", "quantity": 1, "unitPrice": 0.01},
                {"skuCode": "512884-022-M", "quantity": 1, "unitPrice": 0.01}
              ],
              "promotions": ["FALL30"],
              "tenders": [],
              "taxExempt": false
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductClient productClient;
    @MockBean
    InventoryClient inventoryClient;
    @MockBean
    OrderRepository orderRepository;
    @MockBean
    KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void stubCatalog() throws IOException {
        JsonNode catalog = mapper.readTree(new ClassPathResource("gap-catalog.json").getInputStream());
        for (JsonNode product : catalog) {
            ProductResponse response = mapper.treeToValue(product, ProductResponse.class);
            when(productClient.findBySku(product.get("skuCode").asText()))
                    .thenReturn(Optional.of(response));
        }
        lenient().when(productClient.findBySku(argThat(sku -> {
            for (JsonNode product : catalog) {
                if (product.get("skuCode").asText().equals(sku)) return false;
            }
            return true;
        }))).thenReturn(Optional.empty());
        lenient().when(inventoryClient.checkStock(anyList()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(
                        ((List<String>) inv.getArgument(0)).stream()
                                .map(sku -> new InventoryResponse(sku, true))
                                .toList()));
        lenient().when(orderRepository.save(any(Order.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static String tenderBody(String type, String amount) {
        return "{\"type\":\"" + type + "\",\"amount\":" + amount + "}";
    }

    private static String orderBody(String tenders, boolean taxExempt) {
        return MOCKUP_BODY.replace("\"tenders\": []", "\"tenders\": " + tenders)
                .replace("\"taxExempt\": false", "\"taxExempt\": " + taxExempt);
    }

    // ---------- quote ----------

    @Test
    void quoteMockupIgnoresClientSentPrices() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON).content(MOCKUP_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(117.65))
                .andExpect(jsonPath("$.totals.merchandiseTotal").value(159.85))
                .andExpect(jsonPath("$.lineItems.length()").value(3))
                .andExpect(jsonPath("$.lineItems[0].unitPrice").value(17.97));
        verify(orderRepository, never()).save(any());
        verify(inventoryClient, never()).checkStock(anyList());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void quoteAcceptsSpacedLowercasePromo() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOCKUP_BODY.replace("\"FALL30\"", "\" fall30 \"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions[0].code").value("FALL30"))
                .andExpect(jsonPath("$.totals.total").value(117.65));
    }

    @Test
    void quoteUnknownPromo() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOCKUP_BODY.replace("\"FALL30\"", "\"BOGUS\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Unknown promotion code: BOGUS"));
    }

    @Test
    void quoteUnknownSku() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOCKUP_BODY.replace("268341-016-L", "000000-000-X")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Unknown skuCode: 000000-000-X"));
    }

    @Test
    void quoteUnknownStore() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOCKUP_BODY.replace("\"1969\"", "\"9999\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No tax rate configured for store 9999"));
    }

    @Test
    void quoteTaxExemptWithoutRolesIsFine() throws Exception {
        mockMvc.perform(post("/api/order/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[]", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.salesTax").value(0.00))
                .andExpect(jsonPath("$.totals.taxExempt").value(true));
    }

    // ---------- order ----------

    @Test
    void placeOrderCard() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CREDIT_DEBIT", "117.65") + "]", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Order placed"))
                .andExpect(jsonPath("$.totals.total").value(117.65))
                .andExpect(jsonPath("$.changeDue").value(0.00));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals(3, saved.getOrderLineItemsList().size());
        assertEquals(1, saved.getPromotions().size());
        assertEquals(1, saved.getTenders().size());
        assertEquals("FALL30", saved.getPromotions().get(0).getCode());
        assertEquals(0, saved.getTotal().compareTo(new java.math.BigDecimal("117.65")));
        assertEquals(0, saved.getTaxRate().compareTo(new java.math.BigDecimal("0.08625")));

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate).send(eq("notificationTopic"), eventCaptor.capture());
        assertEquals(saved.getOrderNumber(), eventCaptor.getValue().getOrderNumber());
    }

    @Test
    void placeOrderCashChange() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CASH", "120.00") + "]", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeDue").value(2.35));
    }

    @Test
    void placeOrderUnderTender() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CASH", "100.00") + "]", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Tenders (100.00) do not cover the order total (117.65)"));
        verify(inventoryClient, never()).checkStock(anyList());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void taxExemptRequiresManagerRole() throws Exception {
        String body = orderBody("[" + tenderBody("CREDIT_DEBIT", "108.31") + "]", true);
        // no roles header -> 403
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tax-exempt sales require the pos-manager role"));
        // wrong role -> 403
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-User-Roles", "pos-associate"))
                .andExpect(status().isForbidden());
        // manager role -> 201, tax 0
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-User-Roles", "pos-associate,pos-manager"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totals.salesTax").value(0.00))
                .andExpect(jsonPath("$.totals.total").value(108.31));
    }

    @Test
    void headersOverrideBody() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CREDIT_DEBIT", "117.65") + "]", false)
                                .replace("\"1969\"", "\"9999\""))
                        .header("X-Store-Id", "1969")
                        .header("X-Associate-Id", "HDR-1"))
                .andExpect(status().isCreated());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals("1969", captor.getValue().getStoreId());
        assertEquals("HDR-1", captor.getValue().getAssociateId());
    }

    @Test
    void outOfStock() throws Exception {
        when(inventoryClient.checkStock(anyList()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new InventoryResponse("268341-016-L", false),
                        new InventoryResponse("471902-004-29", true),
                        new InventoryResponse("512884-022-M", true))));
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CREDIT_DEBIT", "117.65") + "]", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Products out of stock: 268341-016-L"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void inventoryUnavailable() throws Exception {
        CompletableFuture<List<InventoryResponse>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new UpstreamUnavailableException("Inventory service unavailable, please try again later"));
        when(inventoryClient.checkStock(anyList())).thenReturn(failed);
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("[" + tenderBody("CREDIT_DEBIT", "117.65") + "]", false)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Inventory service unavailable, please try again later"));
        verify(orderRepository, never()).save(any());
    }
}
