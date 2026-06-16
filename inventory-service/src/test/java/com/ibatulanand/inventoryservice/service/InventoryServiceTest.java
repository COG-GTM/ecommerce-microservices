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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory(String skuCode, int quantity) {
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        return inventory;
    }

    @Test
    void isInStock_withItemsInStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku1")))
                .thenReturn(List.of(inventory("sku1", 10)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku1"));

        assertEquals(1, responses.size());
        assertEquals("sku1", responses.get(0).getSkuCode());
        assertTrue(responses.get(0).isInStock());
    }

    @Test
    void isInStock_withItemsOutOfStock() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku1")))
                .thenReturn(List.of(inventory("sku1", 0)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku1"));

        assertEquals(1, responses.size());
        assertFalse(responses.get(0).isInStock());
    }

    @Test
    void isInStock_withMultipleSkuCodes() {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku1", "sku2")))
                .thenReturn(List.of(inventory("sku1", 5), inventory("sku2", 0)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("sku1", "sku2"));

        assertEquals(2, responses.size());
        assertTrue(responses.get(0).isInStock());
        assertFalse(responses.get(1).isInStock());
    }

    @Test
    void isInStock_withNoMatchingSkuCodes() {
        when(inventoryRepository.findBySkuCodeIn(List.of("unknown")))
                .thenReturn(List.of());

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("unknown"));

        assertTrue(responses.isEmpty());
    }
}
