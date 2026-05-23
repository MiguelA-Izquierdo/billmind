package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.izquierdo.billmind._shared.infrastructure.kafka.KafkaEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@JsonTypeName("ElectricityPriceUpdated")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElectricityPriceEvent(
        String eventId,
        String company,
        String tariffName,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhValle,
        BigDecimal pricePerKwhLlano,
        BigDecimal pricePerKwhPunta,
        BigDecimal contractedPowerPrice,
        BigDecimal contractedPowerPriceP2,
        LocalDate validFrom,
        LocalDate validTo,
        String region,
        String source,
        Instant publishedAt
) implements KafkaEvent {}