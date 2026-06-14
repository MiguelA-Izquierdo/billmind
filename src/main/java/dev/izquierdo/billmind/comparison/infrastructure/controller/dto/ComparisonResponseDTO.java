package dev.izquierdo.billmind.comparison.infrastructure.controller.dto;

import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonResponseDTO(
        BigDecimal userPricePerKwh,
        boolean userIsTou,
        BigDecimal annualKwhEstimate,
        OfferBlockDTO flatBlock,
        OfferBlockDTO touBlock
) {

    public record OfferBlockDTO(
            String bestCompany,
            String bestTariffName,
            BigDecimal bestPricePerKwh,
            BigDecimal annualSavingsEuros,
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
                    e.userPricePerKwh(),
                    e.userIsTou(),
                    e.annualKwhEstimate(),
                    toBlockDTO(e.flatBlock()),
                    toBlockDTO(e.touBlock())
            );
        };
    }

    private static OfferBlockDTO toBlockDTO(ElectricityOfferBlock block) {
        if (block == null) return null;
        return new OfferBlockDTO(
                block.bestCompany(),
                block.bestTariffName(),
                block.bestPricePerKwh(),
                block.annualSavingsEuros(),
                block.alternatives().stream()
                        .map(a -> new AlternativeDTO(a.company(), a.tariffName(), a.effectivePricePerKwh(), a.touRate()))
                        .toList()
        );
    }
}