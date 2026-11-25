package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.config.SecurityConfig;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    private static final String ORDER_JSON =
            "{\"orderLineItemsDtoList\":[{\"skuCode\":\"iphone_15\",\"price\":1200,\"quantity\":1}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void placeOrderWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));

        verify(orderService, never()).placeOrder(any(), anyString());
    }

    @Test
    void placeOrderWithInvalidTokenIsRejected() throws Exception {
        when(jwtDecoder.decode("forged")).thenThrow(new BadJwtException("bad signature"));

        mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).placeOrder(any(), anyString());
    }

    @Test
    void placeOrderWithValidTokenRelaysTokenToService() throws Exception {
        when(orderService.placeOrder(any(OrderRequest.class), eq("caller-token")))
                .thenReturn("Order Placed Successfully!");

        MvcResult result = mockMvc.perform(post("/api/order")
                        .with(jwt().jwt(token -> token.tokenValue("caller-token")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isCreated())
                .andExpect(content().string("Order Placed Successfully!"));

        verify(orderService).placeOrder(any(OrderRequest.class), eq("caller-token"));
    }

    @Test
    void sensitiveActuatorEndpointsRequireToken() throws Exception {
        for (String path : new String[]{"/actuator/heapdump", "/actuator/env", "/actuator/loggers", "/actuator"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
