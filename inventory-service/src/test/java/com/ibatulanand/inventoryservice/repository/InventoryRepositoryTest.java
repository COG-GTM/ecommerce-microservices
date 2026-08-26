package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.DbTestBase;
import com.ibatulanand.inventoryservice.TestHelper;
import com.ibatulanand.inventoryservice.model.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRepositoryTest extends DbTestBase {

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        inventoryRepository.saveAll(List.of(
                TestHelper.inventoryFixture("iphone_15", 10),
                TestHelper.inventoryFixture("iphone_15_pro", 0)));
    }

    @Test
    void should_find_inventories_by_sku_codes() {
        List<Inventory> inventories =
                inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "iphone_15_pro"));

        assertEquals(2, inventories.size());
    }

    @Test
    void should_ignore_unknown_sku_codes() {
        List<Inventory> inventories =
                inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "unknown_sku"));

        assertEquals(1, inventories.size());
        assertEquals("iphone_15", inventories.get(0).getSkuCode());
    }

    @Test
    void should_return_empty_list_for_empty_sku_codes() {
        assertTrue(inventoryRepository.findBySkuCodeIn(List.of()).isEmpty());
    }
}
