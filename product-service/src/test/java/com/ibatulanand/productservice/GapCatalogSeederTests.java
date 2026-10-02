package com.ibatulanand.productservice;

import com.ibatulanand.productservice.config.GapCatalogSeeder;
import com.ibatulanand.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "product.seed.gap-catalog=true")
@Testcontainers
class GapCatalogSeederTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.24");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GapCatalogSeeder gapCatalogSeeder;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Test
    void shouldSeedGapCatalogOnceAndRemainIdempotent() throws Exception {
        String[] skuCodes = {"268341-016-L", "471902-004-29", "512884-022-M"};

        for (String skuCode : skuCodes) {
            Assertions.assertEquals(1, productRepository.findBySkuCode(skuCode).size());
        }

        gapCatalogSeeder.run();

        for (String skuCode : skuCodes) {
            Assertions.assertEquals(1, productRepository.findBySkuCode(skuCode).size());
        }
    }
}
