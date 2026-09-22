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
            Inventory inventory1 = new Inventory();
            inventory1.setSkuCode("268341-016-L");
            inventory1.setStoreId("1042");
            inventory1.setOnHand(23);
            inventory1.setShipFromStoreEligible(true);

            Inventory inventory2 = new Inventory();
            inventory2.setSkuCode("268341-016-M");
            inventory2.setStoreId("1042");
            inventory2.setOnHand(0);
            inventory2.setShipFromStoreEligible(false);

            inventoryRepository.save(inventory1);
            inventoryRepository.save(inventory2);
        };
    }

}
