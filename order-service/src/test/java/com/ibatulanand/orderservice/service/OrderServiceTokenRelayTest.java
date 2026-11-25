package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderServiceTokenRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void inventoryCallCarriesCallerAccessToken() {
        AtomicReference<ClientRequest> inventoryRequest = new AtomicReference<>();
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(request -> {
            inventoryRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("[{\"skuCode\":\"iphone_15\",\"inStock\":true}]")
                    .build());
        });
        OrderService orderService = new OrderService(mock(OrderRepository.class), webClientBuilder,
                (KafkaTemplate<String, OrderPlacedEvent>) mock(KafkaTemplate.class));

        String result = orderService.placeOrder(
                new OrderRequest(List.of(new OrderLineItemsDto(null, "iphone_15", BigDecimal.TEN, 1))),
                "caller-token");

        assertThat(result).isEqualTo("Order Placed Successfully!");
        assertThat(inventoryRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer caller-token");
    }
}
