package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;

public record ElectricityMarketOffer(
        String company,
        String tariffName,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhValle,
        BigDecimal pricePerKwhLlano,
        BigDecimal pricePerKwhPunta,
        BigDecimal contractedPowerPrice,
        BigDecimal contractedPowerPriceP2
) implements MarketOffer {

    public boolean isTou() {
        return pricePerKwh == null && pricePerKwhValle != null;
    }

    /** P1 + P2 daily power price, or null when the producer published neither. */
    public BigDecimal sumPowerPrices() {
        if (contractedPowerPrice == null && contractedPowerPriceP2 == null) return null;
        BigDecimal p1 = contractedPowerPrice   != null ? contractedPowerPrice   : BigDecimal.ZERO;
        BigDecimal p2 = contractedPowerPriceP2 != null ? contractedPowerPriceP2 : BigDecimal.ZERO;
        return p1.add(p2);
    }
}