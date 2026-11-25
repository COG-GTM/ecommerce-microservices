package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.function.Function;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** MBA-3938: a repeated till sale with the same idempotency key returns the existing order. */
class OrderServiceIdempotencyTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderPlacedEvent> kafka = mock(KafkaTemplate.class);
    private final OrderRepository repository = mock(OrderRepository.class);
    private final WebClient.Builder webClient = mock(WebClient.Builder.class);
    private final OrderService service = new OrderService(repository, webClient, kafka);

    @Test
    void repeatedSaleReturnsTheExistingOrderWithoutSavingOrPublishing() {
        Order existing = new Order();
        existing.setOrderNumber("ORDER-1");
        existing.setIdempotencyKey("basket-1");
        existing.setOrderLineItemsList(List.of());
        when(repository.findFirstByIdempotencyKey("basket-1")).thenReturn(Optional.of(existing));

        Order order = service.placeAndReturnOrder(OrderRequest.builder().idempotencyKey("basket-1")
                .orderLineItemsDtoList(List.of()).build());

        assertThat(order.getOrderNumber()).isEqualTo("ORDER-1");
        verify(repository, never()).save(any());
        verify(kafka, never()).send(anyString(), any());
        verifyNoInteractions(webClient);
    }

    @Test
    void aNewSaleIsSavedWithItsIdempotencyKey() {
        WebClient.Builder inventory = mock(WebClient.Builder.class, Mockito.RETURNS_DEEP_STUBS);
        when(inventory.build().get().uri(anyString(), any(Function.class)).retrieve()
                .bodyToMono(InventoryResponse[].class))
                .thenReturn(Mono.just(new InventoryResponse[]{new InventoryResponse("milk", true)}));
        when(repository.findFirstByIdempotencyKey("basket-2")).thenReturn(Optional.empty());

        new OrderService(repository, inventory, kafka).placeAndReturnOrder(OrderRequest.builder()
                .idempotencyKey("basket-2").channel("TILL")
                .orderLineItemsDtoList(List.of(new OrderLineItemsDto(null, "milk", BigDecimal.ONE, 1))).build());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("basket-2");
    }
}
