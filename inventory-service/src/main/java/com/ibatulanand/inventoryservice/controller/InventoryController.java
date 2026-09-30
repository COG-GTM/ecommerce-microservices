package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.StockReservationError;
import com.ibatulanand.inventoryservice.dto.StockReservationRequest;
import com.ibatulanand.inventoryservice.exception.InsufficientStockException;
import com.ibatulanand.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // http://localhost:8082/api/inventory?skuCode=iphone_15&skuCode=iphone_15_pro
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestParam List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@RequestBody StockReservationRequest request) {
        inventoryService.reserve(request.getItems());
    }

    @PostMapping("/reservations/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@RequestBody StockReservationRequest request) {
        inventoryService.release(request.getItems());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public StockReservationError handleInsufficientStock(InsufficientStockException e) {
        return new StockReservationError(e.getMessage(), e.getUnavailableSkuCodes());
    }

    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public StockReservationError handleInvalidRequest(RuntimeException e) {
        return new StockReservationError(e.getMessage(), List.of());
    }
}
