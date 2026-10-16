package com.ibatulanand.inventoryservice;

import com.ibatulanand.inventoryservice.dto.InventoryResponse;
import com.ibatulanand.inventoryservice.dto.NearbyStoreAvailability;
import com.ibatulanand.inventoryservice.dto.StoreInventoryResponse;
import com.ibatulanand.inventoryservice.exception.SkuNotFoundException;
import com.ibatulanand.inventoryservice.exception.StoreNotFoundException;
import com.ibatulanand.inventoryservice.model.Inventory;
import com.ibatulanand.inventoryservice.model.Store;
import com.ibatulanand.inventoryservice.repository.InventoryRepository;
import com.ibatulanand.inventoryservice.repository.StoreRepository;
import com.ibatulanand.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stores sit due north of an origin at 0.01 degree latitude steps (~0.69 mi each), so the expected
 * great-circle distances are 0.7, 1.4, 2.1, 2.8, 3.5, 4.1 mi. Every test uses its own store/SKU ids.
 */
@SpringBootTest
class InventoryRulesTests extends MySqlContainerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final double ORIGIN_LAT = 37.70;
    private static final double ORIGIN_LON = -122.40;

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private StoreRepository storeRepository;

    @Test
    void nearbyStoresAreNearestFirstAndLimitedToFour() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        stock(sku, origin, 10);
        // inserted out of order so ordering must come from distance, not insertion
        for (int step : new int[]{6, 2, 5, 1, 4, 3}) {
            stock(sku, store(prefix + step, step, true), step);
        }

        StoreInventoryResponse response = inventoryService.getStoreInventory(sku, origin.getStoreId());

        assertThat(response.getNearbyStores()).extracting(NearbyStoreAvailability::getStoreId)
                .containsExactly(prefix + 1, prefix + 2, prefix + 3, prefix + 4);
        assertThat(response.getNearbyStores()).extracting(NearbyStoreAvailability::getDistanceMiles)
                .containsExactly(new BigDecimal("0.7"), new BigDecimal("1.4"), new BigDecimal("2.1"),
                        new BigDecimal("2.8"));
        assertThat(response.getNearbyStores()).extracting(NearbyStoreAvailability::getOnHand)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void nearbyStoresExcludeZeroOnHandAndTheRequestingStore() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        stock(sku, origin, 10);
        stock(sku, store(prefix + 1, 1, true), 0);
        stock(sku, store(prefix + 2, 2, true), 3);
        stock(sku, store(prefix + 3, 3, true), 0);
        stock(sku, store(prefix + 4, 4, true), 1);

        StoreInventoryResponse response = inventoryService.getStoreInventory(sku, origin.getStoreId());

        assertThat(response.getOnHand()).isEqualTo(10);
        assertThat(response.getNearbyStores()).extracting(NearbyStoreAvailability::getStoreId)
                .containsExactly(prefix + 2, prefix + 4);
    }

    @Test
    void storeWithoutRowForKnownSkuReportsZeroOnHand() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        stock(sku, store(prefix + 1, 1, true), 5);

        StoreInventoryResponse response = inventoryService.getStoreInventory(sku, origin.getStoreId());

        assertThat(response.getOnHand()).isZero();
        assertThat(response.getFloor()).isNull();
        assertThat(response.getNearbyStores()).hasSize(1);
    }

    @Test
    void eligibleWhenAnotherShipFromStoreSourceMeetsSafetyStock() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        stock(sku, origin, 10);
        stock(sku, store(prefix + 1, 1, true), 2); // exactly the default safety stock of 2

        assertThat(inventoryService.getStoreInventory(sku, origin.getStoreId()).isShipFromStoreEligible()).isTrue();
    }

    @Test
    void notEligibleWhenSkuIsNotShipEligible() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        inventoryRepository.save(row(sku, origin, 10, false));
        inventoryRepository.save(row(sku, store(prefix + 1, 1, true), 10, false));

        assertThat(inventoryService.getStoreInventory(sku, origin.getStoreId()).isShipFromStoreEligible()).isFalse();
    }

    @Test
    void notEligibleWhenRequestingStoreIsTheOnlySource() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, true);
        stock(sku, origin, 50);
        stock(sku, store(prefix + 1, 1, true), 1);  // below safety stock
        stock(sku, store(prefix + 2, 2, false), 9); // ship-from-store disabled

        StoreInventoryResponse response = inventoryService.getStoreInventory(sku, origin.getStoreId());

        assertThat(response.isShipFromStoreEligible()).isFalse();
        assertThat(response.getNearbyStores()).hasSize(2);
    }

    @Test
    void notEligibleWhenNoStoreHasShipFromStoreEnabled() {
        String prefix = prefix();
        String sku = prefix + "-SKU";
        Store origin = store(prefix + "O", 0, false);
        stock(sku, origin, 10);
        stock(sku, store(prefix + 1, 1, false), 10);

        assertThat(inventoryService.getStoreInventory(sku, origin.getStoreId()).isShipFromStoreEligible()).isFalse();
    }

    @Test
    void skuCodeAndStoreIdAreUnique() {
        String prefix = prefix();
        Store origin = store(prefix + "O", 0, true);
        inventoryRepository.saveAndFlush(row(prefix + "-SKU", origin, 1, true));

        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(row(prefix + "-SKU", origin, 2, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownSkuOrStoreThrowNotFound() {
        String prefix = prefix();
        Store origin = store(prefix + "O", 0, true);
        stock(prefix + "-SKU", origin, 1);

        assertThatThrownBy(() -> inventoryService.getStoreInventory(prefix + "-NOPE", origin.getStoreId()))
                .isInstanceOf(SkuNotFoundException.class);
        assertThatThrownBy(() -> inventoryService.getStoreInventory(prefix + "-SKU", prefix + "X"))
                .isInstanceOf(StoreNotFoundException.class);
    }

    @Test
    void legacyFormSumsAcrossStoresAndReportsUnknownSkus() {
        String prefix = prefix();
        Store a = store(prefix + "A", 1, true);
        Store b = store(prefix + "B", 2, true);
        stock(prefix + "-IN", a, 0);
        stock(prefix + "-IN", b, 3);
        stock(prefix + "-OUT", a, 0);
        stock(prefix + "-OUT", b, 0);

        List<InventoryResponse> response = inventoryService.isInStock(
                List.of(prefix + "-IN", prefix + "-OUT", prefix + "-NOPE", prefix + "-IN"));

        assertThat(response).extracting(InventoryResponse::getSkuCode, InventoryResponse::isInStock)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(prefix + "-IN", true),
                        org.assertj.core.groups.Tuple.tuple(prefix + "-OUT", false),
                        org.assertj.core.groups.Tuple.tuple(prefix + "-NOPE", false));
    }

    private static String prefix() {
        return "T" + SEQ.incrementAndGet() + "-" + (System.nanoTime() % 100000);
    }

    private Store store(String storeId, int stepsNorth, boolean shipFromStoreEnabled) {
        return storeRepository.save(new Store(storeId, "Store " + storeId,
                ORIGIN_LAT + stepsNorth * 0.01, ORIGIN_LON, shipFromStoreEnabled));
    }

    private void stock(String sku, Store store, int onHand) {
        inventoryRepository.save(row(sku, store, onHand, true));
    }

    private static Inventory row(String sku, Store store, int onHand, boolean shipEligible) {
        return Inventory.builder()
                .skuCode(sku)
                .storeId(store.getStoreId())
                .onHand(onHand)
                .floor("Floor 1")
                .fixture("Fixture X-01")
                .shipEligible(shipEligible)
                .build();
    }
}
