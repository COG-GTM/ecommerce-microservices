package com.ibatulanand.tillservice.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibatulanand.tillservice.config.TillProperties;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.web.TillException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** WebClient adapters over the existing services, resolved through Eureka (lb://). */
@Component
@lombok.extern.slf4j.Slf4j
public class HttpServiceClients implements CatalogueClient, StockClient, OrderClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductDto(String barcode, String skuCode, String name, Integer pricePence, Boolean quickKey,
                      String vatCode, Boolean soldByWeight,
                      Integer minimumAge) {
        CatalogueItem toItem() {
            return new CatalogueItem(barcode, skuCode, name, pricePence == null ? 0 : pricePence,
                    Boolean.TRUE.equals(quickKey), vatCode, Boolean.TRUE.equals(soldByWeight),
                    minimumAge);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockDto(String skuCode, @JsonAlias("isInStock") boolean inStock) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderDto(String orderNumber) {
    }

    private final WebClient.Builder webClientBuilder;
    private final TillProperties properties;

    public HttpServiceClients(WebClient.Builder webClientBuilder, TillProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
    }

    @Override
    public Optional<CatalogueItem> findByBarcode(String barcode) {
        try {
            ProductDto dto = webClientBuilder.build().get()
                    .uri(properties.productServiceUrl() + "/api/product/barcode/{barcode}", barcode)
                    .retrieve().bodyToMono(ProductDto.class).block(TIMEOUT);
            return Optional.ofNullable(dto).map(ProductDto::toItem);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw unavailable("product-service", e);
        }
    }

    @Override
    public List<CatalogueItem> quickKeys() {
        try {
            ProductDto[] dtos = webClientBuilder.build().get()
                    .uri(properties.productServiceUrl() + "/api/product?quickkey=true")
                    .retrieve().bodyToMono(ProductDto[].class).block(TIMEOUT);
            return dtos == null ? List.of() : Arrays.stream(dtos).map(ProductDto::toItem).toList();
        } catch (RuntimeException e) {
            throw unavailable("product-service", e);
        }
    }

    @Override
    public boolean isInStock(String skuCode) {
        try {
            StockDto[] stock = webClientBuilder.build().get()
                    .uri(properties.inventoryServiceUrl() + "/api/inventory",
                            b -> b.queryParam("skuCode", skuCode).build())
                    .retrieve().bodyToMono(StockDto[].class).block(TIMEOUT);
            return stock != null && Arrays.stream(stock).anyMatch(s -> skuCode.equals(s.skuCode()) && s.inStock());
        } catch (RuntimeException e) {
            throw unavailable("inventory-service", e);
        }
    }

    @Override
    public String placeSale(Basket basket, String storeNumber, String tenderType) {
        List<Map<String, Object>> lines = basket.getLines().stream()
                .map(HttpServiceClients::saleLine)
                .toList();
        Map<String, Object> body = Map.of(
                "orderLineItemsDtoList", lines,
                "channel", "TILL",
                "storeNumber", storeNumber,
                "tillId", basket.getTillId(),
                "transactionId", basket.getTransactionNumber(),
                "tenderType", tenderType,
                "totalPence", basket.getTotalPence());
        try {
            OrderDto order = webClientBuilder.build().post()
                    .uri(properties.orderServiceUrl() + "/api/order/sale")
                    .bodyValue(body)
                    .retrieve().bodyToMono(OrderDto.class).block(TIMEOUT);
            if (order == null || order.orderNumber() == null) {
                throw new TillException(HttpStatus.BAD_GATEWAY, "ORDER_SERVICE_ERROR", "order-service returned no order number");
            }
            return order.orderNumber();
        } catch (WebClientResponseException.Conflict e) {
            throw new TillException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "One or more items are out of stock");
        } catch (TillException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("order-service", e);
        }
    }

    private static Map<String, Object> saleLine(BasketLine line) {
        Map<String, Object> body = new HashMap<>();
        body.put("skuCode", line.getSkuCode());
        body.put("price", BigDecimal.valueOf(line.getPricedBy() == BasketLine.PricedBy.EACH
                ? line.getUnitPricePence() : line.getLinePence() / Math.max(1, line.getQuantity()), 2));
        body.put("quantity", line.getQuantity());
        if (line.getMinimumAge() != null) {
            body.put("minimumAge", line.getMinimumAge());
            body.put("ageApprovedBy", line.getAgeApprovedBy());
        }
        return body;
    }

    private static TillException unavailable(String service, RuntimeException cause) {
        log.warn("{} call failed", service, cause);
        return new TillException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE", service + " is unavailable");
    }
}
