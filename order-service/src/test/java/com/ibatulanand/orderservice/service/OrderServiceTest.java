package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final List<ClientRequest> inventoryRequests = new ArrayList<>();
    private HttpStatus reservationStatus = HttpStatus.NO_CONTENT;

    private OrderRepository orderRepository;
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private OrderService orderService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            inventoryRequests.add(request);
            HttpStatus status = request.url().getPath().endsWith("/release") ? HttpStatus.NO_CONTENT : reservationStatus;
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status.value())).build());
        });
        orderService = new OrderService(orderRepository, builder, kafkaTemplate);
    }

    @Test
    void placesOrderAfterReservingStock() {
        String result = orderService.placeOrder(order(line("iphone_15", 2), line("iphone_15", 3)));

        assertThat(result).isEqualTo("Order Placed Successfully!");
        assertThat(inventoryRequests).singleElement().satisfies(request -> {
            assertThat(request.method().name()).isEqualTo("POST");
            assertThat(request.url().toString()).isEqualTo("http://inventory-service/api/inventory/reservations");
        });
        verify(orderRepository).saveAndFlush(any(Order.class));
        verify(kafkaTemplate).send(eq("notificationTopic"), any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsUnknownSkuWhenInventoryCannotReserveIt() {
        reservationStatus = HttpStatus.CONFLICT;

        assertRejected(order(line("ghost-item", 1)), HttpStatus.CONFLICT);
        assertThat(inventoryRequests).hasSize(1);
    }

    @Test
    void rejectsQuantityAboveStock() {
        reservationStatus = HttpStatus.CONFLICT;

        assertRejected(order(line("iphone_15", 1000)), HttpStatus.CONFLICT);
    }

    @Test
    void rejectsEmptyOrNullItemListWithoutCallingInventory() {
        assertRejected(order(), HttpStatus.BAD_REQUEST);
        assertRejected(new OrderRequest(null), HttpStatus.BAD_REQUEST);
        assertRejected(null, HttpStatus.BAD_REQUEST);
        assertThat(inventoryRequests).isEmpty();
    }

    @Test
    void rejectsInvalidQuantitiesAndSkusWithoutCallingInventory() {
        assertRejected(order(line("iphone_15", 0)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line("iphone_15", -1)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line("iphone_15", null)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line("iphone_15", 1000000)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line("iphone_15", 600), line("iphone_15", 600)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line(null, 1)), HttpStatus.BAD_REQUEST);
        assertRejected(order(line("", 1)), HttpStatus.BAD_REQUEST);
        assertThat(inventoryRequests).isEmpty();
    }

    @Test
    void releasesReservationWhenOrderCannotBeSaved() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> orderService.placeOrder(order(line("iphone_15", 1))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(inventoryRequests).extracting(request -> request.url().getPath())
                .containsExactly("/api/inventory/reservations", "/api/inventory/reservations/release");
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void parallelOrdersForTheLastUnitSaveExactlyOneOrder() throws Exception {
        AtomicInteger stock = new AtomicInteger(1);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            boolean reserved = stock.getAndUpdate(left -> left > 0 ? left - 1 : left) > 0;
            HttpStatus status = reserved ? HttpStatus.NO_CONTENT : HttpStatus.CONFLICT;
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status.value())).build());
        });
        OrderService service = new OrderService(orderRepository, builder, kafkaTemplate);

        Thread[] threads = new Thread[2];
        AtomicInteger placed = new AtomicInteger();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    service.placeOrder(order(line("last_unit", 1)));
                    placed.incrementAndGet();
                } catch (ResponseStatusException ignored) {
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(placed).hasValue(1);
        verify(orderRepository).saveAndFlush(any(Order.class));
    }

    private void assertRejected(OrderRequest request, HttpStatus status) {
        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(status.value());
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(kafkaTemplate, never()).send(any(), any());
    }

    private static OrderRequest order(OrderLineItemsDto... lines) {
        return new OrderRequest(new ArrayList<>(Arrays.asList(lines)));
    }

    private static OrderLineItemsDto line(String skuCode, Integer quantity) {
        return new OrderLineItemsDto(null, skuCode, BigDecimal.TEN, quantity);
    }
}
