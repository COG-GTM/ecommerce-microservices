package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    // Golden data from pos-webapp/src/api/fixtures.ts (MOCK_INVENTORY). Coordinates are chosen so the
    // great-circle distances from store 1969 round to the fixture values: 2.4 / 5.1 / 8.3 / 9.6 mi.
    @Bean
    @Profile("dev")
    public CommandLineRunner loadGoldenData(StoreRepository storeRepository, InventoryRepository inventoryRepository) {
        return args -> {
            storeRepository.saveAll(List.of(
                    new Store("1969", "Mission District", 37.7599, -122.4148, true),
                    new Store("1042", "Union Square", 37.7939, -122.4060, true),
                    new Store("1177", "Stonestown", 37.7202, -122.4935, true),
                    new Store("1284", "Serramonte", 37.6518, -122.4811, true),
                    new Store("1391", "Bay Street", 37.8447, -122.2755, true)));

            List.of(
                    golden("268341-016-L", "1969", 23, "Floor 1", "Fixture T-12", true),
                    golden("268341-016-L", "1042", 11, "Floor 1", "Fixture T-12", true),
                    golden("268341-016-L", "1177", 6, "Floor 1", "Fixture T-12", true),
                    golden("268341-016-L", "1284", 4, "Floor 1", "Fixture T-12", true),
                    golden("268341-016-L", "1391", 2, "Floor 1", "Fixture T-12", true),
                    golden("471902-004-29", "1969", 7, "Floor 2", "Fixture D-04", true),
                    golden("471902-004-29", "1042", 5, "Floor 2", "Fixture D-04", true),
                    golden("471902-004-29", "1177", 3, "Floor 2", "Fixture D-04", true),
                    golden("471902-004-29", "1284", 0, "Floor 2", "Fixture D-04", true),
                    golden("471902-004-29", "1391", 0, "Floor 2", "Fixture D-04", true),
                    golden("512884-022-M", "1969", 12, "Floor 2", "Fixture F-07", false),
                    golden("512884-022-M", "1042", 9, "Floor 2", "Fixture F-07", false),
                    golden("512884-022-M", "1177", 0, "Floor 2", "Fixture F-07", false),
                    golden("512884-022-M", "1284", 1, "Floor 2", "Fixture F-07", false),
                    golden("512884-022-M", "1391", 0, "Floor 2", "Fixture F-07", false)
            ).forEach(seed -> {
                Inventory row = inventoryRepository.findBySkuCodeAndStoreId(seed.getSkuCode(), seed.getStoreId())
                        .orElse(seed);
                row.setOnHand(seed.getOnHand());
                row.setFloor(seed.getFloor());
                row.setFixture(seed.getFixture());
                row.setShipEligible(seed.isShipEligible());
                inventoryRepository.save(row);
            });
        };
    }

    private static Inventory golden(String skuCode, String storeId, int onHand, String floor, String fixture,
                                    boolean shipEligible) {
        return Inventory.builder()
                .skuCode(skuCode)
                .storeId(storeId)
                .onHand(onHand)
                .floor(floor)
                .fixture(fixture)
                .shipEligible(shipEligible)
                .build();
    }
}
