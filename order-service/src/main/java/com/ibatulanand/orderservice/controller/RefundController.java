package com.ibatulanand.orderservice.controller;

import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundRequest;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundableSale;
import com.ibatulanand.orderservice.service.RefundException;
import com.ibatulanand.orderservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /** {reference}: order number, receipt number or transaction number of a completed sale. */
    @GetMapping("/refundable/{reference}")
    public RefundableSale refundable(@PathVariable String reference) {
        return refundService.refundable(reference);
    }

    @PostMapping("/{reference}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse refund(@PathVariable String reference, @RequestBody RefundRequest request) {
        return OrderResponse.from(refundService.refund(reference, request));
    }

    @ExceptionHandler(RefundException.class)
    public ResponseEntity<Map<String, String>> rejected(RefundException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCode(), "message", e.getMessage()));
    }
}
