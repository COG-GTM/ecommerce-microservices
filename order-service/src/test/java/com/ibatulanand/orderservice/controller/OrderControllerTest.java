package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private static final String CATALOG =
            "[{\"id\":\"p1\",\"skuCode\":\"iphone_15\",\"name\":\"Iphone 15\",\"price\":1500.00}]";

    private OrderRepository orderRepository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                "/api/product".equals(request.url().getPath())
                        ? ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body(CATALOG)
                                .build()
                        : ClientResponse.create(HttpStatus.NO_CONTENT).build()));
        OrderService orderService = new OrderService(orderRepository, webClient, kafkaTemplate);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void rejectsTamperedPriceWithNegativeQuantityAndSavesNothing() throws Exception {
        mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("{\"skuCode\":\"iphone_15\",\"price\":0.01,\"quantity\":-5}")))
                .andExpect(status().isBadRequest());

        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsZeroMissingOrExcessiveQuantityAndBlankSku() throws Exception {
        for (String line : List.of(
                "{\"skuCode\":\"iphone_15\",\"quantity\":0}",
                "{\"skuCode\":\"iphone_15\",\"price\":0.01}",
                "{\"skuCode\":\"iphone_15\",\"quantity\":1000000}",
                "{\"skuCode\":\" \",\"quantity\":1}")) {
            mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(order(line)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderLineItemsDtoList\":[]}"))
                .andExpect(status().isBadRequest());

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void ignoresClientPriceAndStoresCatalogPrice() throws Exception {
        MvcResult async = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("{\"skuCode\":\"iphone_15\",\"price\":0.01,\"quantity\":2}")))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(async)).andExpect(status().isCreated());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        OrderLineItems line = saved.getValue().getOrderLineItemsList().get(0);
        assertEquals(0, new BigDecimal("1500.00").compareTo(line.getPrice()));
        assertEquals(2, line.getQuantity());
    }

    private static String order(String line) {
        return "{\"orderLineItemsDtoList\":[" + line + "]}";
    }
}
