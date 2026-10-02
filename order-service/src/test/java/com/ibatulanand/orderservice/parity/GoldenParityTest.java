package com.ibatulanand.orderservice.parity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.config.PricingProperties;
import com.ibatulanand.orderservice.pricing.OrderTotals;
import com.ibatulanand.orderservice.pricing.PricingEngine;
import com.ibatulanand.orderservice.pricing.PricingLine;
import com.ibatulanand.orderservice.pricing.PricingRoundingMode;
import com.ibatulanand.orderservice.pricing.PromotionRule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden parity: every cart in ../parity/carts priced by the real PricingEngine
 * in LEGACY_POS mode must match ../parity/expected exactly on every OrderTotals
 * field. A separate check asserts HALF_UP diverges only on the half-cent carts.
 */
class GoldenParityTest {

    private static final Path PARITY_DIR = Path.of("..", "parity");
    private static final Path CARTS_DIR = PARITY_DIR.resolve("carts");
    private static final Path EXPECTED_DIR = PARITY_DIR.resolve("expected");
    private static final Path APP_PROPS = Path.of("src", "main", "resources", "application.properties");

    private static final String[] ALL_FIELDS = {
            "merchandiseTotal", "servicesAndFees", "discountTotal", "taxableSubtotal",
            "taxRate", "salesTax", "total", "savedToday", "taxExempt"};

