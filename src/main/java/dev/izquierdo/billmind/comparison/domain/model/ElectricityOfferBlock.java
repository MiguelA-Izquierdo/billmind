package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The winning offer of one scenario (flat-price or time-of-use) and what switching to it is worth,
 * over two horizons that are not equally knowable.
 *
 * <p>{@code periodSavingsEuros} is what the invoice in hand would have cost on this tariff: real
 * consumption, real days, nothing extrapolated. It carries no band and is not rounded, because
 * there is nothing uncertain to round away.
 *
 * <p>The annual figure is a projection over a year the user has not lived, so it is a band and it
 * is rounded outwards to tens. Presenting the two alike would flatten the difference that matters.
 */
public record ElectricityOfferBlock(
        String bestCompany,
        String bestTariffName,
        BigDecimal bestPricePerKwh,
        BigDecimal bestAnnualCostEuros,
        BigDecimal periodSavingsEuros,
        BigDecimal annualSavingsLow,
        BigDecimal annualSavingsHigh,
        List<ElectricityAlternativeRate> alternatives
) {

    /** Midpoint of the band — the one figure a headline or a counter can settle on. */
    public BigDecimal annualSavingsMid() {
        return annualSavingsLow.add(annualSavingsHigh).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}