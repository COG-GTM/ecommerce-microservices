package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.support.AbstractMySqlTest;
import com.ibatulanand.inventoryservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryRepositoryTest extends AbstractMySqlTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void seed() {
        inventoryRepository.deleteAll();
        inventoryRepository.saveAll(List.of(
                TestFixtures.inventory("iphone_15", 10),
                TestFixtures.inventory("iphone_15_pro", 0),
                TestFixtures.inventory("pixel_8", 5)));
    }

    @Test
    void should_return_only_matching_rows_when_sku_codes_exist() {
        List<Inventory> found = inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "pixel_8"));

        assertThat(found).extracting(Inventory::getSkuCode)
                .containsExactlyInAnyOrder("iphone_15", "pixel_8");
    }

    @Test
    void should_return_empty_result_when_no_sku_code_matches() {
        assertThat(inventoryRepository.findBySkuCodeIn(List.of("unknown_sku"))).isEmpty();
    }

    @Test
    void should_return_matching_subset_when_only_some_sku_codes_exist() {
        List<Inventory> found = inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "unknown_sku"));

        assertThat(found).extracting(Inventory::getSkuCode).containsExactly("iphone_15");
    }
}
