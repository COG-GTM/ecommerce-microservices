package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.NearbyStoreAvailability;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
import com.ibatulanand.inventoryservice.exception.SkuNotFoundException;
import com.ibatulanand.inventoryservice.exception.StoreNotFoundException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private final InventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final int nearbyStoresLimit;
    private final int shipFromStoreSafetyStock;

    public InventoryService(InventoryRepository inventoryRepository,
                            StoreRepository storeRepository,
                            @Value("${inventory.nearby-stores.limit:4}") int nearbyStoresLimit,
                            @Value("${inventory.ship-from-store.safety-stock:2}") int shipFromStoreSafetyStock) {
        this.inventoryRepository = inventoryRepository;
        this.storeRepository = storeRepository;
        this.nearbyStoresLimit = nearbyStoresLimit;
        this.shipFromStoreSafetyStock = shipFromStoreSafetyStock;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCodes) {
        Map<String, Integer> onHandBySku = inventoryRepository.findBySkuCodeIn(skuCodes).stream()
                .collect(Collectors.groupingBy(Inventory::getSkuCode, Collectors.summingInt(Inventory::getOnHand)));

        return new LinkedHashSet<>(skuCodes).stream()
                .map(skuCode -> InventoryResponse.builder()
                        .skuCode(skuCode)
                        .isInStock(onHandBySku.getOrDefault(skuCode, 0) > 0)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreInventoryResponse getStoreInventory(String skuCode, String storeId) {
        Store requestingStore = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        List<Inventory> rows = inventoryRepository.findBySkuCode(skuCode);
        if (rows.isEmpty()) {
            throw new SkuNotFoundException(skuCode);
        }

        Map<String, Store> stores = storeRepository.findAllById(
                        rows.stream().map(Inventory::getStoreId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));
        Inventory local = rows.stream()
                .filter(row -> row.getStoreId().equals(storeId))
                .findFirst()
                .orElse(null);

        return StoreInventoryResponse.builder()
                .skuCode(skuCode)
                .storeId(storeId)
                .onHand(local == null ? 0 : local.getOnHand())
                .nearbyStores(nearbyStores(requestingStore, rows, stores))
                .shipFromStoreEligible(isShipFromStoreEligible(storeId, rows, stores))
                .floor(local == null ? null : local.getFloor())
                .fixture(local == null ? null : local.getFixture())
                .build();
    }

    private List<NearbyStoreAvailability> nearbyStores(Store origin, List<Inventory> rows, Map<String, Store> stores) {
        return rows.stream()
                .filter(row -> !row.getStoreId().equals(origin.getStoreId()))
                .filter(row -> row.getOnHand() > 0)
                .filter(row -> stores.containsKey(row.getStoreId()))
                .map(row -> new Candidate(stores.get(row.getStoreId()), row.getOnHand(),
                        distanceMiles(origin, stores.get(row.getStoreId()))))
                .sorted(Comparator.comparingDouble(Candidate::distance)
                        .thenComparing(candidate -> candidate.store().getStoreId()))
                .limit(nearbyStoresLimit)
                .map(candidate -> NearbyStoreAvailability.builder()
                        .storeId(candidate.store().getStoreId())
                        .storeName(candidate.store().getStoreName())
                        .distanceMiles(BigDecimal.valueOf(candidate.distance()).setScale(1, RoundingMode.HALF_UP))
                        .onHand(candidate.onHand())
                        .build())
                .toList();
    }

    private record Candidate(Store store, int onHand, double distance) {
    }

    private boolean isShipFromStoreEligible(String storeId, List<Inventory> rows, Map<String, Store> stores) {
        boolean skuShipEligible = rows.stream().allMatch(Inventory::isShipEligible);
        boolean otherSourceAvailable = rows.stream()
                .filter(row -> row.getOnHand() >= shipFromStoreSafetyStock)
                .filter(row -> {
                    Store store = stores.get(row.getStoreId());
                    return store != null && store.isShipFromStoreEnabled();
                })
                .anyMatch(row -> !row.getStoreId().equals(storeId));
        return skuShipEligible && otherSourceAvailable;
    }

    static double distanceMiles(Store from, Store to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.sqrt(h));
    }
}
