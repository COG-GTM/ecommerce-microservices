package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            Map<String, Integer> seed = new LinkedHashMap<>();
            seed.put("iphone_15", 100);
            seed.put("iphone_15_pro", 0);
            // Morrisons till demo grocery SKUs (barcodes 1001-1008 in product-service)
            for (String sku : GROCERY_SKUS) {
                seed.put(sku, 500);
            }
            seed.forEach((sku, quantity) -> {
                if (!inventoryRepository.existsBySkuCode(sku)) {
                    Inventory inventory = new Inventory();
                    inventory.setSkuCode(sku);
                    inventory.setQuantity(quantity);
                    inventoryRepository.save(inventory);
                }
            });
        };
    }

    static final List<String> GROCERY_SKUS = List.of(
            "MOR-MILK-SS-4PT", "MOR-BREAD-WM-800", "MOR-EGGS-FR-6", "MOR-BANANA-5",
            "MOR-OJ-1L", "MOR-CHEDDAR-MAT-400", "MOR-CHICKEN-BF-600", "MOR-CARROT-1KG");

}
