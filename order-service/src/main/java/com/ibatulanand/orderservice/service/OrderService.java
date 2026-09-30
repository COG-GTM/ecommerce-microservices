package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.ProductResponse;
import com.ibatulanand.orderservice.dto.StockReservationError;
import com.ibatulanand.orderservice.dto.StockReservationItem;
import com.ibatulanand.orderservice.dto.StockReservationRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.exception.OrderRejectedException;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        List<OrderLineItemsDto> requestedLines = orderRequest.getOrderLineItemsDtoList();
        Map<String, Integer> quantityBySku = aggregateQuantities(requestedLines);

        Map<String, BigDecimal> unitPriceBySku = fetchCatalogPrices(quantityBySku.keySet());
        List<String> unknownSkus = quantityBySku.keySet().stream()
                .filter(skuCode -> !unitPriceBySku.containsKey(skuCode))
                .toList();
        if (!unknownSkus.isEmpty()) {
            throw new OrderRejectedException("Unknown product(s): " + String.join(", ", unknownSkus));
        }

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setOrderLineItemsList(requestedLines.stream()
                .map(line -> toOrderLineItem(line, unitPriceBySku.get(line.getSkuCode())))
                .toList());

        List<StockReservationItem> reservation = quantityBySku.entrySet().stream()
                .map(entry -> new StockReservationItem(entry.getKey(), entry.getValue()))
                .toList();
        reserveStock(reservation);

        try {
            orderRepository.saveAndFlush(order);
        } catch (RuntimeException e) {
            releaseStock(reservation);
            throw e;
        }

        // Send order to the kafka topic
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

        return "Order Placed Successfully!";
    }

    private Map<String, Integer> aggregateQuantities(List<OrderLineItemsDto> lines) {
        Map<String, Integer> quantityBySku = new LinkedHashMap<>();
        for (OrderLineItemsDto line : lines) {
            int total = quantityBySku.merge(line.getSkuCode(), line.getQuantity(), Math::addExact);
            if (total > OrderLineItemsDto.MAX_QUANTITY) {
                throw new OrderRejectedException("Requested quantity for " + line.getSkuCode() + " exceeds the limit");
            }
        }
        return quantityBySku;
    }

    /**
     * Resolves the current unit price of each SKU from product-service. SKUs
     * that are missing, have no positive price, or are ambiguous (several
     * catalog entries with different prices) are left out of the result.
     */
    private Map<String, BigDecimal> fetchCatalogPrices(Set<String> skuCodes) {
        ProductResponse[] products = webClientBuilder.build().get()
                .uri("http://product-service/api/product",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(ProductResponse[].class)
                .block();

        Map<String, BigDecimal> prices = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        Arrays.stream(products == null ? new ProductResponse[0] : products)
                .filter(Objects::nonNull)
                .filter(product -> skuCodes.contains(product.getSkuCode()))
                .filter(product -> product.getPrice() != null && product.getPrice().signum() > 0)
                .forEach(product -> {
                    BigDecimal previous = prices.putIfAbsent(product.getSkuCode(), product.getPrice());
                    if (previous != null && previous.compareTo(product.getPrice()) != 0) {
                        ambiguous.add(product.getSkuCode());
                    }
                });
        ambiguous.forEach(prices::remove);
        return prices;
    }

    private void reserveStock(List<StockReservationItem> items) {
        webClientBuilder.build().post()
                .uri("http://inventory-service/api/inventory/reservations")
                .bodyValue(new StockReservationRequest(items))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(),
                        response -> response.bodyToMono(StockReservationError.class)
                                .map(error -> error.getUnavailableSkuCodes() == null
                                        ? List.<String>of() : error.getUnavailableSkuCodes())
                                .defaultIfEmpty(List.of())
                                .map(skus -> new OrderRejectedException(skus.isEmpty()
                                        ? "Product is not in stock, please try again later"
                                        : "Insufficient stock for: " + String.join(", ", skus))))
                .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(),
                        response -> Mono.just(new OrderRejectedException("Invalid order quantities")))
                .toBodilessEntity()
                .block();
    }

    private void releaseStock(List<StockReservationItem> items) {
        try {
            webClientBuilder.build().post()
                    .uri("http://inventory-service/api/inventory/reservations/release")
                    .bodyValue(new StockReservationRequest(items))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (RuntimeException e) {
            log.error("Failed to release reserved stock {}", items, e);
        }
    }

    private OrderLineItems toOrderLineItem(OrderLineItemsDto orderLineItemsDto, BigDecimal unitPrice) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(unitPrice);
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
