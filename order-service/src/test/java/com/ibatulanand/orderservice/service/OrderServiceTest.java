package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.OutOfStockException;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Test
    void placeOrderReturnsSavedOrderNumberAndSendsEventWhenAllProductsAreInStock() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderService orderService = new OrderService(orderRepository, webClientBuilder(
                "[{\"skuCode\":\"sku_a\",\"inStock\":true}]"), kafkaTemplate);

        String orderNumber = orderService.placeOrder(orderRequest("sku_a"));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertFalse(orderNumber.isBlank());
        assertEquals(orderCaptor.getValue().getOrderNumber(), orderNumber);
        verify(kafkaTemplate).send(eq("notificationTopic"), any(OrderPlacedEvent.class));
    }

    @Test
    void placeOrderThrowsOutOfStockExceptionWithoutSavingOrSendingEvent() {
        OrderService orderService = new OrderService(orderRepository, webClientBuilder(
                "[{\"skuCode\":\"sku_a\",\"inStock\":true},{\"skuCode\":\"sku_b\",\"inStock\":false}]"),
                kafkaTemplate);

        OutOfStockException exception = assertThrows(OutOfStockException.class,
                () -> orderService.placeOrder(orderRequest("sku_a", "sku_b")));

        assertEquals(List.of("sku_b"), exception.getSkuCodes());
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }

    private static WebClient.Builder webClientBuilder(String responseBody) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(responseBody)
                .build()));
    }

    private static OrderRequest orderRequest(String... skuCodes) {
        List<OrderLineItemsDto> items = java.util.Arrays.stream(skuCodes)
                .map(skuCode -> new OrderLineItemsDto(null, skuCode, BigDecimal.ONE, 1))
                .toList();
        return new OrderRequest(items);
    }
}
