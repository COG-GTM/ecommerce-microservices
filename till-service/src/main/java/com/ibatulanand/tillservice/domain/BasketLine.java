package com.ibatulanand.tillservice.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasketLine {
    private String lineId;
    private String barcode;
    private String skuCode;
    private String name;
    private int quantity;
    private int unitPricePence;
    /** quantity x unitPricePence, before basket-level adjustments. Server-calculated. */
    private int linePence;
}
