package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @ParameterizedTest(name = "quantity {0} -> inStock {1}")
    @CsvSource({"100, true", "1, true", "0, false", "-1, false"})
    void stockFlagIsDerivedFromQuantityBoundary(int quantity, boolean expectedInStock) {
        when(inventoryRepository.findBySkuCodeIn(List.of("sku")))
                .thenReturn(List.of(new Inventory(1L, "sku", quantity)));

        assertThat(inventoryService.isInStock(List.of("sku")))
                .containsExactly(new InventoryResponse("sku", expectedInStock));
    }

    @Test
    void returnsOneResponsePerKnownSkuAndOmitsUnknownSkus() {
        List<String> skus = List.of("iphone_15", "iphone_15_pro", "unknown");
        when(inventoryRepository.findBySkuCodeIn(skus)).thenReturn(List.of(
                new Inventory(1L, "iphone_15", 100),
                new Inventory(2L, "iphone_15_pro", 0)));

        assertThat(inventoryService.isInStock(skus)).containsExactly(
                new InventoryResponse("iphone_15", true),
                new InventoryResponse("iphone_15_pro", false));
    }
}
