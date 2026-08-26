package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InventoryDataInitializer {

    private final InventoryRepository inventoryRepository;
    private final boolean seedEnabled;

    public InventoryDataInitializer(
            InventoryRepository inventoryRepository,
            @ConfigProperty(name = "inventory.seed.enabled", defaultValue = "true") boolean seedEnabled) {
        this.inventoryRepository = inventoryRepository;
        this.seedEnabled = seedEnabled;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled) {
            return;
        }

        seedIfMissing("iphone_15", 100);
        seedIfMissing("iphone_15_pro", 0);
    }

    private void seedIfMissing(String skuCode, int quantity) {
        if (inventoryRepository.find("skuCode", skuCode).firstResultOptional().isEmpty()) {
            Inventory inventory = new Inventory();
            inventory.setSkuCode(skuCode);
            inventory.setQuantity(quantity);
            inventoryRepository.persist(inventory);
        }
    }
}
