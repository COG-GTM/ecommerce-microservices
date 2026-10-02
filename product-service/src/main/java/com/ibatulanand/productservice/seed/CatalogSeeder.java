package com.ibatulanand.productservice.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.seed.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CatalogSeeder implements ApplicationRunner {

    private static final String SEED_RESOURCE = "seed/gap-catalog.json";

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seed();
    }

    public int seed() throws IOException {
        List<CatalogSeedItem> items;
        try (InputStream inputStream = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            items = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }

        for (CatalogSeedItem item : items) {
            Product product = productRepository.findBySkuCode(item.getSkuCode())
                    .orElseGet(Product::new);
            product.setStyleId(item.getStyleId());
            product.setSkuCode(item.getSkuCode());
            product.setName(item.getName());
            product.setDescription(item.getDescription());
            product.setDepartment(item.getDepartment());
            product.setCategory(item.getCategory());
            product.setColorName(item.getColorName());
            product.setColorCode(item.getColorCode());
            product.setSize(item.getSize());
            product.setListPrice(item.getListPrice());
            product.setSalePrice(item.getSalePrice());
            product.setClearancePercent(item.getClearancePercent());
            product.setFinalSale(item.getFinalSale());
            product.setPrice(item.getSalePrice());
            productRepository.save(product);
        }

        log.info("Catalog seeded with {} products", items.size());
        return items.size();
    }
}
