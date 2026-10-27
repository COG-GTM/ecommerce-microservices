package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    private final List<ClientRequest> inventoryRequests = new ArrayList<>();
    private HttpStatus inventoryStatus;
    private String inventoryBody;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        inventoryStatus = HttpStatus.OK;
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            inventoryRequests.add(request);
            return Mono.just(ClientResponse.create(inventoryStatus)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(inventoryBody)
                    .build());
        });
        orderService = new OrderService(orderRepository, builder, kafkaTemplate);
    }

    @Test
    void placesOrderPersistsLineItemsAndPublishesEventWhenAllSkusInStock() {
        inventoryBody = "[{\"skuCode\":\"iphone_15\",\"inStock\":true},{\"skuCode\":\"pixel_8\",\"inStock\":true}]";

        String result = orderService.placeOrder(request(
                line("iphone_15", "999.99", 2),
                line("pixel_8", "699.00", 1)));

        assertThat(result).isEqualTo("Order Placed Successfully!");
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        Order order = saved.getValue();
        assertThat(order.getOrderNumber()).isNotBlank();
        assertThat(order.getOrderLineItemsList())
                .extracting(OrderLineItems::getSkuCode, OrderLineItems::getQuantity, OrderLineItems::getPrice)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", 2, new BigDecimal("999.99")),
                        org.assertj.core.groups.Tuple.tuple("pixel_8", 1, new BigDecimal("699.00")));
        assertThat(order.getOrderLineItemsList()).extracting(OrderLineItems::getId).containsOnlyNulls();

        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("notificationTopic"), event.capture());
        assertThat(event.getValue().getOrderNumber()).isEqualTo(order.getOrderNumber());
    }

    @Test
    void queriesInventoryServiceThroughLoadBalancedNameWithEverySku() {
        inventoryBody = "[{\"skuCode\":\"a\",\"inStock\":true},{\"skuCode\":\"b\",\"inStock\":true}]";

        orderService.placeOrder(request(line("a", "1.00", 1), line("b", "2.00", 1)));

        assertThat(inventoryRequests).hasSize(1);
        assertThat(inventoryRequests.get(0).url().toString())
                .isEqualTo("http://inventory-service/api/inventory?skuCode=a&skuCode=b");
    }

    @Test
    void rejectsOrderAndRollsBackSideEffectsWhenAnySkuIsOutOfStock() {
        inventoryBody = "[{\"skuCode\":\"iphone_15\",\"inStock\":true},{\"skuCode\":\"iphone_15_pro\",\"inStock\":false}]";

        assertThatThrownBy(() -> orderService.placeOrder(request(
                line("iphone_15", "999.99", 1),
                line("iphone_15_pro", "1199.00", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product is not in stock, please try again later");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void propagatesInventoryServiceErrorsWithoutPersistingOrPublishing() {
        inventoryStatus = HttpStatus.SERVICE_UNAVAILABLE;
        inventoryBody = "{}";

        assertThatThrownBy(() -> orderService.placeOrder(request(line("iphone_15", "999.99", 1))))
                .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);

        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void unknownSkuIsNotRejectedBecauseInventoryOmitsIt_currentBehaviour() {
        // Documents a known gap (see COVERAGE.md findings): inventory-service returns no entry for an
        // unknown SKU and allMatch() over an empty array is true, so the order is accepted.
        inventoryBody = "[]";

        assertThat(orderService.placeOrder(request(line("does_not_exist", "1.00", 1))))
                .isEqualTo("Order Placed Successfully!");
        verify(orderRepository).save(any());
    }

    private static OrderRequest request(OrderLineItemsDto... lines) {
        return new OrderRequest(List.of(lines));
    }

    private static OrderLineItemsDto line(String sku, String price, int quantity) {
        return new OrderLineItemsDto(99L, sku, new BigDecimal(price), quantity);
    }
}
