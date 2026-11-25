package com.ibatulanand.tillservice.pricing;

/** UK VAT rates the till sells under. Prices are VAT inclusive. */
public enum VatRate {
    Z(0),
    S(20);

    private final int percent;

    VatRate(int percent) {
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }

    /** Unknown or missing codes are treated as standard-rated, so VAT is never under-declared. */
    public static VatRate of(String code) {
        if (code != null) {
            for (VatRate rate : values()) {
                if (rate.name().equalsIgnoreCase(code.trim())) {
                    return rate;
                }
            }
        }
        return S;
    }
}
