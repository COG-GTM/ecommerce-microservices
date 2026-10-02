package com.ibatulanand.orderservice.client;

import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.exception.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProductClientTest {

    private static ProductClient clientResponding(HttpStatus status, String body) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(status)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build()));
        return new ProductClient(builder);
    }

    private static final String PRODUCT_JSON = """
            {"skuCode":"471902-004-29","styleId":"471902","name":"Slim Jean",
             "description":"Slim Jean","department":"MEN'S","category":"BOTTOMS",
             "colorName":"Medium Wash","colorCode":"#5f7390","size":"29",
             "listPrice":59.95,"salePrice":41.97,"clearancePercent":30,"finalSale":false,
             "price":41.97,"variants":[]}
            """;

    private static final String NOT_FOUND_JSON = """
            {"status":404,"error":"Not Found","message":"Unknown skuCode: NO_SUCH_SKU"}
            """;

    @Test
    void returnsProductOn200() {
        Optional<ProductResponse> result =
                clientResponding(HttpStatus.OK, PRODUCT_JSON).findBySku("471902-004-29");
        assertTrue(result.isPresent());
        assertEquals(0, result.get().getListPrice().compareTo(new BigDecimal("59.95")));
        assertEquals(0, result.get().getSalePrice().compareTo(new BigDecimal("41.97")));
        assertFalse(result.get().effectiveFinalSale());
    }

    @Test
    void returnsEmptyOn404WithJsonBody() {
        Optional<ProductResponse> result =
                clientResponding(HttpStatus.NOT_FOUND, NOT_FOUND_JSON).findBySku("NO_SUCH_SKU");
        assertTrue(result.isEmpty());
    }

    @Test
    void throws503On500() {
        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> clientResponding(HttpStatus.INTERNAL_SERVER_ERROR, "{}").findBySku("X"));
        assertEquals("Product service unavailable", ex.getMessage());
    }
}
