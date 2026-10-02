package com.ibatulanand.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "pos.pricing")
public class PricingProperties {

    private BigDecimal servicesAndFees = BigDecimal.ZERO;
    private Map<String, BigDecimal> taxRates = new LinkedHashMap<>();
    private Map<String, Promo> promotions = new LinkedHashMap<>();

    public BigDecimal getServicesAndFees() {
        return servicesAndFees;
    }

    public void setServicesAndFees(BigDecimal servicesAndFees) {
        this.servicesAndFees = servicesAndFees;
    }

    public Map<String, BigDecimal> getTaxRates() {
        return taxRates;
    }

    public void setTaxRates(Map<String, BigDecimal> taxRates) {
        this.taxRates = taxRates;
    }

    public Map<String, Promo> getPromotions() {
        return promotions;
    }

    public void setPromotions(Map<String, Promo> promotions) {
        this.promotions = promotions;
    }

    public static class Promo {
        private String description;
        private BigDecimal percentOff = BigDecimal.ZERO;
        private BigDecimal amountOff = BigDecimal.ZERO;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPercentOff() {
            return percentOff;
        }

        public void setPercentOff(BigDecimal percentOff) {
            this.percentOff = percentOff;
        }

        public BigDecimal getAmountOff() {
            return amountOff;
        }

        public void setAmountOff(BigDecimal amountOff) {
            this.amountOff = amountOff;
        }
    }
}
