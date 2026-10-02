package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.dto.QuoteResponse;
import com.ibatulanand.orderservice.service.OrderService;
import com.ibatulanand.orderservice.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PricingService pricingService;

    @PostMapping("/quote")
    public QuoteResponse quote(@RequestBody OrderRequest orderRequest,
                               @RequestHeader(value = "X-Store-Id", required = false) String storeIdHeader) {
        return pricingService.quote(orderRequest, storeIdHeader);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@RequestBody OrderRequest orderRequest,
                                    @RequestHeader(value = "X-Store-Id", required = false) String storeIdHeader,
                                    @RequestHeader(value = "X-Register-Id", required = false) String registerIdHeader,
                                    @RequestHeader(value = "X-Associate-Id", required = false) String associateIdHeader,
                                    @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader) {
        return orderService.placeOrder(orderRequest, storeIdHeader, registerIdHeader, associateIdHeader, userRolesHeader);
    }
}
