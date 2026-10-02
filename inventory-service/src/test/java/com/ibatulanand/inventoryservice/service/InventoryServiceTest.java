package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
import com.ibatulanand.inventoryservice.exception.InventoryNotFoundException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.model.StoreDistance;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreDistanceRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StoreDistanceRepository storeDistanceRepository;
    @Mock
    private StoreRepository storeRepository;
    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void returnsOnlyAvailableNearbyStoresInDistanceOrder() {
        when(inventoryRepository.findBySkuCodeAndStoreId("sku", "1969"))
                .thenReturn(Optional.of(inventory("sku", "1969", 23)));
        when(storeDistanceRepository
                .findByFromStoreIdAndDistanceMilesLessThanEqualOrderByDistanceMilesAsc("1969", 10.0))
                .thenReturn(List.of(
                        new StoreDistance("1969", "1042", 2.4),
                        new StoreDistance("1969", "1177", 5.1),
                        new StoreDistance("1969", "1284", 8.3)));
        when(inventoryRepository.findBySkuCodeAndStoreIdIn("sku", List.of("1042", "1177", "1284")))
                .thenReturn(List.of(
                        inventory("sku", "1284", 4),
                        inventory("sku", "1042", 0),
                        inventory("sku", "1177", 6)));
        when(storeRepository.findAllById(List.of("1042", "1177", "1284")))
                .thenReturn(List.of(
                        new Store("1284", "Serramonte"),
                        new Store("1177", "Stonestown"),
                        new Store("1042", "Union Square")));

        StoreInventoryResponse response = inventoryService.getStoreInventory("sku", "1969");

        assertEquals(List.of("1177", "1284"),
                response.getNearbyStores().stream().map(availability -> availability.getStoreId()).toList());
        assertEquals(List.of("Stonestown", "Serramonte"),
                response.getNearbyStores().stream().map(availability -> availability.getStoreName()).toList());
        assertEquals(List.of(5.1, 8.3),
                response.getNearbyStores().stream().map(availability -> availability.getDistanceMiles()).toList());
        assertEquals(List.of(6, 4),
                response.getNearbyStores().stream().map(availability -> availability.getOnHand()).toList());
        verify(storeDistanceRepository)
                .findByFromStoreIdAndDistanceMilesLessThanEqualOrderByDistanceMilesAsc("1969", 10.0);
    }

    @Test
    void throwsNotFoundWhenInventoryRowDoesNotExist() {
        when(inventoryRepository.findBySkuCodeAndStoreId("missing", "1969")).thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class,
                () -> inventoryService.getStoreInventory("missing", "1969"));
    }

    @Test
    void legacyStockQueryUsesHomeStoreAndMapsOnHand() {
        when(inventoryRepository.findBySkuCodeInAndStoreId(List.of("available", "empty"), "1969"))
                .thenReturn(List.of(
                        inventory("available", "1969", 1),
                        inventory("empty", "1969", 0)));

        List<InventoryResponse> responses = inventoryService.isInStock(List.of("available", "empty"));

        assertTrue(responses.get(0).isInStock());
        assertFalse(responses.get(1).isInStock());
        verify(inventoryRepository).findBySkuCodeInAndStoreId(List.of("available", "empty"), "1969");
    }

    private Inventory inventory(String skuCode, String storeId, int onHand) {
        return new Inventory(null, skuCode, storeId, onHand, true, "Floor 1", "Fixture T-12");
    }
}
