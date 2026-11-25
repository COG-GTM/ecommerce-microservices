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
 * Seeds the products used by the till multi-buy and meal-deal promotions (MBA-3934, barcodes 393401-393407).
 * Not quick keys: they are scanned. Idempotent: upserts by barcode. Enabled with catalogue.seed.grocery=true.
 */
@Component
@ConditionalOnProperty(name = "catalogue.seed.grocery", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PromotionCatalogueSeeder implements ApplicationRunner {

    record SeedItem(String barcode, String skuCode, String name, int pricePence) {
    }

    static final List<SeedItem> ITEMS = List.of(
            new SeedItem("393401", "MOR-SANDWICH-CHKBAC", "Chicken and bacon sandwich", 300),
            new SeedItem("393402", "MOR-SANDWICH-EGGMAYO", "Egg mayo sandwich", 250),
            new SeedItem("393403", "MOR-CRISPS-SALTED-30G", "Ready salted crisps 30g", 85),
            new SeedItem("393404", "MOR-FRUIT-POT", "Fruit pot", 120),
            new SeedItem("393405", "MOR-WATER-STILL-500", "Still water 500ml", 90),
            new SeedItem("393406", "MOR-COLA-500", "Cola 500ml", 135),
            new SeedItem("393407", "MOR-YOGHURT-GREEK-500", "Greek style yoghurt 500g", 150));

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
            productRepository.save(product);
        }
        log.info("Seeded {} promotion products for the till", ITEMS.size());
    }
}
