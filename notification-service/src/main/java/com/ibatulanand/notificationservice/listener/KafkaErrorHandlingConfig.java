package com.ibatulanand.notificationservice.listener;

import com.ibatulanand.notificationservice.OrderPlacedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded retries, then {@code notificationTopic.DLT}. Deserialization failures (poison pills) and
 * {@link InvalidOrderEventException} skip the retries.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    public static final String DLT_TOPIC = OrderPlacedListener.TOPIC + ".DLT";

    @Bean
    public NewTopic notificationDeadLetterTopic() {
        return TopicBuilder.name(DLT_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties kafkaProperties) {
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.configure(Map.of(JsonSerializer.TYPE_MAPPINGS, "event:" + OrderPlacedEvent.class.getName()), false);
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(String.class, new StringSerializer());
        valueSerializers.put(Object.class, json);
        Map<Class<?>, Serializer<?>> keySerializers = new LinkedHashMap<>();
        keySerializers.put(byte[].class, new ByteArraySerializer());
        keySerializers.put(Object.class, new StringSerializer());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(),
                new DelegatingByTypeSerializer(keySerializers, true),
                new DelegatingByTypeSerializer(valueSerializers, true)));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate,
                                                 @Value("${notification.kafka.retry.interval-ms:1000}") long intervalMs,
                                                 @Value("${notification.kafka.retry.max-retries:2}") long maxRetries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(DLT_TOPIC, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, maxRetries));
        handler.addNotRetryableExceptions(InvalidOrderEventException.class);
        return handler;
    }
}
