package com.ibatulanand.notificationservice.support;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;

import java.nio.charset.StandardCharsets;

/** Central place to build notification test data, so a payload change touches one file. */
public final class TestFixtures {

    /** Type id order-service publishes, see its spring.json.type.mapping producer property. */
    public static final String ORDER_PLACED_TYPE_ID = "event";

    public static final String NOTIFICATION_TOPIC = "notificationTopic";

    private TestFixtures() {
    }

    public static String orderPlacedEventJson(String orderNumber) {
        return "{\"orderNumber\":\"" + orderNumber + "\"}";
    }

    /**
     * A record shaped exactly like the one order-service's JsonSerializer produces: the JSON body
     * plus the mapped type id in the {@code __TypeId__} header.
     */
    public static ProducerRecord<String, String> orderPlacedRecord(String orderNumber) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(NOTIFICATION_TOPIC, null, orderPlacedEventJson(orderNumber));
        record.headers()
                .add(
                        AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
                        ORDER_PLACED_TYPE_ID.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
