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
 * Seeds the Morrisons till demo grocery catalogue (barcodes 1001-1008, weighed PLUs 2101-2299). Idempotent: upserts by barcode.
 * Enabled with catalogue.seed.grocery=true (set by scripts/till/up.sh).
 */
@Component
@ConditionalOnProperty(name = "catalogue.seed.grocery", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GroceryCatalogueSeeder implements ApplicationRunner {

    record SeedItem(String barcode, String skuCode, String name, int pricePence) {
    }

    static final List<SeedItem> ITEMS = List.of(
            new SeedItem("1001", "MOR-MILK-SS-4PT", "Semi skimmed milk 4 pints", 155),
            new SeedItem("1002", "MOR-BREAD-WM-800", "Wholemeal bread 800g", 110),
            new SeedItem("1003", "MOR-EGGS-FR-6", "Free range eggs x6", 185),
            new SeedItem("1004", "MOR-BANANA-5", "Bananas x5", 95),
            new SeedItem("1005", "MOR-OJ-1L", "Orange juice 1L", 160),
            new SeedItem("1006", "MOR-CHEDDAR-MAT-400", "Mature cheddar 400g", 350),
            new SeedItem("1007", "MOR-CHICKEN-BF-600", "Chicken breast fillets 600g", 475),
            new SeedItem("1008", "MOR-CARROT-1KG", "Carrots 1kg", 65));

    /**
     * Loose and counter items sold by weight (PLUs 2101-2299, price per kg, not quick keys). Keyed by PLU they prompt
     * for a weight; counter labels carry the PLU and price in a prefix-2 EAN-13 (e.g. 2002101003490 = PLU 2101, £3.49).
     */
    static final List<SeedItem> WEIGHED_ITEMS = List.of(
            new SeedItem("2101", "MOR-DELI-HAM-KG", "Deli honey roast ham", 1290),
            new SeedItem("2201", "MOR-TOMATO-LOOSE-KG", "Loose tomatoes", 220),
            new SeedItem("2202", "MOR-ONION-LOOSE-KG", "Loose brown onions", 99));

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
            product.setQuickKey(true);
            productRepository.save(product);
        }
        for (SeedItem item : WEIGHED_ITEMS) {
            Product product = productRepository.findFirstByBarcode(item.barcode()).orElseGet(Product::new);
            product.setBarcode(item.barcode());
            product.setSkuCode(item.skuCode());
            product.setName(item.name());
            product.setDescription(item.name() + " (per kg)");
            product.setPricePence(item.pricePence());
            product.setPrice(BigDecimal.valueOf(item.pricePence(), 2));
            product.setQuickKey(false);
            product.setSoldByWeight(true);
            productRepository.save(product);
        }
        log.info("Seeded {} grocery and {} weighed products for the till", ITEMS.size(), WEIGHED_ITEMS.size());
    }
}
