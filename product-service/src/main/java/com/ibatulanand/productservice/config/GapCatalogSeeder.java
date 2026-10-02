package com.ibatulanand.productservice.config;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "product.seed.gap-catalog", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GapCatalogSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        for (Product product : gapProducts()) {
            if (productRepository.findBySkuCode(product.getSkuCode()).isEmpty()) {
                productRepository.save(product);
                log.info("Gap catalog product {} is saved", product.getSkuCode());
            }
        }
    }

    private List<Product> gapProducts() {
        return List.of(
                Product.builder()
                        .styleId("268341")
                        .skuCode("268341-016-L")
                        .name("Vintage Soft Crewneck Tee")
                        .description("Vintage Soft Crewneck Tee")
                        .department("WOMEN'S")
                        .category("TOPS")
                        .colorName("Heather Grey")
                        .colorCode("#9b9ea3")
                        .size("L")
                        .price(new BigDecimal("17.97"))
                        .listPrice(new BigDecimal("29.95"))
                        .salePrice(new BigDecimal("17.97"))
                        .clearancePercent(40)
                        .finalSale(true)
                        .build(),
                Product.builder()
                        .styleId("471902")
                        .skuCode("471902-004-29")
                        .name("High Rise Straight Jean")
                        .description("High Rise Straight Jean")
                        .department("WOMEN'S")
                        .category("DENIM")
                        .colorName("Medium Indigo")
                        .colorCode("#41597d")
                        .size("29 Reg")
                        .price(new BigDecimal("69.95"))
                        .listPrice(new BigDecimal("69.95"))
                        .salePrice(new BigDecimal("69.95"))
                        .clearancePercent(0)
                        .finalSale(false)
                        .build(),
                Product.builder()
                        .styleId("512884")
                        .skuCode("512884-022-M")
                        .name("Logo Fleece Hoodie")
                        .description("Logo Fleece Hoodie")
                        .department("MEN'S")
                        .category("FLEECE")
                        .colorName("Navy Uniform")
                        .colorCode("#1f2a44")
                        .size("M")
                        .price(new BigDecimal("41.97"))
                        .listPrice(new BigDecimal("59.95"))
                        .salePrice(new BigDecimal("41.97"))
                        .clearancePercent(30)
                        .finalSale(false)
                        .build()
        );
    }
}
