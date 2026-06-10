package dev.izquierdo.billmind.comparison.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public sealed interface ComparisonResult
        permits ElectricityComparisonResult {

    String bestCompany();
    String bestTariffName();
    BigDecimal annualSavingsEuros();
    Instant comparedAt();
}