package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.OrderRejectedException;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final String CATALOG =
            "[{\"id\":\"p1\",\"skuCode\":\"iphone_15\",\"name\":\"Iphone 15\",\"price\":1500.00}," +
            "{\"id\":\"p2\",\"skuCode\":\"iphone_15_pro\",\"name\":\"Iphone 15 Pro\",\"price\":2000.00}]";

    private final List<ClientRequest> requests = new ArrayList<>();
    private final Map<String, ClientResponse> responses = new java.util.HashMap<>();

    private OrderRepository orderRepository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            ClientResponse response = responses.get(request.url().getPath());
            return Mono.just(response != null ? response : ClientResponse.create(HttpStatus.NO_CONTENT).build());
        });
        orderService = new OrderService(orderRepository, builder, kafkaTemplate);
        responses.put("/api/product", json(HttpStatus.OK, CATALOG));
    }

    @Test
    void persistsCatalogPriceAndRequestedQuantity() {
        String result = orderService.placeOrder(request(line("iphone_15", 2), line("iphone_15_pro", 1)));

        assertEquals("Order Placed Successfully!", result);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        List<OrderLineItems> lines = saved.getValue().getOrderLineItemsList();
        assertEquals(2, lines.size());
        assertEquals(0, new BigDecimal("1500.00").compareTo(lines.get(0).getPrice()));
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(0, new BigDecimal("2000.00").compareTo(lines.get(1).getPrice()));
        verify(kafkaTemplate).send(anyString(), any(OrderPlacedEvent.class));
        assertEquals("/api/inventory/reservations", requests.get(1).url().getPath());
    }

    @Test
    void rejectsUnknownSkuWithoutReservingOrSaving() {
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> orderService.placeOrder(request(line("iphone_15", 1), line("nonexistent", 5))));

        assertTrue(e.getMessage().contains("nonexistent"));
        assertEquals(1, requests.size(), "only the catalog lookup should have been made");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsWhenCatalogReturnsNothing() {
        responses.put("/api/product", json(HttpStatus.OK, "[]"));

        assertThrows(OrderRejectedException.class,
                () -> orderService.placeOrder(request(line("iphone_15", 1))));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSkuWithNonPositiveOrAmbiguousCatalogPrice() {
        responses.put("/api/product", json(HttpStatus.OK,
                "[{\"skuCode\":\"free\",\"price\":0}," +
                "{\"skuCode\":\"dup\",\"price\":10},{\"skuCode\":\"dup\",\"price\":1}]"));

        assertThrows(OrderRejectedException.class, () -> orderService.placeOrder(request(line("free", 1))));
        assertThrows(OrderRejectedException.class, () -> orderService.placeOrder(request(line("dup", 1))));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWhenInventoryCannotReserve() {
        responses.put("/api/inventory/reservations", json(HttpStatus.CONFLICT,
                "{\"message\":\"Insufficient stock\",\"unavailableSkuCodes\":[\"iphone_15_pro\"]}"));

        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> orderService.placeOrder(request(line("iphone_15_pro", 1))));

        assertTrue(e.getMessage().contains("iphone_15_pro"));
        verify(orderRepository, never()).saveAndFlush(any());
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }

    @Test
    void reservesAggregatedQuantityForRepeatedSku() {
        orderService.placeOrder(request(line("iphone_15", 600), line("iphone_15", 300)));

        assertEquals("/api/inventory/reservations", requests.get(1).url().getPath());
        assertThrows(OrderRejectedException.class,
                () -> orderService.placeOrder(request(line("iphone_15", 600), line("iphone_15", 401))));
    }

    @Test
    void releasesReservationWhenOrderCannotBePersisted() {
        when(orderRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> orderService.placeOrder(request(line("iphone_15", 1))));

        assertEquals("/api/inventory/reservations/release", requests.get(requests.size() - 1).url().getPath());
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static OrderLineItemsDto line(String skuCode, int quantity) {
        return new OrderLineItemsDto(skuCode, quantity);
    }

    private static OrderRequest request(OrderLineItemsDto... lines) {
        return new OrderRequest(List.of(lines));
    }
}
