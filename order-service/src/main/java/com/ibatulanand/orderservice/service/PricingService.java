package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.client.ProductClient;
import com.ibatulanand.orderservice.config.PricingProperties;
import com.ibatulanand.orderservice.dto.*;
import com.ibatulanand.orderservice.exception.OrderValidationException;
import com.ibatulanand.orderservice.pricing.OrderTotals;
import com.ibatulanand.orderservice.pricing.PricingEngine;
import com.ibatulanand.orderservice.pricing.PricingLine;
import com.ibatulanand.orderservice.pricing.PromotionRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingProperties pricingProperties;
    private final ProductClient productClient;
    private final PromotionResolver promotionResolver;
    private final PricingEngine pricingEngine = new PricingEngine();

    public QuoteResponse quote(OrderRequest request, String storeIdHeader) {
        String storeId = firstNonBlank(storeIdHeader, request.getStoreId());
        BigDecimal taxRate = storeId == null ? null : pricingProperties.getTaxRates().get(storeId);
        if (taxRate == null) {
            throw new OrderValidationException("No tax rate configured for store " + storeId);
        }

        List<LineItemRequest> requested = request.getLineItems();
        if (requested == null || requested.isEmpty()) {
            throw new OrderValidationException("At least one line item is required");
        }

        // Fetch each distinct sku once per request, preserving first-seen order.
        Map<String, ProductResponse> products = new LinkedHashMap<>();
        for (LineItemRequest line : requested) {
            String sku = line.getSkuCode();
            if (sku == null || sku.isBlank()) {
                throw new OrderValidationException("skuCode must not be blank");
            }
            if (line.getQuantity() == null || line.getQuantity() < 1) {
                throw new OrderValidationException("quantity must be at least 1 for skuCode " + sku);
            }
            if (!products.containsKey(sku)) {
                products.put(sku, productClient.findBySku(sku)
                        .orElseThrow(() -> new OrderValidationException("Unknown skuCode: " + sku)));
            }
        }

        List<PricedLineItem> pricedLines = new ArrayList<>();
        List<PricingLine> engineLines = new ArrayList<>();
        for (LineItemRequest line : requested) {
            ProductResponse product = products.get(line.getSkuCode());
            BigDecimal listPrice = product.effectiveListPrice();
            BigDecimal salePrice = product.effectiveSalePrice();
            if (listPrice == null || salePrice == null) {
                throw new OrderValidationException("No price available for skuCode " + line.getSkuCode());
            }
            int clearance = product.effectiveClearancePercent();
            boolean finalSale = product.effectiveFinalSale();
            pricedLines.add(new PricedLineItem(
                    product.getSkuCode(),
                    product.getStyleId(),
                    product.getDescription(),
                    product.getDepartment() + " · " + product.getCategory(),
                    product.getColorName(),
                    product.getColorCode(),
                    product.getSize(),
                    line.getQuantity(),
                    listPrice,
                    salePrice,
                    PricingEngine.round2(salePrice.multiply(BigDecimal.valueOf(line.getQuantity()))),
                    clearance > 0 ? "Clearance " + clearance + "%" : null,
                    clearance,
                    finalSale));
            engineLines.add(new PricingLine(listPrice, salePrice, line.getQuantity(), finalSale));
        }

        List<PromotionRule> promos = promotionResolver.resolve(request.getPromotions());
        OrderTotals totals = pricingEngine.calculate(
                engineLines, promos, pricingProperties.getServicesAndFees(), taxRate, request.isTaxExempt());

        List<PromotionDto> promoDtos = promos.stream()
                .map(p -> new PromotionDto(p.code(), p.description(), p.percentOff(), p.amountOff()))
                .toList();
        return new QuoteResponse(storeId, pricedLines, promoDtos, totals);
    }

    static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
