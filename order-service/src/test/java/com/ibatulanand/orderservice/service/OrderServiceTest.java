package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final WebClient.Builder webClientBuilder = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final OrderService orderService = new OrderService(orderRepository, webClientBuilder, kafkaTemplate);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void inStock() {
        when(webClientBuilder.build().get().uri(anyString(), any(Function.class)).retrieve()
                .bodyToMono(InventoryResponse[].class).block())
                .thenReturn(new InventoryResponse[]{new InventoryResponse("MOR-LAGER-4X440", true)});
    }

    private static OrderRequest lagerOrder() {
        OrderLineItemsDto line = new OrderLineItemsDto();
        line.setSkuCode("MOR-LAGER-4X440");
        line.setPrice(new BigDecimal("4.50"));
        line.setQuantity(1);
        line.setMinimumAge(18);
        line.setAgeApprovedBy("OP-FORGED");
        OrderRequest request = new OrderRequest();
        request.setOrderLineItemsDtoList(List.of(line));
        return request;
    }

    private OrderLineItems savedLine() {
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        return saved.getValue().getOrderLineItemsList().get(0);
    }

    @Test
    void standardOrderDropsCallerSuppliedAgeApproval() {
        orderService.placeOrder(lagerOrder());
        assertThat(savedLine().getMinimumAge()).isNull();
        assertThat(savedLine().getAgeApprovedBy()).isNull();
    }

    @Test
    void tillSaleKeepsAgeApproval() {
        orderService.placeAndReturnOrder(lagerOrder());
        assertThat(savedLine().getMinimumAge()).isEqualTo(18);
        assertThat(savedLine().getAgeApprovedBy()).isEqualTo("OP-FORGED");
    }
}
