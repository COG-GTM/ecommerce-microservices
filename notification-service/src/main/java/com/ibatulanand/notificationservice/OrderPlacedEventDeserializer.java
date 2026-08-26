package com.ibatulanand.notificationservice;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderPlacedEventDeserializer extends ObjectMapperDeserializer<OrderPlacedEvent> {
    public OrderPlacedEventDeserializer() {
        super(OrderPlacedEvent.class);
    }
}
