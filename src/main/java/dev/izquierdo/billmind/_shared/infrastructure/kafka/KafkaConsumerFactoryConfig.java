package dev.izquierdo.billmind._shared.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaConsumerFactoryConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerFactoryConfig.class);

    // Declared as DefaultKafkaConsumerFactory<Object, Object> so Spring Kafka's
    // ConcurrentKafkaListenerContainerFactory can inject it without a generic type mismatch.
    // The actual deserialization target (KafkaEvent) is set on the JsonDeserializer instance.
    // ErrorHandlingDeserializer wraps JsonDeserializer so that deserialization failures are
    // surfaced as listener-level errors (accessible to DefaultErrorHandler / @DltHandler)
    // instead of crashing the consumer thread with an unhandleable SerializationException.
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultKafkaConsumerFactory<Object, Object> kafkaConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        JsonDeserializer<KafkaEvent> jsonDeserializer =
                new JsonDeserializer<>(KafkaEvent.class, objectMapper, false);
        ErrorHandlingDeserializer<KafkaEvent> deserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);
        // Log the raw payload + root Jackson error at the exact point of failure so the
        // cause is visible without having to inspect the DLT or read buried stack traces.
        deserializer.setFailedDeserializationFunction(info -> {
            String raw = info.getData() != null
                    ? new String(info.getData(), StandardCharsets.UTF_8)
                    : "<null payload>";
            Throwable root = info.getException();
            while (root.getCause() != null) root = root.getCause();
            log.error("Kafka deserialization failed: topic={} reason='{}' raw={}",
                    info.getTopic(), root.getMessage(), raw);
            return null;
        });
        DefaultKafkaConsumerFactory factory = new DefaultKafkaConsumerFactory(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                deserializer
        );
        return factory;
    }

    // StringDeserializer for DLT consumers: DLT messages may contain raw bytes from
    // failed deserializations that JsonDeserializer would reject again. String decoding
    // always succeeds, so the DLT handler is guaranteed to run and log the payload.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        DefaultKafkaConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                new StringDeserializer()
        );
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        return containerFactory;
    }
}