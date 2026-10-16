package com.ibatulanand.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductIndexConfigTest {

    @Test
    void mongoUnavailableAtStartupDoesNotFailContext() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.indexOps(any(Class.class)))
                .thenThrow(new DataAccessResourceFailureException("Timed out waiting for a server"));
        ProductIndexConfig config = new ProductIndexConfig(mongoTemplate);

        assertFalse(config.tryEnsureIndexes());
        config.afterPropertiesSet();
    }
}