    private static final Set<String> HALF_CENT_CARTS = Set.of(
            "44-penny-half-cent-discount-tee-x9",
            "45-penny-half-cent-discount-jean-x11",
            "46-penny-half-cent-discount-markdown-mix",
            "47-penny-half-cent-discount-and-tax",
            "49-penny-half-cent-tax-180");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @TestFactory
    Stream<DynamicTest> carts() throws IOException {
        assertTrue(Files.isDirectory(CARTS_DIR), "missing " + CARTS_DIR.toAbsolutePath());
        assertTrue(Files.isDirectory(EXPECTED_DIR), "missing " + EXPECTED_DIR.toAbsolutePath());

        Map<String, JsonNode> catalog = loadCatalog();
        PricingProperties props = bindPricingProperties();
        PricingEngine engine = new PricingEngine(PricingRoundingMode.LEGACY_POS);

        List<Path> cartFiles;
        try (Stream<Path> stream = Files.list(CARTS_DIR)) {
            cartFiles = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(cartFiles.size() >= 25, "expected >= 25 carts, found " + cartFiles.size());

        return cartFiles.stream().map(cartFile -> {
            try {
                JsonNode cart = mapper.readTree(cartFile.toFile());
                String cartId = cart.get("id").asText();
                JsonNode expected = mapper.readTree(EXPECTED_DIR.resolve(cartId + ".json").toFile()).get("totals");
                return DynamicTest.dynamicTest(cartId, () -> verifyCart(cartId, cart.get("request"), expected, catalog, props, engine));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void halfUpModeDivergesOnlyOnHalfCentTies() throws IOException {
        Map<String, JsonNode> catalog = loadCatalog();
        PricingProperties props = bindPricingProperties();
        PricingEngine engine = new PricingEngine(PricingRoundingMode.HALF_UP);

        List<Path> cartFiles;
        try (Stream<Path> stream = Files.list(CARTS_DIR)) {
            cartFiles = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        Set<String> diverged = new TreeSet<>();
        for (Path cartFile : cartFiles) {
            JsonNode cart = mapper.readTree(cartFile.toFile());
            String cartId = cart.get("id").asText();
            JsonNode expected = mapper.readTree(EXPECTED_DIR.resolve(cartId + ".json").toFile()).get("totals");
            OrderTotals actual = price(cart.get("request"), catalog, props, engine);
            boolean match = totalsMatch(expected, actual);
            if (!match) diverged.add(cartId);

            if (cartId.equals("44-penny-half-cent-discount-tee-x9")) {
                assertEquals(0, actual.discountTotal().compareTo(new BigDecimal("80.87")),
                        cartId + ": HALF_UP discountTotal");
                assertEquals(0, expected.get("discountTotal").decimalValue().compareTo(new BigDecimal("80.86")),
                        cartId + ": expected discountTotal");
            }
            if (cartId.equals("49-penny-half-cent-tax-180")) {
                assertEquals(0, actual.salesTax().compareTo(new BigDecimal("15.53")),
                        cartId + ": HALF_UP salesTax");
                assertEquals(0, expected.get("salesTax").decimalValue().compareTo(new BigDecimal("15.52")),
                        cartId + ": expected salesTax");
            }
        }
        assertEquals(new TreeSet<>(HALF_CENT_CARTS), diverged,
                "HALF_UP should diverge from expected only on the half-cent carts");
    }

    private void verifyCart(String cartId, JsonNode request, JsonNode expected,
                            Map<String, JsonNode> catalog, PricingProperties props,
                            PricingEngine engine) {
        OrderTotals actual = price(request, catalog, props, engine);
        for (String field : ALL_FIELDS) {
            if (field.equals("taxExempt")) {
                assertEquals(expected.get(field).asBoolean(), actual.taxExempt(), cartId + ".taxExempt");
            } else {
                assertEquals(0, expectedValue(expected, field)
                                .compareTo(actualValue(actual, field)),
                        cartId + "." + field + ": expected " + expected.get(field)
                                + " engine " + actualValue(actual, field));
            }
        }
    }

    private BigDecimal expectedValue(JsonNode expected, String field) {
        BigDecimal value = expected.get(field).decimalValue();
        return field.equals("taxRate") ? value : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal actualValue(OrderTotals actual, String field) {
        return field.equals("taxRate") ? actual.taxRate() : moneyField(actual, field);
    }

    /** Reports whether every OrderTotals field matches expected. */
    private boolean totalsMatch(JsonNode expected, OrderTotals actual) {
        boolean ok = true;
        for (String field : ALL_FIELDS) {
            if (field.equals("taxExempt")) {
                ok &= expected.get(field).asBoolean() == actual.taxExempt();
            } else {
                ok &= expectedValue(expected, field).compareTo(actualValue(actual, field)) == 0;
            }
        }
        return ok;
    }

    private OrderTotals price(JsonNode request, Map<String, JsonNode> catalog,
                              PricingProperties props, PricingEngine engine) {
        List<PricingLine> lines = new ArrayList<>();
        for (JsonNode item : request.get("lineItems")) {
            String sku = item.get("skuCode").asText();
            JsonNode product = catalog.get(sku);
            assertNotNull(product, "sku " + sku + " not in gap-catalog.json");
            lines.add(new PricingLine(
                    product.get("listPrice").decimalValue(),
                    product.get("salePrice").decimalValue(),
                    item.get("quantity").asInt(),
                    product.get("finalSale").asBoolean()));
        }
        List<PromotionRule> promos = resolvePromos(request.get("promotions"), props.getPromotions());
        String storeId = request.get("storeId").asText();
        BigDecimal taxRate = props.getTaxRates().get(storeId);
        assertNotNull(taxRate, "no tax rate for store " + storeId);
        return engine.calculate(lines, promos, props.getServicesAndFees(), taxRate,
                request.get("taxExempt").asBoolean());
    }

    private BigDecimal moneyField(OrderTotals t, String field) {
        return switch (field) {
            case "merchandiseTotal" -> t.merchandiseTotal();
            case "servicesAndFees" -> t.servicesAndFees();
            case "discountTotal" -> t.discountTotal();
            case "taxableSubtotal" -> t.taxableSubtotal();
            case "salesTax" -> t.salesTax();
            case "total" -> t.total();
            case "savedToday" -> t.savedToday();
            default -> throw new IllegalArgumentException(field);
        };
    }

    private List<PromotionRule> resolvePromos(JsonNode codes, Map<String, PricingProperties.Promo> configured) {
        if (codes == null) return List.of();
        Map<String, PromotionRule> resolved = new LinkedHashMap<>();
        for (JsonNode raw : codes) {
            String code = raw.asText().trim().toUpperCase();
            PricingProperties.Promo promo = configured.get(code);
            assertNotNull(promo, "unknown promotion code " + code);
            resolved.putIfAbsent(code, new PromotionRule(code, promo.getDescription(),
                    promo.getPercentOff(), promo.getAmountOff()));
        }
        return new ArrayList<>(resolved.values());
    }

    private Map<String, JsonNode> loadCatalog() throws IOException {
        JsonNode catalog = mapper.readTree(
                new org.springframework.core.io.ClassPathResource("gap-catalog.json").getInputStream());
        Map<String, JsonNode> bySku = new LinkedHashMap<>();
        catalog.forEach(p -> bySku.put(p.get("skuCode").asText(), p));
        return bySku;
    }

    private PricingProperties bindPricingProperties() throws IOException {
        List<PropertySource<?>> sources = new PropertiesPropertySourceLoader()
                .load("application", new FileSystemResource(APP_PROPS.toFile()));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        return binder.bind("pos.pricing", PricingProperties.class)
                .orElseThrow(() -> new IllegalStateException("pos.pricing not bound from " + APP_PROPS));
    }
}
