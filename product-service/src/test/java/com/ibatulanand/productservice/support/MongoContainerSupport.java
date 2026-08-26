package com.ibatulanand.productservice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Single MongoDB container shared by every test in the module, started on first use and reused for
 * the lifetime of the JVM.
 */
public final class MongoContainerSupport {

    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:4.4.24");

    static {
        MONGO_DB.start();
    }

    private MongoContainerSupport() {
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB::getReplicaSetUrl);
    }
}
