package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Frozen POS-2 contract: {@code GET /api/inventory?skuCode=&storeId=} returns one object
 * matching {@code types.ts#Inventory}, or 404.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryResponse {
    private String skuCode;
    private String storeId;
    private Integer onHand;
    private List<NearbyStoreAvailability> nearbyStores;
    private boolean shipFromStoreEligible;
    private String floor;
    private String fixture;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class NearbyStoreAvailability {
        private String storeId;
        private String storeName;
        private Double distanceMiles;
        private Integer onHand;
    }
}
