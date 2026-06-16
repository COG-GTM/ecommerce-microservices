package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
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

    @InjectMocks
    private OrderService orderService;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubInventory(InventoryResponse... responses) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(uriSpec);
        // Invoke the URI-building lambda so it is actually exercised
        when(uriSpec.uri(anyString(), any(Function.class))).thenAnswer(invocation -> {
            Function<UriBuilder, ?> uriFunction = invocation.getArgument(1);
            uriFunction.apply(mock(UriBuilder.class, RETURNS_DEEP_STUBS));
            return headersSpec;
        });
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(InventoryResponse[].class)).thenReturn(Mono.just(responses));
    }

    private OrderRequest sampleRequest() {
        OrderLineItemsDto dto = new OrderLineItemsDto(1L, "sku1", BigDecimal.valueOf(120), 2);
        OrderRequest request = new OrderRequest();
        request.setOrderLineItemsDtoList(List.of(dto));
        return request;
    }

    @Test
    void placeOrder_allInStock_success() {
        stubInventory(new InventoryResponse("sku1", true));

        String result = orderService.placeOrder(sampleRequest());

        assertEquals("Order Placed Successfully!", result);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        verify(kafkaTemplate).send(eq("notificationTopic"), any(OrderPlacedEvent.class));

        // Verifies mapToDto mapping of the line item fields
        Order savedOrder = orderCaptor.getValue();
        OrderLineItems savedLineItem = savedOrder.getOrderLineItemsList().get(0);
        assertEquals("sku1", savedLineItem.getSkuCode());
        assertEquals(BigDecimal.valueOf(120), savedLineItem.getPrice());
        assertEquals(2, savedLineItem.getQuantity());
    }

    @Test
    void placeOrder_notInStock_throwsException() {
        stubInventory(new InventoryResponse("sku1", false));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(sampleRequest()));
        assertEquals("Product is not in stock, please try again later", exception.getMessage());

        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }
}
