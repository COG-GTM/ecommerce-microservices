package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceDuplicateOrderTest {

    private static final String IN_STOCK = "[{\"skuCode\":\"iphone_13\",\"inStock\":true}]";

    private HttpServer inventoryStub;
    private final AtomicInteger inventoryCalls = new AtomicInteger();
    private volatile long inventoryDelayMillis;

    private final List<Order> savedOrders = new CopyOnWriteArrayList<>();
    private final Map<String, Order> ordersByKey = new ConcurrentHashMap<>();
    private final List<OrderPlacedEvent> sentEvents = new CopyOnWriteArrayList<>();

    private OrderRepository orderRepository;
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private OrderService orderService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        inventoryStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        inventoryStub.createContext("/api/inventory", exchange -> {
            inventoryCalls.incrementAndGet();
            try {
                Thread.sleep(inventoryDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = IN_STOCK.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception ignored) {
                // client gave up
            } finally {
                exchange.close();
            }
        });
        inventoryStub.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        inventoryStub.start();
        int port = inventoryStub.getAddress().getPort();

        // Stands in for the @LoadBalanced builder: resolve inventory-service to the local stub.
        WebClient.Builder builder = WebClient.builder().filter((request, next) -> next.exchange(
                ClientRequest.from(request)
                        .url(UriComponentsBuilder.fromUri(request.url())
                                .scheme("http").host("127.0.0.1").port(port).build(true).toUri())
                        .build()));

        orderRepository = mock(OrderRepository.class);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getIdempotencyKey() != null
                    && ordersByKey.putIfAbsent(order.getIdempotencyKey(), order) != null) {
                throw new DataIntegrityViolationException("Duplicate entry for key 'idempotency_key'");
            }
            savedOrders.add(order);
            return order;
        });
        when(orderRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(ordersByKey.get(invocation.<String>getArgument(0))));

        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("notificationTopic"), any(OrderPlacedEvent.class))).thenAnswer(invocation -> {
            sentEvents.add(invocation.getArgument(1));
            return null;
        });

        orderService = new OrderService(orderRepository,
                new InventoryClient(builder, Duration.ofMillis(500)), kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        inventoryStub.stop(0);
    }

    @Test
    void slowInventoryCreatesNoOrderEvenAfterItAnswers() throws Exception {
        inventoryDelayMillis = 4000;

        // The client (or a caller-side retry) tries three times; each attempt times out.
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest("1000"), "alice-order-0001"))
                    .isInstanceOf(RuntimeException.class);
        }

        // Wait until the stub has answered every attempt that reached it.
        Thread.sleep(4500);

        assertThat(inventoryCalls.get()).isEqualTo(3);
        assertThat(savedOrders).isEmpty();
        assertThat(sentEvents).isEmpty();
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingOrderWithoutSecondOrderOrEvent() {
        String first = orderService.placeOrder(orderRequest("1000"), "alice-order-0002");
        String second = orderService.placeOrder(orderRequest("1000.00"), "alice-order-0002");

        assertThat(first).isEqualTo("Order Placed Successfully!");
        assertThat(second).isEqualTo("Order Placed Successfully!");
        assertThat(savedOrders).hasSize(1);
        assertThat(sentEvents).hasSize(1);
        assertThat(inventoryCalls.get()).isEqualTo(1);
    }

    @Test
    void concurrentRequestLosingUniqueConstraintDoesNotPublishSecondEvent() {
        // The other request commits after our key lookup but before our insert.
        orderService.placeOrder(orderRequest("1000"), "alice-order-0003");
        sentEvents.clear();
        savedOrders.clear();
        when(orderRepository.findByIdempotencyKey("alice-order-0003"))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(ordersByKey.get("alice-order-0003")));

        String result = orderService.placeOrder(orderRequest("1000"), "alice-order-0003");

        assertThat(result).isEqualTo("Order Placed Successfully!");
        assertThat(savedOrders).isEmpty();
        assertThat(sentEvents).isEmpty();
    }

    @Test
    void reusedKeyWithDifferentOrderIsRejected() {
        orderService.placeOrder(orderRequest("1000"), "alice-order-0004");

        assertThatThrownBy(() -> orderService.placeOrder(orderRequest("1"), "alice-order-0004"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(savedOrders).hasSize(1);
        assertThat(sentEvents).hasSize(1);
    }

    @Test
    void malformedKeyIsRejectedBeforeInventoryCall() {
        assertThatThrownBy(() -> orderService.placeOrder(orderRequest("1000"), "x".repeat(65)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(inventoryCalls.get()).isZero();
        assertThat(savedOrders).isEmpty();
    }

    @Test
    void eventIsPublishedOnlyAfterOrderIsSaved() {
        orderService.placeOrder(orderRequest("1000"));

        InOrder inOrder = inOrder(orderRepository, kafkaTemplate);
        inOrder.verify(orderRepository).saveAndFlush(any(Order.class));
        inOrder.verify(kafkaTemplate).send(eq("notificationTopic"), any(OrderPlacedEvent.class));
        assertThat(savedOrders).hasSize(1);
        assertThat(sentEvents).extracting(OrderPlacedEvent::getOrderNumber)
                .containsExactly(savedOrders.get(0).getOrderNumber());
    }

    private static OrderRequest orderRequest(String price) {
        return new OrderRequest(List.of(new OrderLineItemsDto(null, "iphone_13", new BigDecimal(price), 1)));
    }
}
