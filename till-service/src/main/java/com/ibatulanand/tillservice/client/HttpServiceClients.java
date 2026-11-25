package com.ibatulanand.tillservice.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ibatulanand.tillservice.config.TillProperties;
import com.ibatulanand.tillservice.domain.Basket;
import com.ibatulanand.tillservice.domain.BasketLine;
import com.ibatulanand.tillservice.domain.CatalogueItem;
import com.ibatulanand.tillservice.web.RefundDtos.RefundLineRequest;
import com.ibatulanand.tillservice.web.RefundDtos.RefundOrder;
import com.ibatulanand.tillservice.web.RefundDtos.RefundableSale;
import com.ibatulanand.tillservice.pricing.VatRate;
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
                    Boolean.TRUE.equals(quickKey), VatRate.of(vatCode).name(), Boolean.TRUE.equals(soldByWeight),
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
        return placeSale(basket, storeNumber, tenderType, null);
    }

    @Override
    public String placeSale(Basket basket, String storeNumber, String tenderType, String idempotencyKey) {
        List<Map<String, Object>> lines = basket.getLines().stream()
                .map(HttpServiceClients::saleLine)
                .toList();
        List<Map<String, Object>> tenders = basket.getTenders().stream()
                .map(t -> {
                    Map<String, Object> tender = new java.util.HashMap<>();
                    tender.put("tenderType", t.tenderType());
                    tender.put("amountPence", t.amountPence());
                    tender.put("tenderedPence", t.tenderedPence());
                    tender.put("changeDuePence", t.changeDuePence());
                    tender.put("authCode", t.authCode());
                    return tender;
                })
                .toList();
        Map<String, Object> body = new HashMap<>(Map.of(
                "orderLineItemsDtoList", lines,
                "tenders", tenders,
                "channel", "TILL",
                "storeNumber", storeNumber,
                "tillId", basket.getTillId(),
                "transactionId", basket.getTransactionNumber(),
                "tenderType", tenderType,
                "totalPence", basket.getTotalPence()));
        // An explicit key (a replayed offline sale id) wins; otherwise one sale per basket. order-service returns the
        // existing order when a request with the same key is repeated.
        body.put("idempotencyKey", idempotencyKey != null ? idempotencyKey : basket.getBasketId());
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorDto(String error, String message) {
    }

    @Override
    public RefundableSale refundable(String reference) {
        try {
            RefundableSale sale = webClientBuilder.build().get()
                    .uri(properties.orderServiceUrl() + "/api/order/refundable/{reference}", reference)
                    .retrieve().bodyToMono(RefundableSale.class).block(TIMEOUT);
            if (sale == null) {
                throw new TillException(HttpStatus.BAD_GATEWAY, "ORDER_SERVICE_ERROR", "order-service returned no sale");
            }
            return sale;
        } catch (WebClientResponseException e) {
            throw refundRejected(e);
        } catch (TillException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("order-service", e);
        }
    }

    @Override
    public RefundOrder refund(String reference, String storeNumber, String tillId, String transactionId,
                              List<RefundLineRequest> lines) {
        Map<String, Object> body = Map.of(
                "storeNumber", storeNumber,
                "tillId", tillId,
                "transactionId", transactionId,
                "lines", lines);
        try {
            RefundOrder refund = webClientBuilder.build().post()
                    .uri(properties.orderServiceUrl() + "/api/order/{reference}/refunds", reference)
                    .bodyValue(body)
                    .retrieve().bodyToMono(RefundOrder.class).block(TIMEOUT);
            if (refund == null || refund.orderNumber() == null) {
                throw new TillException(HttpStatus.BAD_GATEWAY, "ORDER_SERVICE_ERROR", "order-service returned no refund");
            }
            return refund;
        } catch (WebClientResponseException e) {
            throw refundRejected(e);
        } catch (TillException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("order-service", e);
        }
    }

    /** Passes order-service's refund rejections (404/409/400/422) through with their error code and message. */
    private static TillException refundRejected(WebClientResponseException e) {
        if (e.getStatusCode().is5xxServerError()) {
            return unavailable("order-service", e);
        }
        ErrorDto error = null;
        try {
            error = e.getResponseBodyAs(ErrorDto.class);
        } catch (RuntimeException ignored) {
            // fall through to a generic rejection
        }
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return new TillException(status == null ? HttpStatus.BAD_REQUEST : status,
                error != null && error.error() != null ? error.error() : "REFUND_REJECTED",
                error != null && error.message() != null ? error.message() : "order-service rejected the refund");
    }

    private static TillException unavailable(String service, RuntimeException cause) {
        log.warn("{} call failed", service, cause);
        return new TillException(HttpStatus.SERVICE_UNAVAILABLE, "BACKEND_UNAVAILABLE", service + " is unavailable");
    }
}
