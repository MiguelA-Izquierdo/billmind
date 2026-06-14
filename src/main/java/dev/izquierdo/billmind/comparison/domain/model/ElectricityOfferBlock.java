package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record ElectricityOfferBlock(
        String bestCompany,
        String bestTariffName,
        BigDecimal bestPricePerKwh,
        BigDecimal annualSavingsEuros,
        List<ElectricityAlternativeRate> alternatives
) {}