package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ElectricityComparisonResult(
        BigDecimal userPricePerKwh,
        boolean userIsTou,
        BigDecimal annualKwhEstimate,
        ElectricityOfferBlock flatBlock,
        ElectricityOfferBlock touBlock,
        Instant comparedAt
) implements ComparisonResult {

    @Override
    public String bestCompany() {
        if (flatBlock != null) return flatBlock.bestCompany();
        if (touBlock  != null) return touBlock.bestCompany();
        return null;
    }

    @Override
    public String bestTariffName() {
        if (flatBlock != null) return flatBlock.bestTariffName();
        if (touBlock  != null) return touBlock.bestTariffName();
        return null;
    }

    @Override
    public BigDecimal annualSavingsEuros() {
        if (flatBlock != null) return flatBlock.annualSavingsEuros();
        if (touBlock  != null) return touBlock.annualSavingsEuros();
        return BigDecimal.ZERO;
    }
}