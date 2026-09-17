package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            seed(inventoryRepository, "iphone_15", 100);
            seed(inventoryRepository, "iphone_15_pro", 0);
        };
    }

    private void seed(InventoryRepository inventoryRepository, String skuCode, int quantity) {
        if (inventoryRepository.existsBySkuCode(skuCode)) {
            return;
        }
        Inventory inventory = new Inventory();
        inventory.setSkuCode(skuCode);
        inventory.setQuantity(quantity);
        inventoryRepository.save(inventory);
    }

}
