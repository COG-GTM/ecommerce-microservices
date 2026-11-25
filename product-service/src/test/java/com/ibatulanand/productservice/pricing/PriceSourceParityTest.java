package com.ibatulanand.productservice.pricing;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import com.ibatulanand.productservice.seed.PriceFeedCatalogueSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parity: the legacy price file and the price feed produce identical prices for every seeded till item, so flipping
 * {@code pricing.source} never changes a price on the till.
 */
@SpringBootTest(properties = {"catalogue.seed.grocery=true", "pricing.source=legacy-file", "eureka.client.enabled=false"})
@Testcontainers
@AutoConfigureMockMvc
class PriceSourceParityTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:4.4.24");

    @DynamicPropertySource
    static void mongoUri(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private LegacyPriceFileImporter legacyImporter;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void legacyFileAndPriceFeedProduceIdenticalPricesForEverySeededItem() {
        Map<String, Integer> seeded = PriceFeedCatalogueSeeder.seededPrices();

        scramblePrices();
        PriceFeedService.ImportResult imported = legacyImporter.importNow();
        assertThat(imported.unknownBarcodes()).isEmpty();
        Map<String, Integer> fromLegacyFile = catalogueprices();

        scramblePrices();
        PriceFeedService feed = new PriceFeedService(productRepository,
                new PricingProperties(PriceSource.PRICE_FEED, null));
        feed.applyFeed(seeded.entrySet().stream()
                .map(e -> new PriceFeedService.PriceUpdate(e.getKey(), e.getValue())).toList());
        Map<String, Integer> fromPriceFeed = catalogueprices();

        assertThat(fromLegacyFile.keySet()).containsExactlyInAnyOrderElementsOf(seeded.keySet());
        assertThat(fromLegacyFile).isEqualTo(fromPriceFeed).isEqualTo(new TreeMap<>(seeded));
    }

    @Test
    void feedWritesAreRejectedWhileTheLegacyFileIsTheSource() throws Exception {
        mockMvc.perform(put("/api/product/price-feed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prices\":[{\"barcode\":\"394101\",\"pricePence\":300}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PRICE_FEED_DISABLED"));
        mockMvc.perform(post("/api/product/price-feed/legacy-import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(PriceFeedCatalogueSeeder.seededPrices().size()));
    }

    private void scramblePrices() {
        for (Product product : productRepository.findAll()) {
            product.setPricePence(1);
            productRepository.save(product);
        }
    }

    private Map<String, Integer> catalogueprices() {
        Map<String, Integer> prices = new TreeMap<>();
        productRepository.findAll().stream()
                .filter(p -> PriceFeedCatalogueSeeder.seededPrices().containsKey(p.getBarcode()))
                .forEach(p -> prices.put(p.getBarcode(), p.getPricePence()));
        return prices;
    }
}
