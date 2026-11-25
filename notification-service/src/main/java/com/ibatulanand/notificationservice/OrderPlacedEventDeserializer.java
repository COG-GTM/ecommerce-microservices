package com.ibatulanand.notificationservice;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Replaces decode failures with a payload-free exception, so neither the
 * DeserializationException header nor the error log carry a copy of the record bytes.
 */
public class OrderPlacedEventDeserializer extends JsonDeserializer<OrderPlacedEvent> {

    @Override
    public OrderPlacedEvent deserialize(String topic, byte[] data) {
        try {
            return super.deserialize(topic, data);
        } catch (RuntimeException e) {
            throw undecodable(data, e);
        }
    }

    @Override
    public OrderPlacedEvent deserialize(String topic, Headers headers, byte[] data) {
        try {
            return super.deserialize(topic, headers, data);
        } catch (RuntimeException e) {
            throw undecodable(data, e);
        }
    }

    private static SerializationException undecodable(byte[] data, RuntimeException e) {
        return new SerializationException("Undecodable OrderPlacedEvent value ("
                + (data == null ? 0 : data.length) + " bytes, "
                + NestedExceptionUtils.getMostSpecificCause(e).getClass().getName() + ")");
    }
}
