package com.ibatulanand.inventoryservice.seed;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stock for the till promotion products (MBA-3934, barcodes 393401-393407 in product-service), when absent. */
// Runs before the grocery seed that the till readiness probe waits for, so promotion items are ready with it.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PromotionStockSeeder implements CommandLineRunner {

    static final List<String> PROMOTION_SKUS = List.of(
            "MOR-SANDWICH-CHKBAC", "MOR-SANDWICH-EGGMAYO", "MOR-CRISPS-SALTED-30G", "MOR-FRUIT-POT",
            "MOR-WATER-STILL-500", "MOR-COLA-500", "MOR-YOGHURT-GREEK-500");

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        for (String sku : PROMOTION_SKUS) {
            if (!inventoryRepository.existsBySkuCode(sku)) {
                Inventory inventory = new Inventory();
                inventory.setSkuCode(sku);
                inventory.setQuantity(500);
                inventoryRepository.save(inventory);
            }
        }
    }
}
