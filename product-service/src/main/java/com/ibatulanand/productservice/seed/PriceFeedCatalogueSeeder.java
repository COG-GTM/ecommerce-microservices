package com.ibatulanand.productservice.seed;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Seeds non-quick-key items (barcodes 394101-394103) whose prices the price-feed E2E specs change, so the shared
 * 1001-1008 prices other specs assert never move. Idempotent: upserts by barcode.
 */
@Component
@ConditionalOnProperty(name = "catalogue.seed.grocery", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PriceFeedCatalogueSeeder implements ApplicationRunner {

    static final List<GroceryCatalogueSeeder.SeedItem> ITEMS = List.of(
            new GroceryCatalogueSeeder.SeedItem("394101", "MOR-TEA-BAGS-80", "Tea bags x80", 250, "Z", false),
            new GroceryCatalogueSeeder.SeedItem("394102", "MOR-COFFEE-INST-200", "Instant coffee 200g", 425, "Z", false),
            new GroceryCatalogueSeeder.SeedItem("394103", "MOR-DIGESTIVE-400", "Digestive biscuits 400g", 99, "Z", false));

    private final ProductRepository productRepository;

    /** Every seeded till item's price in pence, by barcode. */
    public static Map<String, Integer> seededPrices() {
        Map<String, Integer> prices = new LinkedHashMap<>();
        Stream.concat(GroceryCatalogueSeeder.ITEMS.stream(), ITEMS.stream())
                .forEach(i -> prices.put(i.barcode(), i.pricePence()));
        return prices;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (GroceryCatalogueSeeder.SeedItem item : ITEMS) {
            Product product = productRepository.findFirstByBarcode(item.barcode()).orElseGet(Product::new);
            if (product.getPricePence() == null) {
                product.setPricePence(item.pricePence());
                product.setPrice(BigDecimal.valueOf(item.pricePence(), 2));
            }
            product.setBarcode(item.barcode());
            product.setSkuCode(item.skuCode());
            product.setName(item.name());
            product.setDescription(item.name());
            product.setQuickKey(false);
            product.setVatCode(item.vatCode());
            productRepository.save(product);
        }
        log.info("Seeded {} price-feed products for the till", ITEMS.size());
    }
}
