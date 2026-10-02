package com.ibatulanand.inventoryservice.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.model.StoreDistance;
import com.ibatulanand.inventoryservice.model.StoreDistanceId;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreDistanceRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "inventory.seed.enabled", havingValue = "true", matchIfMissing = true)
public class StoreInventorySeeder implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final StoreRepository storeRepository;
    private final StoreDistanceRepository storeDistanceRepository;
    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        SeedFile seedFile;
        try (InputStream inputStream = new ClassPathResource("seed/store-inventory.json").getInputStream()) {
            seedFile = objectMapper.readValue(inputStream, SeedFile.class);
        }

        Set<String> storeIds = new HashSet<>();
        storeRepository.findAll().forEach(store -> storeIds.add(store.getStoreId()));
        var stores = seedFile.stores().stream()
                .filter(store -> !storeIds.contains(store.storeId()))
                .map(store -> new Store(store.storeId(), store.name()))
                .toList();
        storeRepository.saveAll(stores);

        Set<StoreDistanceId> distanceIds = new HashSet<>();
        storeDistanceRepository.findAll().forEach(distance ->
                distanceIds.add(new StoreDistanceId(distance.getFromStoreId(), distance.getToStoreId())));
        var distances = seedFile.distances().stream()
                .filter(distance -> !distanceIds.contains(
                        new StoreDistanceId(distance.fromStoreId(), distance.toStoreId())))
                .map(distance -> new StoreDistance(
                        distance.fromStoreId(), distance.toStoreId(), distance.distanceMiles()))
                .toList();
        storeDistanceRepository.saveAll(distances);

        Set<String> inventoryIds = new HashSet<>();
        inventoryRepository.findAll().forEach(inventory ->
                inventoryIds.add(inventory.getSkuCode() + "\u0000" + inventory.getStoreId()));
        var inventory = seedFile.inventory().stream()
                .filter(item -> !inventoryIds.contains(item.skuCode() + "\u0000" + item.storeId()))
                .map(item -> new Inventory(null, item.skuCode(), item.storeId(), item.onHand(),
                        item.shipFromStoreEligible(), item.floor(), item.fixture()))
                .toList();
        inventoryRepository.saveAll(inventory);

        log.info("Seeded stores: {}, distances: {}, inventory rows: {}",
                stores.size(), distances.size(), inventory.size());
    }

    public record SeedFile(
            java.util.List<StoreSeed> stores,
            java.util.List<DistanceSeed> distances,
            java.util.List<InventorySeed> inventory) {
    }

    public record StoreSeed(String storeId, String name) {
    }

    public record DistanceSeed(String fromStoreId, String toStoreId, Double distanceMiles) {
    }

    public record InventorySeed(
            String skuCode,
            String storeId,
            Integer onHand,
            boolean shipFromStoreEligible,
            String floor,
            String fixture) {
    }
}
