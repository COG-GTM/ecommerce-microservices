package com.ibatulanand.inventoryservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreDistanceId implements Serializable {
    private String fromStoreId;
    private String toStoreId;
}
