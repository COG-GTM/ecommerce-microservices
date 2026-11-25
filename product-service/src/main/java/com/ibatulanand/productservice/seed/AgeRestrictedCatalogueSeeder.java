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
import java.util.List;

/**
 * Seeds age-restricted till products (barcodes 3001-3002, scan only, not quick keys). Idempotent: upserts by barcode.
 * Enabled with catalogue.seed.grocery=true alongside {@link GroceryCatalogueSeeder}.
 */
@Component
@ConditionalOnProperty(name = "catalogue.seed.grocery", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AgeRestrictedCatalogueSeeder implements ApplicationRunner {

    record SeedItem(String barcode, String skuCode, String name, int pricePence, int minimumAge) {
    }

    static final List<SeedItem> ITEMS = List.of(
            new SeedItem("3001", "MOR-LAGER-4X440", "Lager 4 x 440ml", 450, 18),
            new SeedItem("3002", "MOR-WINE-RED-75CL", "Red wine 75cl", 700, 18));

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (SeedItem item : ITEMS) {
            Product product = productRepository.findFirstByBarcode(item.barcode()).orElseGet(Product::new);
            product.setBarcode(item.barcode());
            product.setSkuCode(item.skuCode());
            product.setName(item.name());
            product.setDescription(item.name());
            product.setPricePence(item.pricePence());
            product.setPrice(BigDecimal.valueOf(item.pricePence(), 2));
            product.setQuickKey(false);
            product.setMinimumAge(item.minimumAge());
            productRepository.save(product);
        }
        log.info("Seeded {} age-restricted products for the till", ITEMS.size());
    }
}
