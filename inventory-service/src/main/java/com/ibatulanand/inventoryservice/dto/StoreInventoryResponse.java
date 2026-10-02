package com.ibatulanand.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreInventoryResponse {
    private String skuCode;
    private String storeId;
    private int onHand;
    private List<NearbyStoreAvailability> nearbyStores;
    private boolean shipFromStoreEligible;
    private String floor;
    private String fixture;
}
