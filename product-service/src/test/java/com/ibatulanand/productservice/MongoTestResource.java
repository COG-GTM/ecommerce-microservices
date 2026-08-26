package com.ibatulanand.productservice;

import java.util.Map;

import org.testcontainers.containers.MongoDBContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MongoTestResource implements QuarkusTestResourceLifecycleManager {
    private MongoDBContainer mongo;

    @Override
    public Map<String, String> start() {
        mongo = new MongoDBContainer("mongo:4.4.24");
        mongo.start();
        return Map.of("quarkus.mongodb.connection-string", mongo.getReplicaSetUrl());
    }

    @Override
    public void stop() {
        if (mongo != null) {
            mongo.stop();
        }
    }
}
