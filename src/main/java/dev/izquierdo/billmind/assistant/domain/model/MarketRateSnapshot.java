package dev.izquierdo.billmind.assistant.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketRateSnapshot(
    String company,
    String tariffName,
    BigDecimal pricePerKwh,
    BigDecimal pricePerKwhValle,
    BigDecimal pricePerKwhLlano,
    BigDecimal pricePerKwhPunta,
    BigDecimal contractedPowerPrice,
    BigDecimal contractedPowerPriceP2,
    LocalDate validFrom
) {}