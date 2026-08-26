package com.ibatulanand.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.support.TestWebClients;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderPlacedEvent> eventCaptor;

    private MockWebServer inventoryService;
    private OrderService orderService;

    @BeforeEach
    void setUp() throws IOException {
        inventoryService = new MockWebServer();
        inventoryService.start();
        when(webClientBuilder.build()).thenReturn(TestWebClients.redirectingTo(inventoryService).build());
        orderService = new OrderService(orderRepository, webClientBuilder, kafkaTemplate);
    }

    @AfterEach
    void tearDown() throws IOException {
        inventoryService.shutdown();
    }

    @Test
    void placesOrderAndPublishesEventWhenAllProductsAreInStock() throws Exception {
        enqueueInventoryResponse(
                new InventoryResponse("iphone_13", true),
                new InventoryResponse("iphone_13_red", true));

        String result = orderService.placeOrder(orderRequest());

        assertThat(result).isEqualTo("Order Placed Successfully!");

        verify(orderRepository).save(orderCaptor.capture());
        verify(kafkaTemplate).send(eq("notificationTopic"), eventCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getOrderNumber()).isNotBlank();
        assertThat(eventCaptor.getValue().getOrderNumber()).isEqualTo(savedOrder.getOrderNumber());
    }

    @Test
    void mapsOrderLineItemDtosOntoPersistedEntities() throws Exception {
        enqueueInventoryResponse(new InventoryResponse("iphone_13", true),
                new InventoryResponse("iphone_13_red", true));

        orderService.placeOrder(orderRequest());

        verify(orderRepository).save(orderCaptor.capture());
        List<OrderLineItems> lineItems = orderCaptor.getValue().getOrderLineItemsList();
        assertThat(lineItems).hasSize(2);
        assertThat(lineItems)
                .extracting(OrderLineItems::getSkuCode, OrderLineItems::getPrice, OrderLineItems::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_13", new BigDecimal("1200"), 1),
                        org.assertj.core.groups.Tuple.tuple("iphone_13_red", new BigDecimal("1500"), 2));
    }

    @Test
    void queriesInventoryServiceWithEverySkuCode() throws Exception {
        enqueueInventoryResponse(new InventoryResponse("iphone_13", true),
                new InventoryResponse("iphone_13_red", true));

        orderService.placeOrder(orderRequest());

        RecordedRequest recordedRequest = inventoryService.takeRequest();
        assertThat(recordedRequest.getPath()).startsWith("/api/inventory?skuCode=");
        assertThat(recordedRequest.getPath()).contains("iphone_13");
        assertThat(recordedRequest.getPath()).contains("iphone_13_red");
    }

    @Test
    void throwsAndPersistsNothingWhenAnyProductIsOutOfStock() throws Exception {
        enqueueInventoryResponse(
                new InventoryResponse("iphone_13", true),
                new InventoryResponse("iphone_13_red", false));

        assertThatThrownBy(() -> orderService.placeOrder(orderRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product is not in stock, please try again later");

        verifyNoInteractions(orderRepository, kafkaTemplate);
    }

    private void enqueueInventoryResponse(InventoryResponse... responses) throws Exception {
        inventoryService.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(OBJECT_MAPPER.writeValueAsString(responses)));
    }

    private static OrderRequest orderRequest() {
        return new OrderRequest(List.of(
                new OrderLineItemsDto(null, "iphone_13", new BigDecimal("1200"), 1),
                new OrderLineItemsDto(null, "iphone_13_red", new BigDecimal("1500"), 2)));
    }
}
