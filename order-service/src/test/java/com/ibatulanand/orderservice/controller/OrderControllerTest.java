package com.ibatulanand.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private OrderService orderService;

    @Test
    void shouldReturnCreatedOrderWithLocationHeader() throws Exception {
        OrderResponse orderResponse = orderResponse("order-123");
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(orderResponse);

        MvcResult mvcResult = mockMvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/order/order-123"))
                .andExpect(jsonPath("$.orderNumber").value("order-123"))
                .andExpect(jsonPath("$.orderLineItemsList[0].skuCode").value("iphone_15"));
    }

    @Test
    void shouldReturnOrderByOrderNumber() throws Exception {
        when(orderService.getOrder("order-123")).thenReturn(Optional.of(orderResponse("order-123")));

        mockMvc.perform(get("/api/order/order-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("order-123"));
    }

    @Test
    void shouldReturnNotFoundForUnknownOrderNumber() throws Exception {
        when(orderService.getOrder("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/order/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListOrders() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(orderResponse("order-123")));

        mockMvc.perform(get("/api/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("order-123"));
    }

    private OrderRequest orderRequest() {
        return new OrderRequest(List.of(lineItem()));
    }

    private OrderLineItemsDto lineItem() {
        return new OrderLineItemsDto(1L, "iphone_15", BigDecimal.valueOf(1500), 1);
    }

    private OrderResponse orderResponse(String orderNumber) {
        return OrderResponse.builder()
                .id(1L)
                .orderNumber(orderNumber)
                .orderLineItemsList(List.of(lineItem()))
                .build();
    }
}
