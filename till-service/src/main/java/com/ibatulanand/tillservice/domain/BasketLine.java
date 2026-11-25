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
    /** Minimum customer age for an age-restricted line; null when unrestricted. */
    private Integer minimumAge;
    /** PENDING until the operator confirms ID was seen (APPROVED); null for unrestricted lines. */
    private AgeCheck ageCheck;
    /** Operator id that approved the Challenge 25 check. */
    private String ageApprovedBy;

    public enum AgeCheck { PENDING, APPROVED }

    public BasketLine(String lineId, String barcode, String skuCode, String name, int quantity, int unitPricePence,
                      int linePence) {
        this(lineId, barcode, skuCode, name, quantity, unitPricePence, linePence, null, null, null);
    }
}
