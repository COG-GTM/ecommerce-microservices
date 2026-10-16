package com.ibatulanand.productservice.config;

import com.ibatulanand.productservice.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

/** Ensures the indexes declared on {@link Product} (incl. the unique {@code variants.skuCode}) exist. */
@Configuration
@RequiredArgsConstructor
public class ProductIndexConfig implements InitializingBean {

    private final MongoTemplate mongoTemplate;

    @Override
    public void afterPropertiesSet() {
        IndexOperations indexOps = mongoTemplate.indexOps(Product.class);
        new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext())
                .resolveIndexFor(Product.class)
                .forEach(indexOps::ensureIndex);
    }
}
