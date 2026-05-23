package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class ElectricityPriceDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElectricityPriceDltConsumer.class);

    @KafkaListener(
        topics = "${kafka.topics.electricity-price-updated-dlt}",
        groupId = "billmind-market-dlt",
        containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void handle(ConsumerRecord<String, String> record) {
        log.error("Electricity price event in DLT — no further processing: topic={} partition={} offset={} payload={}",
            record.topic(), record.partition(), record.offset(), decodePayload(record.value()));
    }

    // @RetryableTopic publishes the original bytes to the DLT via KafkaTemplate<String,Object>
    // with JsonSerializer. JsonSerializer encodes byte[] as a Base64 JSON string: "\"<base64>\"".
    // Detect that pattern and decode to recover the original payload for readable logs.
    static String decodePayload(String raw) {
        if (raw == null) return "<null payload>";
        if (raw.length() > 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            try {
                byte[] bytes = Base64.getDecoder().decode(raw.substring(1, raw.length() - 1));
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {}
        }
        return raw;
    }
}