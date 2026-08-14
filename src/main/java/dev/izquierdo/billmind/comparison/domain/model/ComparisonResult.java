package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface ComparisonResult
        permits ElectricityComparisonResult {

    String bestCompany();
    String bestTariffName();

    // A band, never a single figure: the saving is extrapolated from one billing period, so the
    // two ends are what the engine actually knows. Callers that need one number take the midpoint.
    BigDecimal annualSavingsLowEuros();
    BigDecimal annualSavingsHighEuros();

    ComparisonBasis basis();
    Instant comparedAt();
}