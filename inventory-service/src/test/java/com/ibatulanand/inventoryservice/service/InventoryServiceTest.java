package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void returnsInStockTrueWhenQuantityIsPositive() {
        List<String> skuCodes = List.of("sku-in-stock");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(List.of(new Inventory(1L, "sku-in-stock", 1)));

        assertThat(inventoryService.isInStock(skuCodes))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getSkuCode()).isEqualTo("sku-in-stock");
                    assertThat(response.isInStock()).isTrue();
                });
        verify(inventoryRepository).findBySkuCodeIn(skuCodes);
    }

    @Test
    void returnsInStockFalseWhenQuantityIsZero() {
        List<String> skuCodes = List.of("sku-out-of-stock");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(List.of(new Inventory(2L, "sku-out-of-stock", 0)));

        assertThat(inventoryService.isInStock(skuCodes))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getSkuCode()).isEqualTo("sku-out-of-stock");
                    assertThat(response.isInStock()).isFalse();
                });
        verify(inventoryRepository).findBySkuCodeIn(skuCodes);
    }

    @Test
    void preservesRepositoryOrderAndSkuCodesForMixedResults() {
        List<String> skuCodes = List.of("sku-first", "sku-second");
        when(inventoryRepository.findBySkuCodeIn(skuCodes))
                .thenReturn(List.of(
                        new Inventory(3L, "sku-first", 10),
                        new Inventory(4L, "sku-second", 0)));

        assertThat(inventoryService.isInStock(skuCodes))
                .extracting(response -> response.getSkuCode(), response -> response.isInStock())
                .containsExactly(
                        tuple("sku-first", true),
                        tuple("sku-second", false));
        verify(inventoryRepository).findBySkuCodeIn(skuCodes);
    }

    @Test
    void returnsEmptyForEmptyInput() {
        List<String> skuCodes = List.of();
        when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(List.of());

        assertThat(inventoryService.isInStock(skuCodes)).isEmpty();
        verify(inventoryRepository).findBySkuCodeIn(skuCodes);
    }

    @Test
    void returnsEmptyForUnknownSkuCodes() {
        List<String> skuCodes = List.of("unknown-sku");
        when(inventoryRepository.findBySkuCodeIn(skuCodes)).thenReturn(List.of());

        assertThat(inventoryService.isInStock(skuCodes)).isEmpty();
        verify(inventoryRepository).findBySkuCodeIn(skuCodes);
    }
}
