package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;

public record ElectricityMarketOffer(
        String company,
        String tariffName,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhValle,
        BigDecimal pricePerKwhLlano,
        BigDecimal pricePerKwhPunta,
        BigDecimal contractedPowerPrice
) implements MarketOffer {

    public boolean isTou() {
        return pricePerKwh == null && pricePerKwhValle != null;
    }
}
