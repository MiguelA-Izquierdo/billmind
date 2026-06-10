package dev.izquierdo.billmind.comparison.infrastructure.controller.dto;

import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonResponseDTO(
        String bestCompany,
        String bestTariffName,
        BigDecimal annualSavingsEuros,
        Object details
) {

    public record ElectricityDetails(
            BigDecimal userPricePerKwh,
            BigDecimal bestPricePerKwh,
            boolean touRate,
            BigDecimal annualKwhEstimate,
            List<AlternativeDTO> alternatives
    ) {}

    public record AlternativeDTO(
            String company,
            String tariffName,
            BigDecimal effectivePricePerKwh,
            boolean touRate
    ) {}

    public static ComparisonResponseDTO from(ComparisonResult result) {
        return switch (result) {
            case ElectricityComparisonResult e -> new ComparisonResponseDTO(
                    e.bestCompany(),
                    e.bestTariffName(),
                    e.annualSavingsEuros(),
                    new ElectricityDetails(
                            e.userPricePerKwh(),
                            e.bestPricePerKwh(),
                            e.touRate(),
                            e.annualKwhEstimate(),
                            e.alternatives().stream()
                                    .map(a -> new AlternativeDTO(
                                            a.company(), a.tariffName(),
                                            a.effectivePricePerKwh(), a.touRate()))
                                    .toList()
                    )
            );
        };
    }
}