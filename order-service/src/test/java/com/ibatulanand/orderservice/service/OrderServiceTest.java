package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.TestHelper;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"rawtypes", "unchecked"})
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private void stubInventory(InventoryResponse... inventoryResponses) {
        when(responseSpec.bodyToMono(InventoryResponse[].class))
                .thenReturn(Mono.just(inventoryResponses));
    }

    @Test
    void should_place_order_when_all_products_in_stock() {
        stubInventory(TestHelper.inventoryResponseFixture("iphone_15", true));
        OrderRequest orderRequest = TestHelper.orderRequestFixture("iphone_15");

        String result = orderService.placeOrder(orderRequest);

        assertEquals("Order Placed Successfully!", result);
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertNotNull(saved.getOrderNumber());
        assertEquals(1, saved.getOrderLineItemsList().size());
        assertEquals("iphone_15", saved.getOrderLineItemsList().get(0).getSkuCode());
    }

    @Test
    void should_publish_order_placed_event_when_order_is_placed() {
        stubInventory(TestHelper.inventoryResponseFixture("iphone_15", true));

        orderService.placeOrder(TestHelper.orderRequestFixture("iphone_15"));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate).send(eq("notificationTopic"), captor.capture());
        assertNotNull(captor.getValue().getOrderNumber());
    }

    @Test
    void should_reject_order_when_any_product_is_out_of_stock() {
        stubInventory(
                TestHelper.inventoryResponseFixture("iphone_15", true),
                TestHelper.inventoryResponseFixture("iphone_15_pro", false));
        OrderRequest orderRequest = TestHelper.orderRequestFixture("iphone_15", "iphone_15_pro");

        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(orderRequest));

        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }
}
