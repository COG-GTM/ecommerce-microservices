package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private OrderService orderService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderService = new OrderService(orderRepository, webClientBuilder, kafkaTemplate);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("http://inventory-service/api/inventory"), any(Function.class)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private void stubInventory(InventoryResponse... responses) {
        when(responseSpec.bodyToMono(InventoryResponse[].class)).thenReturn(Mono.just(responses));
    }

    private OrderRequest orderRequest(String... skuCodes) {
        List<OrderLineItemsDto> items = java.util.Arrays.stream(skuCodes)
                .map(sku -> new OrderLineItemsDto(null, sku, BigDecimal.TEN, 1))
                .toList();
        return new OrderRequest(items);
    }

    @Test
    void placeOrderSavesAndPublishesEventWhenAllItemsInStock() {
        stubInventory(new InventoryResponse("iphone_13", true), new InventoryResponse("pixel_8", true));

        String result = orderService.placeOrder(orderRequest("iphone_13", "pixel_8"));

        assertThat(result).isEqualTo("Order Placed Successfully!");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getOrderNumber()).isNotBlank();
        assertThat(saved.getOrderLineItemsList()).hasSize(2)
                .extracting("skuCode").containsExactly("iphone_13", "pixel_8");

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate).send(eq("notificationTopic"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderNumber()).isEqualTo(saved.getOrderNumber());
    }

    @Test
    void placeOrderThrowsAndDoesNotSaveOrPublishWhenAnyItemOutOfStock() {
        stubInventory(new InventoryResponse("iphone_13", true), new InventoryResponse("pixel_8", false));

        assertThatThrownBy(() -> orderService.placeOrder(orderRequest("iphone_13", "pixel_8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in stock");

        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(String.class), any(OrderPlacedEvent.class));
    }

    @Test
    void placeOrderMapsLineItemFieldsFromDto() {
        stubInventory(new InventoryResponse("iphone_13", true));
        OrderRequest request = new OrderRequest(
                List.of(new OrderLineItemsDto(null, "iphone_13", new BigDecimal("1200.00"), 3)));

        orderService.placeOrder(request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        var item = orderCaptor.getValue().getOrderLineItemsList().get(0);
        assertThat(item.getSkuCode()).isEqualTo("iphone_13");
        assertThat(item.getPrice()).isEqualByComparingTo("1200.00");
        assertThat(item.getQuantity()).isEqualTo(3);
    }
}
