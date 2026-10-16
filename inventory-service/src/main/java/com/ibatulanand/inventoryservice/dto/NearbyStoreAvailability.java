package com.ibatulanand.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NearbyStoreAvailability {
    private String storeId;
    private String storeName;
    private BigDecimal distanceMiles;
    private int onHand;
}
