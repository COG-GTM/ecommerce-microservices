package com.ibatulanand.productservice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.ibatulanand.productservice.model.Product;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class ProductServiceTest {
    @Inject
    MongoClient mongoClient;

    private MongoCollection<Document> collection;

    @BeforeEach
    void cleanCollection() {
        collection = mongoClient.getDatabase("product-service").getCollection("product");
        collection.deleteMany(new Document());
    }

    @Test
    void postReturnsCreatedAndGetReturnsNumericPriceAndObjectId() {
        String responseBody = given()
                .contentType("application/json")
                .body("""
                        {"name":"iPhone 15","description":"Apple iPhone 15","price":1200}
                        """)
                .when()
                .post("/api/product")
                .then()
                .statusCode(201)
                .body(is(emptyString()))
                .extract()
                .asString();
        assertEquals("", responseBody);

        String body = given()
                .when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertTrue(body.matches(
                "\\[\\{\\\"id\\\":\\\"[0-9a-f]{24}\\\",\\\"name\\\":\\\"iPhone 15\\\","
                        + "\\\"description\\\":\\\"Apple iPhone 15\\\",\\\"price\\\":1200\\}\\]"));
        assertTrue(!body.contains("\"price\":\"1200\""));
    }

    @Test
    void readsLegacySpringDocumentWithStringPriceAndClassHint() {
        ObjectId id = new ObjectId();
        collection.insertOne(new Document("_id", id)
                .append("name", "Frac")
                .append("description", "d")
                .append("price", "1200.55")
                .append("_class", "com.ibatulanand.productservice.model.Product"));

        String body = given()
                .when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertTrue(body.contains("\"id\":\"" + id.toHexString() + "\""));
        assertTrue(body.contains("\"name\":\"Frac\""));
        assertTrue(body.contains("\"description\":\"d\""));
        assertTrue(body.contains("\"price\":1200.55"));
        assertTrue(!body.contains("\"price\":\"1200.55\""));
    }

    @Test
    void toleratesUnknownJsonProperties() {
        given()
                .contentType("application/json")
                .body("""
                        {"name":"iPhone 15","description":"Apple iPhone 15","price":1200,"bogus":"ignored"}
                        """)
                .when()
                .post("/api/product")
                .then()
                .statusCode(201)
                .body(is(emptyString()));
    }

    @Test
    void writesBigDecimalAsStringAndIdAsObjectId() {
        given()
                .contentType("application/json")
                .body("""
                        {"name":"Frac","description":"d","price":1200.55}
                        """)
                .when()
                .post("/api/product")
                .then()
                .statusCode(201);

        Document stored = collection.find().first();
        assertInstanceOf(ObjectId.class, stored.get("_id"));
        assertInstanceOf(String.class, stored.get("price"));
        assertEquals("1200.55", stored.getString("price"));
    }

    @Test
    void healthIncludesMongoReadinessCheck() {
        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body(containsString("MongoDB connection health check"));
    }

    @Test
    void metricsReturnsPrometheusText() {
        given()
                .accept("text/plain; version=0.0.4")
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .contentType(containsString("text/plain"))
                .body(containsString("# TYPE"));
    }

    @Test
    void emptyCollectionReturnsEmptyArray() {
        given()
                .when()
                .get("/api/product")
                .then()
                .statusCode(200)
                .body(is("[]"));
    }
}
