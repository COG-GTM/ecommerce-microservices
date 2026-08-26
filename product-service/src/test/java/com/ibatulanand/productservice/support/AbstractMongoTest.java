package com.ibatulanand.productservice.support;

import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for repository slice tests running against a real MongoDB. */
@DataMongoTest
@ActiveProfiles("test")
public abstract class AbstractMongoTest {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        MongoContainerSupport.registerProperties(registry);
    }
}
