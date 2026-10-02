package com.ibatulanand.inventoryservice.service;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.NearbyStoreAvailability;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
import com.ibatulanand.inventoryservice.exception.InventoryNotFoundException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.model.StoreDistance;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreDistanceRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    public static final String HOME_STORE_ID = "1969";
    public static final double NEARBY_RADIUS_MILES = 10.0;

    private final InventoryRepository inventoryRepository;
    private final StoreDistanceRepository storeDistanceRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCodes) {
        Map<String, Inventory> inventoryBySkuCode = inventoryRepository
                .findBySkuCodeInAndStoreId(skuCodes, HOME_STORE_ID).stream()
                .collect(Collectors.toMap(Inventory::getSkuCode, Function.identity()));
        return skuCodes.stream()
                .map(inventoryBySkuCode::get)
                .filter(Objects::nonNull)
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getOnHand() > 0)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreInventoryResponse getStoreInventory(String skuCode, String storeId) {
        Inventory inventory = inventoryRepository.findBySkuCodeAndStoreId(skuCode, storeId)
                .orElseThrow(InventoryNotFoundException::new);

        List<StoreDistance> distances = storeDistanceRepository
                .findByFromStoreIdAndDistanceMilesLessThanEqualOrderByDistanceMilesAsc(
                        storeId, NEARBY_RADIUS_MILES);
        List<String> nearbyStoreIds = distances.stream().map(StoreDistance::getToStoreId).toList();

        Map<String, Inventory> inventoryByStore = inventoryRepository
                .findBySkuCodeAndStoreIdIn(skuCode, nearbyStoreIds).stream()
                .collect(Collectors.toMap(Inventory::getStoreId, Function.identity()));
        Map<String, Store> storesById = storeRepository.findAllById(nearbyStoreIds).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));

        List<NearbyStoreAvailability> nearbyStores = distances.stream()
                .map(distance -> {
                    Inventory nearbyInventory = inventoryByStore.get(distance.getToStoreId());
                    Store nearbyStore = storesById.get(distance.getToStoreId());
                    if (nearbyInventory == null || nearbyInventory.getOnHand() <= 0) {
                        return null;
                    }
                    return NearbyStoreAvailability.builder()
                            .storeId(nearbyInventory.getStoreId())
                            .storeName(nearbyStore.getName())
                            .distanceMiles(distance.getDistanceMiles())
                            .onHand(nearbyInventory.getOnHand())
                            .build();
                })
                .filter(availability -> availability != null)
                .toList();

        return StoreInventoryResponse.builder()
                .skuCode(inventory.getSkuCode())
                .storeId(inventory.getStoreId())
                .onHand(inventory.getOnHand())
                .nearbyStores(nearbyStores)
                .shipFromStoreEligible(inventory.isShipFromStoreEligible())
                .floor(inventory.getFloor())
                .fixture(inventory.getFixture())
                .build();
    }
}
