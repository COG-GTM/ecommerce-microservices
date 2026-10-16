package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.NearbyStoreAvailability;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"inventory.nearby-stores.limit=2", "inventory.ship-from-store.safety-stock=5"})
class NearbyStoresLimitConfigTests extends MySqlContainerTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreRepository storeRepository;

    @Test
    void limitAndSafetyStockAreConfigurable() {
        String prefix = "CFG" + (System.nanoTime() % 100000);
        String sku = prefix + "-SKU";
        for (int step = 0; step <= 4; step++) {
            storeRepository.save(new Store(prefix + step, "Store " + step, 37.70 + step * 0.01, -122.40, true));
            inventoryRepository.save(Inventory.builder().skuCode(sku).storeId(prefix + step)
                    .onHand(step == 0 ? 10 : 4).floor("Floor 1").fixture("Fixture X-01").build());
        }

        var response = inventoryService.getStoreInventory(sku, prefix + 0);

        assertThat(response.getNearbyStores()).extracting(NearbyStoreAvailability::getStoreId)
                .containsExactly(prefix + 1, prefix + 2);
        // other stores hold 4 < safety stock 5, so the requesting store is the only source
        assertThat(response.isShipFromStoreEligible()).isFalse();
    }
}
