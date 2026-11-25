package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MBA-3940: a till's order list pages by id so every order can be reconciled, not just the latest 200. */
class OrderControllerTillPagingTest {

    OrderService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(OrderService.class);
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, Runnable::run)).build();
    }

    @Test
    void fullPageCarriesTheCursorForTheNextPage() throws Exception {
        List<Order> first = orders(1, 3);
        List<Order> second = orders(4, 5);
        when(service.findByTillId("till-1", 0, 3)).thenReturn(first);
        when(service.findByTillId("till-1", 3, 3)).thenReturn(second);

        mvc.perform(get("/api/order").param("tillId", "till-1").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(header().string("X-Next-After-Id", "3"));
        mvc.perform(get("/api/order").param("tillId", "till-1").param("afterId", "3").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(header().doesNotExist("X-Next-After-Id"));
    }

    @Test
    void defaultsTo200AndCapsThePageAt500() throws Exception {
        when(service.findByTillId(eq("till-1"), anyLong(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/order").param("tillId", "till-1")).andExpect(status().isOk());
        verify(service).findByTillId("till-1", 0, 200);
        mvc.perform(get("/api/order").param("tillId", "till-1").param("limit", "100000")).andExpect(status().isOk());
        verify(service).findByTillId("till-1", 0, 500);
    }

    private static List<Order> orders(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId).mapToObj(id -> {
            Order order = mock(Order.class);
            when(order.getId()).thenReturn(id);
            when(order.getOrderNumber()).thenReturn("ORDER-" + id);
            return order;
        }).toList();
    }
}
