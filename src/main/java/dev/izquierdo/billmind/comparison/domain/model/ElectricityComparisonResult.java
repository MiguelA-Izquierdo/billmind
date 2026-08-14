package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ElectricityComparisonResult(
        BigDecimal userPricePerKwh,
        boolean userIsTou,
        BigDecimal annualKwhEstimate,
        BigDecimal userAnnualCostEuros,
        BigDecimal invoiceTotalEuros,
        ComparisonBasis basis,
        ElectricityOfferBlock flatBlock,
        ElectricityOfferBlock touBlock,
        Instant comparedAt
) implements ComparisonResult {

    /** Flat first: it is the scenario that needs no change of habits. */
    private ElectricityOfferBlock headlineBlock() {
        return flatBlock != null ? flatBlock : touBlock;
    }

    @Override
    public String bestCompany() {
        ElectricityOfferBlock block = headlineBlock();
        return block != null ? block.bestCompany() : null;
    }

    @Override
    public String bestTariffName() {
        ElectricityOfferBlock block = headlineBlock();
        return block != null ? block.bestTariffName() : null;
    }

    @Override
    public BigDecimal annualSavingsLowEuros() {
        ElectricityOfferBlock block = headlineBlock();
        return block != null ? block.annualSavingsLow() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal annualSavingsHighEuros() {
        ElectricityOfferBlock block = headlineBlock();
        return block != null ? block.annualSavingsHigh() : BigDecimal.ZERO;
    }
}