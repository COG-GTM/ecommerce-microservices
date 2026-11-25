package com.ibatulanand.tillservice.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasketLine {

    /** How the line is priced: per unit, by weight (unitPricePence is per kg), or from a price-embedded label. */
    public enum PricedBy { EACH, WEIGHT, LABEL }

    private String lineId;
    private String barcode;
    private String skuCode;
    private String name;
    private int quantity;
    private int unitPricePence;
    /** Server-calculated line price before basket-level adjustments: quantity x unitPricePence, or weight x price/kg. */
    private int linePence;
    private PricedBy pricedBy = PricedBy.EACH;
    /** Weight in grams for WEIGHT lines, otherwise null. */
    private Integer weightGrams;

    public BasketLine(String lineId, String barcode, String skuCode, String name, int quantity, int unitPricePence,
                      int linePence) {
        this(lineId, barcode, skuCode, name, quantity, unitPricePence, linePence, PricedBy.EACH, null);
    }

    /** quantity x the price of one unit, where one unit of a WEIGHT line is its weight at the per-kg price (half up). */
    public int calculateLinePence() {
        int unit = pricedBy == PricedBy.WEIGHT && weightGrams != null
                ? (int) ((Math.multiplyExact((long) weightGrams, unitPricePence) + 500) / 1000)
                : unitPricePence;
        return Math.multiplyExact(quantity, unit);
    }
}
