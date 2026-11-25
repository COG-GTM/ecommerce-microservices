package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final String ORDER_PLACED = "Order Placed Successfully!";

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        return placeOrder(orderRequest, null);
    }

    // Not @Transactional: the inventory call must not hold a DB connection, and the event
    // is only published once saveAndFlush has committed the order.
    public String placeOrder(OrderRequest orderRequest, String idempotencyKey) {
        String requestFingerprint = null;
        if (idempotencyKey != null) {
            if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Idempotency-Key must be 8-64 characters of A-Z, a-z, 0-9, '-' or '_'");
            }
            requestFingerprint = fingerprint(orderRequest);
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), requestFingerprint);
            }
        }

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(requestFingerprint);

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory service, and place order if product is in stock
        InventoryResponse[] inventoryResponseArray = inventoryClient.getStock(skuCodes);

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            try {
                orderRepository.saveAndFlush(order);
            } catch (DataIntegrityViolationException e) {
                if (idempotencyKey == null) {
                    throw e;
                }
                // A concurrent request with the same key won the unique constraint.
                String fingerprint = requestFingerprint;
                return orderRepository.findByIdempotencyKey(idempotencyKey)
                        .map(saved -> replay(saved, fingerprint))
                        .orElseThrow(() -> e);
            }

            // Send order to the kafka topic
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

            return ORDER_PLACED;
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    private String replay(Order existing, String requestFingerprint) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used for a different order");
        }
        return ORDER_PLACED;
    }

    private static String fingerprint(OrderRequest orderRequest) {
        StringBuilder canonical = new StringBuilder();
        List<OrderLineItemsDto> items = orderRequest.getOrderLineItemsDtoList();
        if (items != null) {
            for (OrderLineItemsDto item : items) {
                BigDecimal price = item.getPrice();
                canonical.append(item.getSkuCode()).append('\u0000')
                        .append(price == null ? "null" : price.stripTrailingZeros().toString()).append('\u0000')
                        .append(item.getQuantity()).append('\n');
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
