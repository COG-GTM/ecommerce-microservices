package com.ibatulanand.orderservice.parity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.orderservice.config.PricingProperties;
import com.ibatulanand.orderservice.pricing.OrderTotals;
import com.ibatulanand.orderservice.pricing.PricingEngine;
import com.ibatulanand.orderservice.pricing.PricingLine;
import com.ibatulanand.orderservice.pricing.PromotionRule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden parity: every cart in ../parity/carts is priced by the real
 * PricingEngine + PricingProperties (bound from the real application.properties)
 * and compared against ../parity/expected, honoring known-divergences.json.
 */
class GoldenParityTest {

    private static final Path PARITY_DIR = Path.of("..", "parity");
    private static final Path CARTS_DIR = PARITY_DIR.resolve("carts");
    private static final Path EXPECTED_DIR = PARITY_DIR.resolve("expected");
    private static final Path DIVERGENCES = PARITY_DIR.resolve("known-divergences.json");
    private static final Path APP_PROPS = Path.of("src", "main", "resources", "application.properties");

    private static final String[] MONEY_FIELDS = {
            "merchandiseTotal", "servicesAndFees", "discountTotal", "taxableSubtotal",
            "salesTax", "total", "savedToday"};
    private static final String[] ALL_FIELDS = {
            "merchandiseTotal", "servicesAndFees", "discountTotal", "taxableSubtotal",
            "taxRate", "salesTax", "total", "savedToday", "taxExempt"};

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final Set<String> matchedDivergences = ConcurrentHashMap.newKeySet();

    @TestFactory
    Stream<DynamicTest> carts() throws IOException {
        assertTrue(Files.isDirectory(CARTS_DIR), "missing " + CARTS_DIR.toAbsolutePath());
        assertTrue(Files.isDirectory(EXPECTED_DIR), "missing " + EXPECTED_DIR.toAbsolutePath());
        assertTrue(Files.isRegularFile(DIVERGENCES), "missing " + DIVERGENCES.toAbsolutePath());

        Map<String, JsonNode> catalog = loadCatalog();
        PricingProperties props = bindPricingProperties();
        Map<String, Map<String, JsonNode>> divergences = loadDivergences();

        List<Path> cartFiles;
        try (Stream<Path> stream = Files.list(CARTS_DIR)) {
            cartFiles = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(cartFiles.size() >= 25, "expected >= 25 carts, found " + cartFiles.size());

        List<DynamicTest> tests = new ArrayList<>();
        for (Path cartFile : cartFiles) {
            JsonNode cart = mapper.readTree(cartFile.toFile());
            String cartId = cart.get("id").asText();
            JsonNode request = cart.get("request");
            JsonNode expected = mapper.readTree(EXPECTED_DIR.resolve(cartId + ".json").toFile()).get("totals");
            tests.add(DynamicTest.dynamicTest(cartId, () -> verifyCart(cartId, request, expected, catalog, props, divergences)));
        }
        tests.add(DynamicTest.dynamicTest("no-stale-known-divergences", () -> {
            List<String> unmatched = divergenceKeys(divergences).stream()
                    .filter(k -> !matchedDivergences.contains(k)).toList();
            assertTrue(unmatched.isEmpty(), "stale known-divergences entries: " + unmatched);
        }));
        return tests.stream();
    }

    private Set<String> divergenceKeys(Map<String, Map<String, JsonNode>> divergences) {
        Set<String> keys = new TreeSet<>();
        divergences.forEach((cartId, fields) -> fields.keySet().forEach(f -> keys.add(cartId + "|" + f)));
        return keys;
    }

    private void verifyCart(String cartId, JsonNode request, JsonNode expected,
                            Map<String, JsonNode> catalog, PricingProperties props,
                            Map<String, Map<String, JsonNode>> divergences) {
        List<PricingLine> lines = new ArrayList<>();
        for (JsonNode item : request.get("lineItems")) {
            String sku = item.get("skuCode").asText();
            JsonNode product = catalog.get(sku);
            assertNotNull(product, cartId + ": sku " + sku + " not in gap-catalog.json");
            lines.add(new PricingLine(
                    product.get("listPrice").decimalValue(),
                    product.get("salePrice").decimalValue(),
                    item.get("quantity").asInt(),
                    product.get("finalSale").asBoolean()));
        }

        List<PromotionRule> promos = resolvePromos(request.get("promotions"), props.getPromotions());
        String storeId = request.get("storeId").asText();
        BigDecimal taxRate = props.getTaxRates().get(storeId);
        assertNotNull(taxRate, cartId + ": no tax rate for store " + storeId);

        OrderTotals actual = new PricingEngine().calculate(
                lines, promos, props.getServicesAndFees(), taxRate, request.get("taxExempt").asBoolean());

        Map<String, JsonNode> known = divergences.getOrDefault(cartId, Map.of());
        for (String field : ALL_FIELDS) {
            if (known.containsKey(field)) {
                JsonNode entry = known.get(field);
                BigDecimal old = entry.get("old").decimalValue();
                BigDecimal expectedNew = entry.get("new").decimalValue();
                matchedDivergences.add(cartId + "|" + field);
                assertEquals(0, expected.get(field).decimalValue().compareTo(old),
                        cartId + "." + field + ": expected != divergence old");
                assertEquals(0, moneyField(actual, field).compareTo(expectedNew),
                        cartId + "." + field + ": engine != divergence new");
            } else if (field.equals("taxExempt")) {
                assertEquals(expected.get(field).asBoolean(), actual.taxExempt(), cartId + ".taxExempt");
            } else if (field.equals("taxRate")) {
                assertEquals(0, expected.get(field).decimalValue().compareTo(actual.taxRate()),
                        cartId + ".taxRate");
            } else {
                assertEquals(0, expected.get(field).decimalValue()
                                .setScale(2, java.math.RoundingMode.HALF_UP)
                                .compareTo(moneyField(actual, field)),
                        cartId + "." + field + ": expected " + expected.get(field)
                                + " engine " + moneyField(actual, field));
            }
        }
    }

    private BigDecimal moneyField(OrderTotals t, String field) {
        return switch (field) {
            case "merchandiseTotal" -> t.merchandiseTotal();
            case "servicesAndFees" -> t.servicesAndFees();
            case "discountTotal" -> t.discountTotal();
            case "taxableSubtotal" -> t.taxableSubtotal();
            case "taxRate" -> t.taxRate();
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

    private Map<String, Map<String, JsonNode>> loadDivergences() throws IOException {
        Map<String, Map<String, JsonNode>> map = new LinkedHashMap<>();
        for (JsonNode entry : mapper.readTree(DIVERGENCES.toFile())) {
            map.computeIfAbsent(entry.get("cartId").asText(), k -> new LinkedHashMap<>())
                    .put(entry.get("field").asText(), entry);
        }
        return map;
    }
}
