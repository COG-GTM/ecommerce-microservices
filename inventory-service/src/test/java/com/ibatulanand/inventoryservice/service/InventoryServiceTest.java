package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void returnsOneResultPerRequestedSkuInRequestOrderIncludingUnknownSkus() {
        when(inventoryRepository.findBySkuCodeIn(List.of("known", "unknown")))
                .thenReturn(List.of(inventory("known", 3)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("known", "unknown"));

        assertEquals(2, responses.size());
        assertEquals(List.of("known", "unknown"), responses.stream().map(InventoryResponse::getSkuCode).toList());
        assertTrue(responses.get(0).isInStock());
        assertFalse(responses.get(1).isInStock());
    }

    @Test
    void requestedQuantityEqualToAvailableQuantityIsInStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku"))).thenReturn(List.of(inventory("sku", 5)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku"), List.of(5));

        assertTrue(responses.get(0).isInStock());
    }

    @Test
    void requestedQuantityGreaterThanAvailableQuantityIsOutOfStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku"))).thenReturn(List.of(inventory("sku", 1)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku"), List.of(5));

        assertFalse(responses.get(0).isInStock());
    }

    @Test
    void absentQuantityDefaultsToOneForBothOverloads() {
        List<String> skuCodes = List.of("one", "zero");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(List.of(inventory("one", 1), inventory("zero", 0)));

        List<InventoryResponse> responsesWithoutQuantity = inventoryService.isInStock(skuCodes);
        List<InventoryResponse> responsesWithNullQuantity = inventoryService.isInStock(skuCodes, null);

        assertTrue(responsesWithoutQuantity.get(0).isInStock());
        assertFalse(responsesWithoutQuantity.get(1).isInStock());
        assertTrue(responsesWithNullQuantity.get(0).isInStock());
        assertFalse(responsesWithNullQuantity.get(1).isInStock());
    }

    @Test
    void nullQuantityEntryDefaultsToOne() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku"))).thenReturn(List.of(inventory("sku", 1)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku"), java.util.Arrays.asList((Integer) null));

        assertTrue(responses.get(0).isInStock());
    }

    @Test
    void sumsDuplicateRowsForTheSameSku() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku")))
                .thenReturn(List.of(inventory("sku", 2), inventory("sku", 3)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku"), List.of(5));

        assertTrue(responses.get(0).isInStock());
    }

    @Test
    void mismatchedQuantityListSizeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.isInStock(List.of("sku"), List.of(1, 2)));
    }

    @Test
    void quantityBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.isInStock(List.of("sku"), List.of(0)));
    }

    private Inventory inventory(String skuCode, Integer quantity) {
        return new Inventory(null, skuCode, quantity);
    }
}
