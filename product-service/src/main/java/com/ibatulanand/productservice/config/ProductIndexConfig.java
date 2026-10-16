package com.ibatulanand.productservice.config;

import com.ibatulanand.productservice.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

import java.time.Duration;

/**
 * Ensures the indexes declared on {@link Product} (unique {@code variants.skuCode} and {@code styleId}) exist.
 * If Mongo isn't reachable yet, startup continues and creation is retried in the background.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ProductIndexConfig implements InitializingBean {

    static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final MongoTemplate mongoTemplate;

    @Override
    public void afterPropertiesSet() {
        if (!tryEnsureIndexes()) {
            Thread retry = new Thread(this::retryUntilCreated, "product-index-init");
            retry.setDaemon(true);
            retry.start();
        }
    }

    boolean tryEnsureIndexes() {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(Product.class);
            new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext())
                    .resolveIndexFor(Product.class)
                    .forEach(indexOps::ensureIndex);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not ensure product indexes yet ({}); retrying in {}s",
                    e.getMessage(), RETRY_DELAY.toSeconds());
            return false;
        }
    }

    private void retryUntilCreated() {
        do {
            try {
                Thread.sleep(RETRY_DELAY.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        } while (!tryEnsureIndexes());
        log.info("Product indexes ensured");
    }
}
