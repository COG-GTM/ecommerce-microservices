package com.ibatulanand.tillservice.loyalty;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * More Card loyalty settings, bound from till.more-card.*.
 *
 * @param pointsPerPound points earned per whole pound of the amount paid (after savings)
 * @param memberPrices   member-only unit price in pence, keyed by barcode
 */
@ConfigurationProperties(prefix = "till.more-card")
public record MoreCardProperties(Integer pointsPerPound, Map<String, Integer> memberPrices) {

    public MoreCardProperties {
        pointsPerPound = pointsPerPound == null ? 5 : pointsPerPound;
        memberPrices = memberPrices == null ? Map.of("1006", 300, "1007", 400) : Map.copyOf(memberPrices);
    }

    public int pointsFor(int paidPence) {
        return paidPence / 100 * pointsPerPound;
    }
}
