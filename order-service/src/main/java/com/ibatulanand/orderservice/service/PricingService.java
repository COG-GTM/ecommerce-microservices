package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderTotals;
import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.model.Promotion;
import com.ibatulanand.orderservice.repository.PromotionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side POS pricing. Totals reproduce {@code pos-webapp/src/lib/totals.ts#calculateTotals}
 * exactly, including its double arithmetic and {@code roundCurrency}, so the server and the
 * register agree to the cent (PRE-1 golden cart: 159.85 / 63.54 / 108.31 / 9.34 / 117.65).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final WebClient.Builder webClientBuilder;
    private final PromotionRepository promotionRepository;
    private final Environment environment;

    @Value("${order.product-service.base-url:http://product-service}")
    private String productServiceUrl;

    public record PricedLineInput(ProductResponse product, int quantity) {
    }

    public record PricedLine(ProductResponse product,
                             int quantity,
                             BigDecimal listPrice,
                             BigDecimal unitPrice,
                             BigDecimal markdownAmount,
                             BigDecimal extendedPrice,
                             Map<String, BigDecimal> promotionAllocations) {

        public BigDecimal promotionAmount() {
            return promotionAllocations.values().stream().reduce(money(0), BigDecimal::add);
        }
    }

    public record PricingResult(List<PricedLine> lines,
                                Map<String, BigDecimal> promotionTotals,
                                OrderTotals totals) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedPromotions() {
        seed("FALL30", "30% off", 30);
        seed("CARD10", "10% off with Gap card", 10);
    }

    private void seed(String code, String description, int percentOff) {
        if (!promotionRepository.existsById(code)) {
            promotionRepository.save(new Promotion(code, description, BigDecimal.valueOf(percentOff),
                    money(0), true));
        }
    }

    /** POS-1 frozen contract: one {@code Product} or 404. Unknown SKU means it can't be sold: 409. */
    @CircuitBreaker(name = "product", fallbackMethod = "productFallback")
    public ProductResponse lookupProduct(String skuCode) {
        ProductResponse product = webClientBuilder.build().get()
                .uri(productServiceUrl + "/api/product", b -> b.queryParam("skuCode", skuCode).build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(OrderProblemException.outOfStock("Unknown SKU " + skuCode)))
                .bodyToMono(ProductResponse.class)
                .block();
        if (product == null) {
            throw OrderProblemException.outOfStock("Unknown SKU " + skuCode);
        }
        if (product.getListPrice() == null || product.getSalePrice() == null) {
            throw OrderProblemException.dependencyUnavailable(
                    "product-service returned no price for SKU " + skuCode);
        }
        return product;
    }

    @SuppressWarnings("unused")
    private ProductResponse productFallback(String skuCode, Throwable throwable) {
        if (throwable instanceof OrderProblemException problem) {
            throw problem;
        }
        log.warn("product-service lookup failed for {}: {}", skuCode, throwable.toString());
        throw OrderProblemException.dependencyUnavailable("product-service is unavailable");
    }

    public BigDecimal taxRateFor(String storeId) {
        String rate = environment.getProperty("order.pricing.tax-rate." + storeId);
        if (rate == null) {
            throw OrderProblemException.validationFailed("No tax rate configured for store " + storeId);
        }
        return new BigDecimal(rate);
    }

    /** Resolves promotion codes in request order. Unknown, inactive or repeated codes → 422. */
    public List<Promotion> resolvePromotions(List<String> codes) {
        List<Promotion> promotions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : codes == null ? List.<String>of() : codes) {
            String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!seen.add(code)) {
                throw OrderProblemException.invalidPromotion("Promotion " + code + " applied more than once");
            }
            Promotion promotion = promotionRepository.findById(code)
                    .filter(Promotion::isActive)
                    .orElseThrow(() -> OrderProblemException.invalidPromotion("Unknown promotion " + code));
            promotions.add(promotion);
        }
        return promotions;
    }

    public PricingResult price(List<PricedLineInput> inputs,
                               List<Promotion> promotions,
                               BigDecimal servicesAndFees,
                               boolean taxExempt,
                               BigDecimal taxRate) {
        // Order-level totals: same operations, same order, same doubles as totals.ts.
        double merchandise = 0;
        double markdown = 0;
        double promotionEligible = 0;
        for (PricedLineInput in : inputs) {
            double list = in.product().getListPrice().doubleValue();
            double unit = in.product().getSalePrice().doubleValue();
            merchandise += list * in.quantity();
            markdown += (list - unit) * in.quantity();
            if (!in.product().isFinalSale()) {
                promotionEligible += unit * in.quantity();
            }
        }
        double promotionTotal = 0;
        Map<String, BigDecimal> promotionTotals = new LinkedHashMap<>();
        for (Promotion promo : promotions) {
            double amount = promotionEligible > 0
                    ? (promotionEligible * promo.getPercentOff().doubleValue()) / 100 + promo.getAmountOff().doubleValue()
                    : 0;
            promotionTotal += amount;
            promotionTotals.put(promo.getCode(), money(roundCurrency(amount)));
        }
        double services = servicesAndFees.doubleValue();
        double rate = taxRate.doubleValue();
        double merchandiseTotal = roundCurrency(merchandise);
        double discountTotal = roundCurrency(markdown + promotionTotal);
        double taxableSubtotal = roundCurrency(merchandiseTotal - discountTotal + services);
        double salesTax = taxExempt ? 0 : roundCurrency(taxableSubtotal * rate);
        double total = roundCurrency(taxableSubtotal + salesTax);

        OrderTotals totals = OrderTotals.builder()
                .merchandiseTotal(money(merchandiseTotal))
                .servicesAndFees(money(roundCurrency(services)))
                .discountTotal(money(discountTotal))
                .taxableSubtotal(money(taxableSubtotal))
                .taxRate(taxRate)
                .salesTax(money(salesTax))
                .total(money(total))
                .savedToday(money(discountTotal))
                .taxExempt(taxExempt)
                .build();

        List<PricedLine> lines = new ArrayList<>();
        for (PricedLineInput in : inputs) {
            BigDecimal list = in.product().getListPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal unit = in.product().getSalePrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal qty = BigDecimal.valueOf(in.quantity());
            lines.add(new PricedLine(in.product(), in.quantity(), list, unit,
                    list.subtract(unit).multiply(qty), unit.multiply(qty), new LinkedHashMap<>()));
        }
        allocatePromotions(lines, promotionTotals);
        return new PricingResult(lines, promotionTotals, totals);
    }

    /**
     * Spreads each promotion over the non-final-sale lines in proportion to extended price.
     * Allocations sum exactly to the promotion's order-level amount; the rounding remainder
     * goes to the last eligible line.
     */
    private void allocatePromotions(List<PricedLine> lines, Map<String, BigDecimal> promotionTotals) {
        List<PricedLine> eligible = lines.stream()
                .filter(line -> !line.product().isFinalSale() && line.extendedPrice().signum() > 0)
                .toList();
        BigDecimal eligibleTotal = eligible.stream().map(PricedLine::extendedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        promotionTotals.forEach((code, amount) -> {
            BigDecimal allocated = money(0);
            for (int i = 0; i < eligible.size(); i++) {
                PricedLine line = eligible.get(i);
                BigDecimal share = i == eligible.size() - 1
                        ? amount.subtract(allocated)
                        : amount.multiply(line.extendedPrice()).divide(eligibleTotal, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(share);
                line.promotionAllocations().merge(code, share, BigDecimal::add);
            }
        });
    }

    /** {@code totals.ts#roundCurrency}: {@code Math.round((value + Number.EPSILON) * 100) / 100}. */
    static double roundCurrency(double value) {
        return Math.round((value + Math.ulp(1.0)) * 100) / 100.0;
    }

    static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
