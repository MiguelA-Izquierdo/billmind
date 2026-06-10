package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;

public record ElectricityAlternativeRate(
        String company,
        String tariffName,
        BigDecimal effectivePricePerKwh,
        boolean touRate
) implements AlternativeRate {}