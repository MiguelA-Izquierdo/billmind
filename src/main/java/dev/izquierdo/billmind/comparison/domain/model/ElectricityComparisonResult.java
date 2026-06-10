package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ElectricityComparisonResult(
        BigDecimal userPricePerKwh,
        String bestCompany,
        String bestTariffName,
        BigDecimal bestPricePerKwh,
        boolean touRate,
        BigDecimal annualKwhEstimate,
        BigDecimal annualSavingsEuros,
        List<ElectricityAlternativeRate> alternatives,
        Instant comparedAt
) implements ComparisonResult {}