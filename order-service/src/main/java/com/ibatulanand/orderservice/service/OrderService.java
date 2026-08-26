package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.client.InventoryClient;
import com.ibatulanand.orderservice.dto.InventoryResponse;
import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderService {

    private final OrderPersistence orderPersistence;
    private final InventoryClient inventoryClient;
    private final Emitter<OrderPlacedEvent> notificationEmitter;

    @Inject
    public OrderService(OrderPersistence orderPersistence,
                        @RestClient InventoryClient inventoryClient,
                        @Channel("notificationTopic")
                        Emitter<OrderPlacedEvent> notificationEmitter) {
        this.orderPersistence = orderPersistence;
        this.inventoryClient = inventoryClient;
        this.notificationEmitter = notificationEmitter;
    }

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToEntity)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
        List<InventoryResponse> inventoryResponses = inventoryClient.isInStock(skuCodes);

        boolean allProductsInStock = inventoryResponses.stream()
                .allMatch(InventoryResponse::inStock);

        if (!allProductsInStock) {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }

        orderPersistence.persist(order);
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withHeaders(new RecordHeaders().add("__TypeId__",
                        "event".getBytes(StandardCharsets.UTF_8)))
                .build();
        notificationEmitter.send(Message.of(new OrderPlacedEvent(order.getOrderNumber()))
                .addMetadata(metadata));
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto dto) {
        OrderLineItems lineItem = new OrderLineItems();
        lineItem.setPrice(dto.getPrice());
        lineItem.setQuantity(dto.getQuantity());
        lineItem.setSkuCode(dto.getSkuCode());
        return lineItem;
    }
}
