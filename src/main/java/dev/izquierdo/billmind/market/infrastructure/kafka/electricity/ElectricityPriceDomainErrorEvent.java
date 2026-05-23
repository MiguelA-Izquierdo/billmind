package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;

// Outbound notification only — does not implement KafkaEvent (that interface is for inbound deserialization).
@JsonTypeName("ElectricityPriceDomainError")
public record ElectricityPriceDomainErrorEvent(
        ElectricityPriceEvent originalEvent,
        String reason,
        Instant failedAt
) {
    public ElectricityPriceDomainErrorEvent(ElectricityPriceEvent originalEvent, String reason) {
        this(originalEvent, reason, Instant.now());
    }
}