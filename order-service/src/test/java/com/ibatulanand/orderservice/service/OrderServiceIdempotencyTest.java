package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.function.Function;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MBA-3940: a replayed offline till sale (same idempotency key) is stored and published exactly once. */
class OrderServiceIdempotencyTest {

    OrderRepository repository;
    WebClient.Builder webClientBuilder;
    KafkaTemplate<String, OrderPlacedEvent> kafka;
    OrderService service;
    List<Order> stored;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OrderRepository.class);
        webClientBuilder = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);
        kafka = mock(KafkaTemplate.class);
        service = new OrderService(repository, webClientBuilder, kafka);
        stored = new ArrayList<>();
        when(webClientBuilder.build().get().uri(anyString(), any(java.util.function.Function.class)).retrieve()
                .bodyToMono(InventoryResponse[].class).block())
                .thenReturn(new InventoryResponse[]{new InventoryResponse("MOR-MILK-SS-4PT", true)});
        when(repository.save(any(Order.class))).thenAnswer(inv -> {
            stored.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.findFirstByIdempotencyKey(anyString())).thenAnswer(inv -> stored.stream()
                .filter(o -> inv.getArgument(0).equals(o.getIdempotencyKey())).findFirst());
    }

    @Test
    void replayWithSameKeyReturnsTheStoredOrderWithoutPublishingAgain() {
        Order first = service.placeAndReturnOrder(sale("offline-sale-1"));
        Order replay = service.placeAndReturnOrder(sale("offline-sale-1"));

        assertThat(replay.getOrderNumber()).isEqualTo(first.getOrderNumber());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getIdempotencyKey()).isEqualTo("offline-sale-1");
        verify(kafka, times(1)).send(eq("notificationTopic"), any(OrderPlacedEvent.class));
    }

    @Test
    void differentKeysAreDifferentSales() {
        Order a = service.placeAndReturnOrder(sale("offline-sale-a"));
        Order b = service.placeAndReturnOrder(sale("offline-sale-b"));

        assertThat(a.getOrderNumber()).isNotEqualTo(b.getOrderNumber());
        assertThat(stored).hasSize(2);
    }

    @Test
    void salesWithoutAKeyAreNeverDeduplicated() {
        service.placeAndReturnOrder(sale(null));
        service.placeAndReturnOrder(sale(" "));

        assertThat(stored).hasSize(2);
        assertThat(stored).allMatch(o -> o.getIdempotencyKey() == null);
        verify(repository, never()).findFirstByIdempotencyKey(any());
    }

    private static OrderRequest sale(String idempotencyKey) {
        OrderLineItemsDto line = new OrderLineItemsDto();
        line.setSkuCode("MOR-MILK-SS-4PT");
        line.setPrice(new BigDecimal("1.55"));
        line.setQuantity(1);
        OrderRequest request = new OrderRequest();
        request.setOrderLineItemsDtoList(List.of(line));
        request.setChannel("TILL");
        request.setStoreNumber("0417");
        request.setTillId("till-1");
        request.setTransactionId("0417-till-1-OFF1");
        request.setTenderType("CASH");
        request.setTotalPence(155);
        request.setIdempotencyKey(idempotencyKey);
        return request;
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
                .orderLineItemsDtoList(List.of(new OrderLineItemsDto(null, "milk", BigDecimal.ONE, 1, null, null))).build());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("basket-2");
    }
}
