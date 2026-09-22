package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void seed() {
        inventoryRepository.deleteAll();
        inventoryRepository.save(new Inventory(null, "iphone_15", 100));
        inventoryRepository.save(new Inventory(null, "iphone_15_pro", 0));
        inventoryRepository.save(new Inventory(null, "pixel_8", 5));
    }

    @Test
    void findBySkuCodeIn_returnsOnlyMatchingRows() {
        List<Inventory> found = inventoryRepository.findBySkuCodeIn(List.of("iphone_15", "pixel_8"));

        assertThat(found)
                .extracting(Inventory::getSkuCode, Inventory::getQuantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("iphone_15", 100),
                        org.assertj.core.groups.Tuple.tuple("pixel_8", 5));
    }

    @Test
    void findBySkuCodeIn_returnsEmptyForUnknownSkus() {
        assertThat(inventoryRepository.findBySkuCodeIn(List.of("does_not_exist"))).isEmpty();
    }

    @Test
    void save_generatesId() {
        Inventory saved = inventoryRepository.save(new Inventory(null, "new_sku", 1));
        assertThat(saved.getId()).isNotNull();
        assertThat(inventoryRepository.findById(saved.getId())).isPresent();
    }
}
