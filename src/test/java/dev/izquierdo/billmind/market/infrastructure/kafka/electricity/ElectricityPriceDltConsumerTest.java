package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ElectricityPriceDltConsumerTest {

    private final ElectricityPriceDltConsumer consumer = new ElectricityPriceDltConsumer();

    @Test
    void shouldNotThrowWhenPayloadIsValidJson() {
        assertThatNoException().isThrownBy(() ->
            consumer.handle(record("{\"type\":\"ElectricityPriceUpdated\",\"pricePerKwh\":\"no-soy-un-numero\"}")));
    }

    @Test
    void shouldNotThrowWhenPayloadIsMalformedJson() {
        assertThatNoException().isThrownBy(() ->
            consumer.handle(record("{esto no es json valido")));
    }

    @Test
    void shouldNotThrowWhenPayloadIsNull() {
        assertThatNoException().isThrownBy(() -> consumer.handle(record(null)));
    }

    // @RetryableTopic sends failed bytes to DLT via JsonSerializer, which encodes byte[] as
    // a Base64 JSON string: "\"<base64>\"". decodePayload must recover the original text.
    @Test
    void shouldDecodeBase64JsonEncodedPayload() {
        String original = "{\"type\":\"ElectricityPriceUpdated\",\"pricePerKwh\":\"no-soy-un-numero\"}";
        String base64Wrapped = "\"" + Base64.getEncoder().encodeToString(original.getBytes()) + "\"";

        String decoded = ElectricityPriceDltConsumer.decodePayload(base64Wrapped);

        assertThat(decoded).isEqualTo(original);
    }

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("market.electricity-price-updated.DLT", 0, 0L, null, payload);
    }
}