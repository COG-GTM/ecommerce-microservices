package com.ibatulanand.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockReservationItem {
    private String skuCode;
    private Integer quantity;
}
