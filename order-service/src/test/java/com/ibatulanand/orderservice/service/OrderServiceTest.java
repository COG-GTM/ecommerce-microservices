package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private WebClient.Builder webClientBuilder;

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderPlacedEvent> eventCaptor;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, webClientBuilder, kafkaTemplate);
    }

    @Test
    void should_save_order_and_publish_event_when_all_items_in_stock() {
        stubInventory(inStock("iphone_13"), inStock("iphone_13_red"));

        String result = orderService.placeOrder(TestFixtures.orderRequest("iphone_13", "iphone_13_red"));

        assertThat(result).isEqualTo("Order Placed Successfully!");
        verify(orderRepository).save(orderCaptor.capture());
        verify(kafkaTemplate).send(eq("notificationTopic"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderNumber())
                .isEqualTo(orderCaptor.getValue().getOrderNumber());
    }

    @Test
    void should_reject_order_when_item_out_of_stock() {
        stubInventory(inStock("iphone_13"), outOfStock("iphone_13_red"));

        assertThatThrownBy(() -> orderService.placeOrder(TestFixtures.orderRequest("iphone_13", "iphone_13_red")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product is not in stock, please try again later");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void should_preserve_line_item_fields_when_mapping_dto_to_entity() {
        stubInventory(inStock("iphone_13"));
        OrderLineItemsDto dto = TestFixtures.orderLineItemsDto("iphone_13", 4);
        OrderRequest orderRequest = new OrderRequest(List.of(dto));

        orderService.placeOrder(orderRequest);

        verify(orderRepository).save(orderCaptor.capture());
        List<OrderLineItems> lineItems = orderCaptor.getValue().getOrderLineItemsList();
        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0).getSkuCode()).isEqualTo(dto.getSkuCode());
        assertThat(lineItems.get(0).getPrice()).isEqualTo(dto.getPrice());
        assertThat(lineItems.get(0).getQuantity()).isEqualTo(dto.getQuantity());
    }

    @Test
    void should_generate_order_number_as_uuid_when_order_placed() {
        stubInventory(inStock("iphone_13"));

        orderService.placeOrder(TestFixtures.orderRequest("iphone_13"));

        verify(orderRepository).save(orderCaptor.capture());
        String orderNumber = orderCaptor.getValue().getOrderNumber();
        assertThat(UUID.fromString(orderNumber)).hasToString(orderNumber);
    }

    @SuppressWarnings("unchecked")
    private void stubInventory(InventoryResponse... responses) {
        when(webClientBuilder.build().get()
                .uri(anyString(), any(Function.class))
                .retrieve()
                .bodyToMono(InventoryResponse[].class))
                .thenReturn(Mono.just(responses));
    }

    private static InventoryResponse inStock(String skuCode) {
        return new InventoryResponse(skuCode, true);
    }

    private static InventoryResponse outOfStock(String skuCode) {
        return new InventoryResponse(skuCode, false);
    }
}
