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
    /** UK VAT code of the item: Z (0%) or S (20%). */
    private String vatCode;
    /** Minimum customer age for an age-restricted line; null when unrestricted. */
    private Integer minimumAge;
    /** PENDING until the operator confirms ID was seen (APPROVED); null for unrestricted lines. */
    private AgeCheck ageCheck;
    /** Operator id that approved the Challenge 25 check. */
    private String ageApprovedBy;

    public enum AgeCheck { PENDING, APPROVED }


    public BasketLine(String lineId, String barcode, String skuCode, String name, int quantity, int unitPricePence,
                      int linePence) {
        this(lineId, barcode, skuCode, name, quantity, unitPricePence, linePence, PricedBy.EACH, null, null, null, null, null);
    }

    public BasketLine(String lineId, String barcode, String skuCode, String name, int quantity, int unitPricePence,
                      int linePence, PricedBy pricedBy, Integer weightGrams) {
        this(lineId, barcode, skuCode, name, quantity, unitPricePence, linePence, pricedBy, weightGrams, null, null, null, null);
    }

    /** quantity x the price of one unit, where one unit of a WEIGHT line is its weight at the per-kg price (half up). */
    public int calculateLinePence() {
        int unit = pricedBy == PricedBy.WEIGHT && weightGrams != null
                ? weighedPence(weightGrams, unitPricePence)
                : unitPricePence;
        return Math.multiplyExact(quantity, unit);
    }

    /** Price of a weight at a per-kg price, rounded half-up to the penny. */
    public static int weighedPence(int weightGrams, int pricePencePerKg) {
        return Math.toIntExact((Math.multiplyExact((long) weightGrams, pricePencePerKg) + 500) / 1000);
    }
}
