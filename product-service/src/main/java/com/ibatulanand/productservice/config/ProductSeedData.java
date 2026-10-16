package com.ibatulanand.productservice.config;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.model.ProductVariant;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Seeds the PRE-1 golden SKUs, copied from {@code pos-webapp/src/api/fixtures.ts#MOCK_PRODUCTS}. */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class ProductSeedData implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        for (Product golden : goldenProducts()) {
            productRepository.findByStyleId(golden.getStyleId()).ifPresent(existing -> golden.setId(existing.getId()));
            productRepository.save(golden);
            log.info("Seeded golden style {} ({})", golden.getStyleId(), golden.getVariants().get(0).getSkuCode());
        }
    }

    public static List<Product> goldenProducts() {
        return List.of(
                style("268341", "Vintage Soft Crewneck Tee", "WOMEN'S", "TOPS",
                        variant("268341-016-L", "Heather Grey", "#9b9ea3", "L", "29.95", "17.97", 40, true)),
                style("471902", "High Rise Straight Jean", "WOMEN'S", "DENIM",
                        variant("471902-004-29", "Medium Indigo", "#41597d", "29 Reg", "69.95", "69.95", 0, false)),
                style("512884", "Logo Fleece Hoodie", "MEN'S", "FLEECE",
                        variant("512884-022-M", "Navy Uniform", "#1f2a44", "M", "59.95", "41.97", 30, false)));
    }

    private static Product style(String styleId, String name, String department, String category,
                                 ProductVariant variant) {
        return Product.builder()
                .styleId(styleId)
                .name(name)
                .description(name)
                .department(department)
                .category(category)
                .price(variant.getListPrice())
                .variants(List.of(variant))
                .build();
    }

    private static ProductVariant variant(String skuCode, String colorName, String colorCode, String size,
                                          String listPrice, String salePrice, int clearancePercent,
                                          boolean finalSale) {
        return ProductVariant.builder()
                .skuCode(skuCode)
                .colorName(colorName)
                .colorCode(colorCode)
                .size(size)
                .listPrice(new BigDecimal(listPrice))
                .salePrice(new BigDecimal(salePrice))
                .clearancePercent(clearancePercent)
                .finalSale(finalSale)
                .build();
    }
}
