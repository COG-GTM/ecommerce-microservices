package com.ibatulanand.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyStoreAvailability {
    private String storeId;
    private String storeName;
    private double distanceMiles;
    private int onHand;
}
