package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
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

    @Test
    void shouldReturnOnHandQuantityForStore() {
        Inventory inventory = Inventory.builder()
                .skuCode("268341-016-L")
                .storeId("1042")
                .onHand(23)
                .shipFromStoreEligible(true)
                .build();
        when(inventoryRepository.findBySkuCodeInAndStoreId(List.of("268341-016-L"), "1042"))
                .thenReturn(List.of(inventory));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("268341-016-L"), "1042");

        assertEquals(1, responses.size());
        InventoryResponse response = responses.get(0);
        assertEquals("268341-016-L", response.getSkuCode());
        assertEquals("1042", response.getStoreId());
        assertEquals(23, response.getOnHand());
        assertTrue(response.isInStock());
        assertTrue(response.isShipFromStoreEligible());
    }

    @Test
    void shouldReportOutOfStockWhenNothingOnHand() {
        Inventory inventory = Inventory.builder()
                .skuCode("268341-016-M")
                .storeId("1042")
                .onHand(0)
                .shipFromStoreEligible(false)
                .build();
        when(inventoryRepository.findBySkuCodeInAndStoreId(List.of("268341-016-M"), "1042"))
                .thenReturn(List.of(inventory));

        InventoryResponse response = inventoryService.isInStock(List.of("268341-016-M"), "1042").get(0);

        assertEquals(0, response.getOnHand());
        assertFalse(response.isInStock());
        assertFalse(response.isShipFromStoreEligible());
    }

    @Test
    void shouldQueryAcrossStoresWhenStoreIdIsNotGiven() {
        Inventory storeInventory = Inventory.builder()
                .skuCode("268341-016-L")
                .storeId("1042")
                .onHand(23)
                .build();
        Inventory nearbyInventory = Inventory.builder()
                .skuCode("268341-016-L")
                .storeId("1188")
                .onHand(4)
                .build();
        when(inventoryRepository.findBySkuCodeIn(List.of("268341-016-L")))
                .thenReturn(List.of(storeInventory, nearbyInventory));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("268341-016-L"), null);

        assertEquals(List.of("1042", "1188"), responses.stream().map(InventoryResponse::getStoreId).toList());
        assertEquals(List.of(23, 4), responses.stream().map(InventoryResponse::getOnHand).toList());
    }
}
