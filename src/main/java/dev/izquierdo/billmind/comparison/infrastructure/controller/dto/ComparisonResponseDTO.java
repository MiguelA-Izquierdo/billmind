package dev.izquierdo.billmind.comparison.infrastructure.controller.dto;

import dev.izquierdo.billmind.comparison.domain.model.ComparisonBasis;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonResponseDTO(
        BigDecimal userPricePerKwh,
        boolean userIsTou,
        BigDecimal annualKwhEstimate,
        BigDecimal userAnnualCostEuros,
        BigDecimal invoiceTotalEuros,
        BasisDTO basis,
        OfferBlockDTO flatBlock,
        OfferBlockDTO touBlock
) {

    /**
     * What the savings band rests on. Every client that prints a figure has to be able to print
     * its caveats from the same payload — otherwise the API and the UI disagree about how sure
     * they are.
     */
    public record BasisDTO(
            long observedDays,
            boolean annualised,
            String powerTerm,
            String consumptionProfile,
            boolean taxesIncluded
    ) {}

    public record OfferBlockDTO(
            String bestCompany,
            String bestTariffName,
            BigDecimal bestPricePerKwh,
            BigDecimal bestAnnualCostEuros,
            BigDecimal periodSavingsEuros,
            BigDecimal annualSavingsLow,
            BigDecimal annualSavingsHigh,
            BigDecimal annualSavingsMid,
            List<AlternativeDTO> alternatives
    ) {}

    public record AlternativeDTO(
            String company,
            String tariffName,
            BigDecimal effectivePricePerKwh,
            BigDecimal annualCostEuros,
            boolean touRate
    ) {}

    public static ComparisonResponseDTO from(ComparisonResult result) {
        return switch (result) {
            case ElectricityComparisonResult e -> new ComparisonResponseDTO(
                    e.userPricePerKwh(),
                    e.userIsTou(),
                    e.annualKwhEstimate(),
                    e.userAnnualCostEuros(),
                    e.invoiceTotalEuros(),
                    toBasisDTO(e.basis()),
                    toBlockDTO(e.flatBlock()),
                    toBlockDTO(e.touBlock())
            );
        };
    }

    private static BasisDTO toBasisDTO(ComparisonBasis basis) {
        if (basis == null) return null;
        return new BasisDTO(
                basis.observedDays(),
                basis.annualised(),
                basis.powerTerm().name(),
                basis.consumptionProfile().name(),
                basis.taxesIncluded()
        );
    }

    private static OfferBlockDTO toBlockDTO(ElectricityOfferBlock block) {
        if (block == null) return null;
        return new OfferBlockDTO(
                block.bestCompany(),
                block.bestTariffName(),
                block.bestPricePerKwh(),
                block.bestAnnualCostEuros(),
                block.periodSavingsEuros(),
                block.annualSavingsLow(),
                block.annualSavingsHigh(),
                block.annualSavingsMid(),
                block.alternatives().stream()
                        .map(a -> new AlternativeDTO(a.company(), a.tariffName(),
                                a.effectivePricePerKwh(), a.annualCostEuros(), a.touRate()))
                        .toList()
        );
    }
}