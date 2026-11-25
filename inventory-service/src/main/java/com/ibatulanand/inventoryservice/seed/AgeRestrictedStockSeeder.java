package com.ibatulanand.inventoryservice.seed;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stock for the age-restricted till SKUs (barcodes 3001-3002 in product-service), seeded when absent. */
@Component
@RequiredArgsConstructor
public class AgeRestrictedStockSeeder implements CommandLineRunner {

    static final List<String> SKUS = List.of("MOR-LAGER-4X440", "MOR-WINE-RED-75CL");

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        for (String sku : SKUS) {
            if (!inventoryRepository.existsBySkuCode(sku)) {
                Inventory inventory = new Inventory();
                inventory.setSkuCode(sku);
                inventory.setQuantity(500);
                inventoryRepository.save(inventory);
            }
        }
    }
}
