package com.ibatulanand.inventoryservice.controller;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
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

    // http://localhost:8082/api/inventory?skuCode=268341-016-L&skuCode=471902-004-29
    @GetMapping(params = "!storeId")
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestParam List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    // http://localhost:8082/api/inventory?skuCode=268341-016-L&storeId=1969
    @GetMapping(params = "storeId")
    @ResponseStatus(HttpStatus.OK)
    public StoreInventoryResponse getStoreInventory(@RequestParam List<String> skuCode, @RequestParam String storeId) {
        if (skuCode.size() != 1) {
            throw new IllegalArgumentException("Exactly one skuCode is required when storeId is given");
        }
        return inventoryService.getStoreInventory(skuCode.get(0), storeId);
    }
}
